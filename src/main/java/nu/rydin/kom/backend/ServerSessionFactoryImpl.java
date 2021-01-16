/*
 * Created on Nov 3, 2003
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.backend;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;
import nu.rydin.kom.backend.data.FileManager;
import nu.rydin.kom.backend.data.SettingsManager;
import nu.rydin.kom.backend.data.UserManager;
import nu.rydin.kom.constants.SettingKeys;
import nu.rydin.kom.constants.UserFlags;
import nu.rydin.kom.constants.UserPermissions;
import nu.rydin.kom.events.UserAttendanceEvent;
import nu.rydin.kom.exceptions.AlreadyLoggedInException;
import nu.rydin.kom.exceptions.AuthenticationException;
import nu.rydin.kom.exceptions.AuthorizationException;
import nu.rydin.kom.exceptions.DuplicateNameException;
import nu.rydin.kom.exceptions.LoginProhibitedException;
import nu.rydin.kom.exceptions.ModuleException;
import nu.rydin.kom.exceptions.NoSuchModuleException;
import nu.rydin.kom.exceptions.ObjectNotFoundException;
import nu.rydin.kom.exceptions.UnexpectedException;
import nu.rydin.kom.frontend.text.ClientSettings;
import nu.rydin.kom.modules.Module;
import nu.rydin.kom.modules.Modules;
import nu.rydin.kom.structs.IntrusionAttempt;
import nu.rydin.kom.structs.SessionListItem;
import nu.rydin.kom.structs.UserInfo;
import nu.rydin.kom.utils.Base64;
import nu.rydin.kom.utils.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** @author Pontus Rydin */
public class ServerSessionFactoryImpl implements ServerSessionFactory, Module {
  private static final Logger LOG = LogManager.getLogger(ServerSessionFactoryImpl.class);
  private final Map<String, IntrusionAttempt> intrusionAttempts =
      Collections.synchronizedMap(new HashMap<String, IntrusionAttempt>());
  /** Valid tickets */
  private final Map<String, Long> m_validTickets = Collections.synchronizedMap(new HashMap<>());
  /** General purpose timer for ticket expirations and intrusion attempt clearing */
  private final Timer timer = new Timer(true);

  private int m_nextSessionId = 0;
  private SessionManager m_sessionManager;
  private ContextCleaner m_contextCleaner;

  public ServerSessionFactoryImpl() throws UnexpectedException {}

  public static ServerSessionFactory getInstance() {
    try {
      return Modules.getModule("Backend", ServerSessionFactoryImpl.class);
    } catch (final NoSuchModuleException e) {
      ServerSessionFactoryImpl.LOG.fatal(
          "Backend not found. Things will break badly from here on!", e);
      return null;
    }
  }

  @Override
  public void start(final Map<String, String> properties) throws ModuleException {
    // Initialize the static global server settings class.
    //
    ServerSettings.initialize(properties);

    // Initialize the static global client settings class.
    // FIXME: This should probably not be initialized here, but since
    // the module system can't handle a separated client-side anyway,
    // we can safely ignore this for now.
    ClientSettings.initialize(properties);

    // Start context cleaner thread
    //
    m_contextCleaner = new ContextCleaner();
    m_contextCleaner.setName("ContextCleaner");
    m_contextCleaner.start();

    // Since we just loaded the client settings, we can perform this check:
    //
    // Check that we have the character sets we need
    //
    final StringTokenizer st = new StringTokenizer(ClientSettings.getCharsets(), ",");
    while (st.hasMoreTokens()) {
      final String charSet = st.nextToken();
      try {
        new OutputStreamWriter(System.out, charSet);
      } catch (final UnsupportedEncodingException e) {
        throw new ModuleException(
            "Character set "
                + charSet
                + " not supported. Do you have charsets.jar in you classpath?",
            e);
      }
    }

    // Make sure there's at least a sysop in the database
    //
    boolean committed = false;
    DataAccess da = null;
    try {
      da = DataAccessPool.instance().getDataAccess();
      final UserManager um = da.getUserManager();

      // FIXME Move to a bootstrapping sql-script.
      // Make sure there is at least a sysop in the database.
      //
      if (!um.userExists("sysop")) {
        um.addUser(
            "sysop",
            "sysop",
            "Sysop",
            null,
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "ISO-8859-1",
            "sv_SE",
            UserFlags.DEFAULT_FLAGS1,
            UserFlags.DEFAULT_FLAGS2,
            UserFlags.DEFAULT_FLAGS3,
            UserFlags.DEFAULT_FLAGS4,
            UserPermissions.EVERYTHING);
        da.commit();
        committed = true;
      }
    } catch (final SQLException e) {
      throw new ModuleException(e);
    } catch (final NoSuchAlgorithmException e) {
      // Could not calculate password digest. Should not happen!
      //
      throw new ModuleException(e);
    } catch (final DuplicateNameException e) {
      // Duplicate name when adding sysop. Should not happen!
      //
      throw new ModuleException(e);
    } catch (final UnexpectedException e) {
      // No one expects ths Spanish Inquisition!
      //
      throw new ModuleException(e);
    } finally {
      if (da != null) {
        if (!committed) {
          try {
            da.rollback();
          } catch (final UnexpectedException e) {
            // This is probably bad if it happens.
            throw new ModuleException(e);
          }
        }
        DataAccessPool.instance().returnDataAccess(da);
      }
    }

    m_sessionManager = new SessionManager();
    m_sessionManager.start();
  }

  @Override
  public void stop() {
    m_sessionManager.stop();
    m_contextCleaner.interrupt();
  }

  @Override
  public void join() throws InterruptedException {
    m_sessionManager.join();
    m_contextCleaner.join();
  }

  @Override
  public void notifySuccessfulLogin(final String client) {
    intrusionAttempts.remove(client);
  }

  @Override
  public void notifyFailedLogin(final String client, final int limit, final long lockout) {
    synchronized (intrusionAttempts) {
      IntrusionAttempt ia = intrusionAttempts.get(client);
      if (ia == null) {
        ia = new IntrusionAttempt(client, limit, lockout);
        intrusionAttempts.put(client, ia);
      } else {
        ia.addAttempt();
      }

      // We schedule a timer for every attempt. When they expire, they will decrease the
      // counter, resulting in the lockout to run from the last attempt
      //
      timer.schedule(new IntrusionKiller(client), lockout);
    }
  }

  @Override
  public boolean isBlacklisted(final String client) {
    synchronized (intrusionAttempts) {
      final IntrusionAttempt ia = intrusionAttempts.get(client);
      if (ia == null) {
        return false;
      }
      return ia.isBlocked();
    }
  }

  @Override
  public ServerSession login(final String ticket, final short clientType, final boolean allowMulti)
      throws AuthenticationException, LoginProhibitedException, AlreadyLoggedInException,
          UnexpectedException {
    // Log us in!
    //
    final DataAccess da = DataAccessPool.instance().getDataAccess();
    try {
      final long id = authenticate(ticket);
      final ServerSession session = innerLogin(id, clientType, da, allowMulti);
      consumeTicket(ticket);
      return session;
    } finally {
      DataAccessPool.instance().returnDataAccess(da);
    }
  }

  private ServerSession innerLogin(
      final long id, final short clientType, final DataAccess da, final boolean allowMulti)
      throws AuthenticationException, LoginProhibitedException, AlreadyLoggedInException,
          UnexpectedException {
    try {
      // Authenticate user
      //
      final UserManager um = da.getUserManager();
      final UserInfo ui = um.loadUser(id);

      // Login prohibited? Allow login only if sysop
      //
      if (!m_sessionManager.canLogin() && (ui.getRights() & UserPermissions.ADMIN) == 0) {
        throw new LoginProhibitedException();
      }

      // Was the user already logged in?
      //
      final List<ServerSession> userSessions = m_sessionManager.getSessionsByUser(id);
      final int top = userSessions.size();
      if (!allowMulti && top > 0) {
        final ArrayList<SessionListItem> list = new ArrayList<>(top);
        for (final ServerSession each : userSessions) {
          list.add(
              new SessionListItem(
                  each.getSessionId(),
                  each.getClientType(),
                  each.getLoginTime(),
                  each.getLastHeartbeat()));
        }
        throw new AlreadyLoggedInException(list);
      }

      // Create a ServerSessionImpl, wrapped in a dynamic proxy and an InvocationHandler
      // keeping track of connections and transactions.
      //
      final ServerSessionImpl session =
          new ServerSessionImpl(da, id, m_nextSessionId++, clientType, m_sessionManager);
      m_sessionManager.registerSession(session);

      // Successfully logged in!
      // Broadcast message.
      //
      m_sessionManager.broadcastEvent(
          new UserAttendanceEvent(id, ui.getName(), UserAttendanceEvent.LOGIN));

      //  Create transactional wrapper and return
      //
      final InvocationHandler handler = new TransactionalInvocationHandler(session);
      return (ServerSession)
          Proxy.newProxyInstance(
              ServerSession.class.getClassLoader(), new Class[] {ServerSession.class}, handler);

    } catch (final SQLException e) {
      throw new UnexpectedException(-1, e);
    } catch (final ObjectNotFoundException e) {
      // User was not found. We treat that as an authentication
      // exception.
      //
      throw new AuthenticationException();
    }
  }

  @Override
  public ServerSession login(
      final String user, final String password, final short clientType, final boolean allowMulti)
      throws AuthenticationException, LoginProhibitedException, AlreadyLoggedInException,
          UnexpectedException {
    final DataAccess da = DataAccessPool.instance().getDataAccess();
    try {
      final UserManager um = da.getUserManager();

      // Authenticate user
      //
      final long id = um.authenticate(user, password);
      return innerLogin(id, clientType, da, allowMulti);
    } catch (final SQLException | NoSuchAlgorithmException e) {
      throw new UnexpectedException(-1, e);
    } catch (final ObjectNotFoundException e) {
      // User was not found. We treat that as an authentication
      // exception.
      //
      throw new AuthenticationException();
    } finally {
      DataAccessPool.instance().returnDataAccess(da);
    }
  }

  @Override
  public String generateTicket(final String user, final String password)
      throws AuthenticationException, UnexpectedException {
    // Check that username and password are valid.
    //
    final long userId = authenticate(user, password);
    try {
      // Generate 128 bytes of random data and calculate MD5
      // digest. A ticket is typically valid for <30s, so
      // this feels fairly secure.
      //
      final MessageDigest md = MessageDigest.getInstance("MD5");
      final byte[] data = new byte[128];
      SecureRandom.getInstance("SHA1PRNG").nextBytes(data);
      md.update(data);
      final String ticket = Base64.encodeBytes(md.digest());

      // Have a valid ticket! Now register it.
      //
      m_validTickets.put(ticket, userId);
      timer.schedule(new TicketKiller(ticket), ServerSettings.getTicketLifetime());
      return ticket;
    } catch (final NoSuchAlgorithmException e) {
      throw new UnexpectedException(-1, e);
    }
  }

  @Override
  public void consumeTicket(final String ticket) throws AuthenticationException {
    final Long idObj = m_validTickets.remove(ticket);
    if (idObj == null) {
      throw new AuthenticationException();
    }
  }

  @Override
  public long authenticate(final String ticket) throws AuthenticationException {
    final Long idObj = m_validTickets.get(ticket);
    if (idObj == null) {
      throw new AuthenticationException();
    }
    return idObj;
  }

  @Override
  public long authenticate(final String user, final String password)
      throws AuthenticationException, UnexpectedException {
    final DataAccess da = DataAccessPool.instance().getDataAccess();
    try {
      final UserManager um = da.getUserManager();

      // Authenticate user
      //
      return um.authenticate(user, password);
    } catch (final ObjectNotFoundException e) {
      // User was not found. We treat that as an authentication
      // exception.
      //
      throw new AuthenticationException();
    } catch (final NoSuchAlgorithmException | SQLException e) {
      // Could not calculate password digest. Should not happen!
      //
      throw new UnexpectedException(-1, e);
    } finally {
      DataAccessPool.instance().returnDataAccess(da);
    }
  }

  @Override
  public boolean allowsSelfRegistration() throws UnexpectedException {
    final DataAccessPool dap = DataAccessPool.instance();
    final DataAccess da = dap.getDataAccess();
    try {
      final SettingsManager sm = da.getSettingManager();
      return sm.getNumber(SettingKeys.ALLOW_SELF_REGISTER) != 0;
    } catch (final ObjectNotFoundException e) {
      // Not found? Not set!
      //
      return false;
    } catch (final SQLException e) {
      throw new UnexpectedException(-1, e);
    } finally {
      dap.returnDataAccess(da);
    }
  }

  @Override
  public boolean loginExits(final String userid) throws UnexpectedException {
    final DataAccessPool dap = DataAccessPool.instance();
    final DataAccess da = dap.getDataAccess();
    try {
      da.getUserManager().getUserIdByLogin(userid);
      return true;
    } catch (final ObjectNotFoundException e) {
      return false;
    } catch (final SQLException e) {
      throw new UnexpectedException(-1, e);
    } finally {
      dap.returnDataAccess(da);
    }
  }

  @Override
  public long selfRegister(
      final String login, final String password, final String fullName, final String charset)
      throws AuthorizationException, UnexpectedException, DuplicateNameException {
    final DataAccessPool dap = DataAccessPool.instance();
    final DataAccess da = dap.getDataAccess();
    boolean committed = false;
    try {
      // Are we allowed to do this?
      //
      if (!allowsSelfRegistration()) {
        throw new AuthorizationException();
      }

      // Create user
      //
      final long id =
          da.getUserManager()
              .addUser(
                  login,
                  password,
                  fullName,
                  null,
                  "",
                  "",
                  "",
                  "",
                  "",
                  "",
                  "",
                  "",
                  "",
                  charset,
                  "sv_SE",
                  UserFlags.DEFAULT_FLAGS1,
                  UserFlags.DEFAULT_FLAGS2,
                  UserFlags.DEFAULT_FLAGS3,
                  UserFlags.DEFAULT_FLAGS4,
                  UserPermissions.SELF_REGISTERED_USER);

      // Create a login-script to help this user set stuff up
      //
      try {
        final String content = FileUtils.loadTextFromResource("selfregistered.login");
        final FileManager fm = da.getFileManager();
        fm.store(id, ".login.cmd", content);
      } catch (final FileNotFoundException e) {
        // No command file exists? Probably just means the
        // sysop doesn't think one is needed. Just skip!
      } catch (final IOException e) {
        throw new UnexpectedException(-1, e);
      }
      // Done!
      //
      da.commit();
      committed = true;
      return id;
    } catch (final SQLException | NoSuchAlgorithmException e) {
      throw new UnexpectedException(-1, e);
    } finally {
      if (!committed) {
        da.rollback();
      }
      dap.returnDataAccess(da);
    }
  }

  @Override
  public void killSession(final int sessionId, final String user, final String password)
      throws AuthenticationException, UnexpectedException, InterruptedException {
    // Authenticate
    //
    final long id = authenticate(user, password);
    innerKillSession(sessionId, id);
  }

  @Override
  public void killSession(final int session, final String ticket)
      throws AuthenticationException, UnexpectedException, InterruptedException {
    // Authenticate
    //
    final long id = authenticate(ticket);
    innerKillSession(session, id);
  }

  protected void innerKillSession(final int sessionId, final long userId)
      throws AuthenticationException, UnexpectedException, InterruptedException {
    // We can only kill our own sessions
    //
    final ServerSession sess = m_sessionManager.getSessionById(sessionId);
    if (sess.getLoggedInUserId() != userId) {
      throw new AuthenticationException();
    }
    m_sessionManager.killSessionById(sessionId);
  }

  private class TicketKiller extends TimerTask {
    private final String m_ticket;

    public TicketKiller(final String ticket) {
      m_ticket = ticket;
    }

    @Override
    public void run() {
      if (m_validTickets.remove(m_ticket) != null) {
        ServerSessionFactoryImpl.LOG.info("Discarded unused ticket");
      }
    }
  }

  private class IntrusionKiller extends TimerTask {
    private final String client;

    public IntrusionKiller(final String client) {
      this.client = client;
    }

    @Override
    public void run() {
      synchronized (intrusionAttempts) {
        final IntrusionAttempt ia = intrusionAttempts.get(client);
        if (ia != null) {
          // Decrease attempt count and remove if it reached zero
          //
          if (ia.expireAttempt() == 0) {
            intrusionAttempts.remove(client);
            ServerSessionFactoryImpl.LOG.info("Discarded expired intrusion attempt for: " + client);
          }
        }
      }
    }
  }

  private class ContextCleaner extends Thread {
    @Override
    public void run() {
      try {
        final UserContextFactory ucf = UserContextFactory.getInstance();
        for (; ; ) {
          Thread.sleep(60000);
          synchronized (ucf) {
            final List<UserContext> list = ucf.listContexts();
            for (final UserContext each : list) {
              if (!m_sessionManager.userHasSession(each.getUserId())) {
                ucf.release(each.getUserId());
                ServerSessionFactoryImpl.LOG.info(
                    "Released rogue context for user " + each.getUserId());
              }
            }
          }
        }
      } catch (final InterruptedException e) {
        ServerSessionFactoryImpl.LOG.info("ContextCleaner shutting down...");
      }
    }
  }
}
