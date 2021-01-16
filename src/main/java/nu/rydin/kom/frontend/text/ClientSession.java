/*
 * Created on Oct 5, 2003
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.frontend.text;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.text.DateFormatSymbols;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;
import nu.rydin.kom.backend.NameUtils;
import nu.rydin.kom.backend.ServerSession;
import nu.rydin.kom.backend.ServerSessionFactory;
import nu.rydin.kom.backend.ServerSessionFactoryImpl;
import nu.rydin.kom.backend.data.NameManager;
import nu.rydin.kom.constants.ClientTypes;
import nu.rydin.kom.constants.CommandSuggestions;
import nu.rydin.kom.constants.MessageLogKinds;
import nu.rydin.kom.constants.SystemFiles;
import nu.rydin.kom.constants.UserFlags;
import nu.rydin.kom.constants.UserPermissions;
import nu.rydin.kom.events.BroadcastAnonymousMessageEvent;
import nu.rydin.kom.events.BroadcastMessageEvent;
import nu.rydin.kom.events.ChatAnonymousMessageEvent;
import nu.rydin.kom.events.ChatMessageEvent;
import nu.rydin.kom.events.ClientEventTarget;
import nu.rydin.kom.events.DetachRequestEvent;
import nu.rydin.kom.events.Event;
import nu.rydin.kom.events.EventTarget;
import nu.rydin.kom.events.MessageDeletedEvent;
import nu.rydin.kom.events.NewMessageEvent;
import nu.rydin.kom.events.ReloadUserProfileEvent;
import nu.rydin.kom.events.TicketDeliveredEvent;
import nu.rydin.kom.events.UserAttendanceEvent;
import nu.rydin.kom.exceptions.AlreadyLoggedInException;
import nu.rydin.kom.exceptions.AmbiguousNameException;
import nu.rydin.kom.exceptions.AuthenticationException;
import nu.rydin.kom.exceptions.AuthorizationException;
import nu.rydin.kom.exceptions.DuplicateNameException;
import nu.rydin.kom.exceptions.EventDeliveredException;
import nu.rydin.kom.exceptions.ImmediateShutdownException;
import nu.rydin.kom.exceptions.InternalException;
import nu.rydin.kom.exceptions.InvalidChoiceException;
import nu.rydin.kom.exceptions.InvalidNameException;
import nu.rydin.kom.exceptions.KOMException;
import nu.rydin.kom.exceptions.KOMRuntimeException;
import nu.rydin.kom.exceptions.LineEditingDoneException;
import nu.rydin.kom.exceptions.LineEditorException;
import nu.rydin.kom.exceptions.LineOverflowException;
import nu.rydin.kom.exceptions.LineUnderflowException;
import nu.rydin.kom.exceptions.LoginNotAllowedException;
import nu.rydin.kom.exceptions.LoginProhibitedException;
import nu.rydin.kom.exceptions.NoSuchModuleException;
import nu.rydin.kom.exceptions.ObjectNotFoundException;
import nu.rydin.kom.exceptions.OperationInterruptedException;
import nu.rydin.kom.exceptions.OutputInterruptedException;
import nu.rydin.kom.exceptions.StopCharException;
import nu.rydin.kom.exceptions.UnexpectedException;
import nu.rydin.kom.exceptions.UserException;
import nu.rydin.kom.frontend.text.commands.GotoNextConference;
import nu.rydin.kom.frontend.text.commands.ReadNextMail;
import nu.rydin.kom.frontend.text.commands.ReadNextMessage;
import nu.rydin.kom.frontend.text.commands.ReadNextReply;
import nu.rydin.kom.frontend.text.commands.ReadNextSelectedMessage;
import nu.rydin.kom.frontend.text.commands.ShowTime;
import nu.rydin.kom.frontend.text.editor.StandardWordWrapper;
import nu.rydin.kom.frontend.text.editor.WordWrapper;
import nu.rydin.kom.frontend.text.editor.WordWrapperFactory;
import nu.rydin.kom.frontend.text.editor.fullscreen.FullscreenMessageEditor;
import nu.rydin.kom.frontend.text.editor.simple.SimpleMessageEditor;
import nu.rydin.kom.frontend.text.parser.Parser;
import nu.rydin.kom.frontend.text.parser.Parser.ExecutableCommand;
import nu.rydin.kom.frontend.text.terminal.ANSITerminalController;
import nu.rydin.kom.frontend.text.terminal.TerminalController;
import nu.rydin.kom.i18n.MessageFormatter;
import nu.rydin.kom.structs.ConferenceInfo;
import nu.rydin.kom.structs.FileStatus;
import nu.rydin.kom.structs.Name;
import nu.rydin.kom.structs.NameAssociation;
import nu.rydin.kom.structs.SessionListItem;
import nu.rydin.kom.structs.SessionState;
import nu.rydin.kom.structs.UserInfo;
import nu.rydin.kom.utils.FileUtils;
import nu.rydin.kom.utils.HeaderPrinter;
import nu.rydin.kom.utils.PrintUtils;
import nu.rydin.kom.utils.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author Pontus Rydin
 * @author Jepson
 * @author Henrik Schr�der
 * @author Magnus Ihse Bursie
 */
public class ClientSession
    implements Runnable, Context, ClientEventTarget, TerminalSizeListener, EnvironmentListener {
  private static final Logger LOG = LogManager.getLogger(ClientSession.class);
  private static final int MAX_LOGIN_RETRIES = 3;
  private static final String DEFAULT_CHARSET = "ISO-8859-1";
  private final boolean m_useTicket;
  private final LineEditor m_in;
  private final KOMWriter m_out;
  private final LinkedList<Event> m_displayMessageQueue = new LinkedList<>();
  private final WordWrapperFactory m_wordWrapperFactory = new StandardWordWrapper.Factory();
  private final EventPrinter eventPrinter;
  private final String clientName;
  private MessageFormatter m_formatter = new MessageFormatter(Locale.getDefault(), "messages");
  private ServerSession m_session;
  private long m_userId;
  private UserInfo m_thisUserCache;
  private int m_windowHeight = -1;
  private int m_windowWidth = -1;
  private boolean m_ListenToTerminalSize = true;
  private DateFormatSymbols m_dateSymbols;
  private SessionState m_state;
  private String m_ticket;
  private int retriesBeforeLockout = 9;
  private long lockout = 900000;
  private Parser m_parser;
  private boolean m_loggedIn;
  private HeartbeatSender m_heartbeatSender;

  public ClientSession(
      final InputStream in,
      final OutputStream out,
      final boolean useTicket,
      final boolean selfRegister,
      final String clientName,
      final Map<String, String> parameters)
      throws UnexpectedException, InternalException {
    // Unpack properties
    //

    try {
      retriesBeforeLockout = Integer.parseInt(parameters.get("attempts"));
    } catch (final NullPointerException e) {
      // Just keep default
    } catch (final NumberFormatException e) {
      ClientSession.LOG.warn("Error parsing 'attempts' parameter, using default");
    }

    try {
      lockout = Long.parseLong(parameters.get("lockout"));
    } catch (final NullPointerException e) {
      // Just keep default
    } catch (final NumberFormatException e) {
      ClientSession.LOG.warn("Error parsing 'lockout' parameter, using default");
    }

    // Set up I/O
    //
    this.clientName = clientName;
    eventPrinter = new EventPrinter(this);

    // Set up authentication method
    //
    m_useTicket = useTicket;

    // Install commands and init parser
    //
    installCommands();

    // More I/O
    //
    try {
      m_out = new KOMWriter(out, ClientSession.DEFAULT_CHARSET);
      m_in =
          new LineEditor(in, m_out, this, this, null, m_formatter, ClientSession.DEFAULT_CHARSET);
      m_out.addNewlineListener(m_in);
    } catch (final UnsupportedEncodingException e) {
      // There're NO WAY we don't support US-ASCII!
      //
      throw new InternalException(
          ClientSession.DEFAULT_CHARSET + " not supported. Your JVM is broken!");
    }
  }

  @Override
  public void run() {
    try {
      // Start keystroke poller
      //
      m_in.start();

      // Print welcome message if defined
      //
      if (!m_useTicket) {
        try {
          final String content = FileUtils.loadTextFromResource("welcome.txt");
          PrintUtils.printIndented(m_out, content, 80, 0);
          m_out.println();
        } catch (final FileNotFoundException e) {
          // No message defined, no problem.
        } catch (final IOException e) {
          m_out.println("This should not happen!");
        }
      }

      // Try to login
      //
      UserInfo userInfo = null;
      try {
        for (int idx = 0; idx < ClientSession.MAX_LOGIN_RETRIES; ++idx) {
          try {
            userInfo = login();
            m_userId = userInfo.getId();
            Thread.currentThread().setName("Session (" + userInfo.getUserid() + ")");
            break;
          } catch (final AuthenticationException e) {
            // Logging in with ticket? This HAS to work
            //
            if (m_useTicket) {
              return;
            }
            m_out.println(m_formatter.format("login.failure"));
            ClientSession.LOG.info("Failed login");
          }
        }
      } catch (final LoginProhibitedException | LoginNotAllowedException e) {
        m_out.println();
        m_out.println(e.formatMessage(this));
        m_out.println();
        return;
      } catch (final InterruptedException | OperationInterruptedException e) {
        // Interrupted during login
        //
        return;
      } catch (final Exception e) {
        // Unhandled exception while logging in?
        // I'm afraid the ride is over...
        //
        ClientSession.LOG.warn("Unhandled exception during login?", e);
        return;
      }

      // Didn't manage to log in? Game over!
      //
      if (userInfo == null) {
        return;
      }

      // Set up IO with the correct character set
      //
      String charSet = userInfo.getCharset();
      for (; ; ) {
        try {
          m_out.setCharset(charSet);
          m_in.setCharset(charSet);
          break;
        } catch (final UnsupportedEncodingException e) {
          // Doesn't even support plain US-ASCII? We're outta here
          //
          if (charSet.equals("US-ASCII")) {
            throw new RuntimeException("No suitable character set!");
          }

          // Resort to US-ASCII
          //
          charSet = "US-ASCII";
        }
      }

      // Replace message formatter with a localized one
      //
      final String locale = userInfo.getLocale();
      final Locale m_locale;
      if (locale != null) {
        final int p = locale.indexOf('_');
        m_locale =
            p != -1
                ? new Locale(locale.substring(0, p), locale.substring(p + 1))
                : new Locale(locale);
        m_formatter = new MessageFormatter(m_locale, "messages");
      } else {
        m_locale = Locale.getDefault();
      }
      m_dateSymbols = new DateFormatSymbols(m_locale);
      m_formatter.setTimeZone(userInfo.getTimeZone());

      m_out.println(m_formatter.format("login.welcome", userInfo.getName()));
      m_out.println();

      // Print motd (if any)
      //
      try {
        final String motd = m_session.readSystemFile(SystemFiles.WELCOME_MESSAGE);
        getDisplayController().output();
        m_out.println();
        final WordWrapper ww = getWordWrapper(motd);
        String line;
        while ((line = ww.nextLine()) != null) {
          m_out.println(line);
        }
        m_out.println();
      } catch (final ObjectNotFoundException e) {
        // No motd. No big deal.
      } catch (final AuthorizationException e) {
        ClientSession.LOG.error("Users don't have permission for motd");
      } catch (final UnexpectedException e) {
        ClientSession.LOG.error(e);
      }

      // Run the login and profile script
      //
      getDisplayController().normal();
      try {
        // Get profile scripts
        //
        final FileStatus[] profiles = m_session.listFiles(getLoggedInUserId(), ".profile.%.cmd");
        final int nProfiles = profiles.length;
        String profile = null;
        if (nProfiles == 1) {
          profile = profiles[0].getName();
        } else if (nProfiles > 1) {
          // More than one profile. Ask user.
          //
          for (; ; ) {
            m_out.println(m_formatter.format("login.profiles"));
            for (int idx = 0; idx < nProfiles; ++idx) {
              final String name = profiles[idx].getName();
              m_out.print(idx + 1);
              m_out.print(". ");
              m_out.println(name.substring(9, name.length() - 4));
            }
            m_out.println();
            m_out.print(m_formatter.format("login.choose.profile"));
            try {
              final String choiceStr = m_in.innerReadLine("1", "", 3, 0);
              try {
                final int choice = Integer.parseInt(choiceStr);
                profile = profiles[choice - 1].getName();
                break;
              } catch (final NumberFormatException | ArrayIndexOutOfBoundsException e) {
                // Bad choice
                //
                m_out.println();
                m_out.println(m_formatter.format("login.profil.invalid.choice"));
              }
            } catch (final EventDeliveredException e) {
              // Should not happen
              //
              throw new UnexpectedException(getLoggedInUserId(), e);
            }
          }
        }

        String loginScript = null;
        String profileScript = null;
        try {
          loginScript = m_session.readFile(getLoggedInUserId(), ".login.cmd");
          if (profile != null) {
            profileScript = m_session.readFile(getLoggedInUserId(), profile);
          }
        } catch (final ObjectNotFoundException e) {
          // No login script. Not much to do
          //
        }
        try {
          if (profileScript != null) {
            executeScript(profileScript);
          }
          if (loginScript != null) {
            executeScript(loginScript);
          }
        } catch (final OutputInterruptedException e) {
          m_out.println(e.formatMessage(this));
          m_out.println();
        } catch (final OperationInterruptedException e) {
          m_out.println(e.formatMessage(this));
          m_out.println();
        }
      } catch (final KOMException e) {
        m_out.println(e.formatMessage(this));
        m_out.println();
      } catch (final IOException e) {
        e.printStackTrace(m_out);
      } catch (final InterruptedException | ImmediateShutdownException e) {
        return;
      }

      // Start heartbeat sender
      //
      m_heartbeatSender = new HeartbeatSender();
      m_heartbeatSender.setName("Heartbeat sender (" + m_thisUserCache.getUserid() + ')');
      m_heartbeatSender.start();
      m_in.setKeystrokeListener(m_heartbeatSender);

      // Ensure that more prompts will be shown (if the user choose goAhead during login).
      getIn().setPageBreak(true);

      // MAIN SCREEN TURN ON!
      //
      mainloop();
      m_out.println();
      m_out.println();
      try {
        m_out.println(m_formatter.format("login.goodbye", getCachedUserInfo().getName()));
      } catch (final UnexpectedException e) {
        // Probably issues getting hold of user. Just get us out of here!
        //
        ClientSession.LOG.error("Error logging out", e);
      }
    } finally {
      // Shut down...
      //
      try {
        // Clean up...
        //
        if (m_session != null) {
          synchronized (this) {
            m_session.close();
            m_session = null;
          }
        }
      } catch (final Exception e) {
        // Ooops! Exception while cleaning up!
        //
        ClientSession.LOG.error(m_formatter.format("logout.failure"), e);
      }
    }
  }

  protected UserInfo login()
      throws AuthenticationException, LoginNotAllowedException, UnexpectedException, IOException,
          InterruptedException, OperationInterruptedException, LoginProhibitedException,
          NoSuchModuleException, AuthorizationException {
    // Find backend. We are going to need it
    //
    final ServerSessionFactory ssf = ServerSessionFactoryImpl.getInstance();
    final boolean selfRegister = ssf.allowsSelfRegistration();

    // Collect information
    //
    String ticket = null;
    String password = null;
    String userid = null;
    if (m_useTicket) {
      ticket = waitForTicket();
    } else {
      try {
        // Print self-registration prompt if requested
        //
        final String srToken = m_formatter.format("login.self.register.token");
        if (selfRegister) {

          m_out.println(m_formatter.format("login.self.register", srToken));
          m_out.println();
        }
        m_out.print(m_formatter.format("login.user"));
        m_out.flush();
        userid = m_in.readLine(null, null, 100, LineEditor.FLAG_ECHO);

        // Self registration?
        //
        if (selfRegister && srToken.equals(userid)) {
          final String[] login = selfRegister();
          userid = login[0];
          password = login[1];
        } else {
          // Normal login
          //
          ClientSession.LOG.info("Trying to login as: " + userid);
          m_out.print(m_formatter.format("login.password"));
          m_out.flush();
          password = m_in.readLine(null, null, 100, 0);
        }
      } catch (final LineEditorException e) {
        throw new RuntimeException("This should not happen!", e);
      }
    }

    // Authenticate
    //
    try {
      m_session =
          m_useTicket
              ? ssf.login(ticket, ClientTypes.TTY, false)
              : ssf.login(userid, password, ClientTypes.TTY, false);
      ssf.notifySuccessfulLogin(clientName);
    } catch (final AuthenticationException e) {
      // For ticket-based logins, this will be handles elsewhere
      //
      if (!m_useTicket) {
        ssf.notifyFailedLogin(clientName, retriesBeforeLockout, lockout);

        // Sleep for a while to make brute force attacks a bit harder...
        //
        final long failedLoginDelay = 3000;
        Thread.sleep(failedLoginDelay);

        // Rethrow, we will handle this in the mainloop of the shell
        //
        throw e;
      }
    } catch (final AlreadyLoggedInException e) {
      // Already logged in. Ask if they want to create another session
      //
      m_out.println(m_formatter.format("login.multiple.session"));
      m_out.println();

      // Print headers
      //
      final HeaderPrinter hp = new HeaderPrinter();
      hp.addHeader(m_formatter.format("list.sessions.session"), 7, true);
      hp.addHeader(m_formatter.format("list.sessions.login"), 7, true);
      hp.addHeader(m_formatter.format("list.sessions.idle"), 7, true);
      hp.addHeader(m_formatter.format("list.sessions.client"), 7, true);
      hp.addSpace(1);
      // int termWidth = this.getTerminalSettings().getWidth();
      // int firstColsWidth = 7 + 7 + 7 + 7 + 1;
      // int lastColWidth = termWidth - firstColsWidth - 1 ;
      hp.printOn(m_out);

      // Print list
      final List<SessionListItem> list = e.getSessions();
      for (final SessionListItem each : list) {
        PrintUtils.printRightJustified(m_out, Integer.toString(each.getSessionId()), 7);
        final long now = System.currentTimeMillis();
        PrintUtils.printRightJustified(
            m_out, StringUtils.formatElapsedTime(now - each.getLoginTime()), 7);
        final long idle = now - each.getLastHeartbeat();
        PrintUtils.printRightJustified(
            m_out,
            idle >= 60000 ? StringUtils.formatElapsedTime(now - each.getLastHeartbeat()) : "",
            7);
        m_out.print(' ');
        PrintUtils.printLeftJustified(
            m_out, m_formatter.format("clienttypes." + Integer.toString(each.getSessionType())), 7);
        m_out.println();
      }

      m_out.println();
      m_out.println(m_formatter.format("login.multiple.session.question.1"));
      m_out.println(m_formatter.format("login.multiple.session.question.2"));
      m_out.println(m_formatter.format("login.multiple.session.question.3"));
      m_out.println();
      m_out.flush();
      try {
        final int choice =
            m_in.getChoice(
                m_formatter.format("login.multiple.session.question"),
                new String[] {"1", "2", "3"},
                -1,
                m_formatter.format("misc.invalid.choice"));
        switch (choice) {
          case 0:
            // Kill this session
            //
            throw new InterruptedException();
          case 2:
            // Kill all other sessions
            //
            for (final Iterator<SessionListItem> itor = list.iterator(); itor.hasNext(); ) {
              if (m_useTicket) {
                ssf.killSession(itor.next().getSessionId(), m_ticket);
              } else {
                ssf.killSession(itor.next().getSessionId(), userid, password);
              }
            }

            // FALL THRU
          case 1:
            // Create new session
            //
            try {
              m_session =
                  m_useTicket
                      ? ssf.login(ticket, ClientTypes.TTY, true)
                      : ssf.login(userid, password, ClientTypes.TTY, true);
            } catch (final AlreadyLoggedInException e1) {
              // Huh? We DID allow this!
              //
              ClientSession.LOG.error("This cannot happen!", e1);
            }
            ClientSession.LOG.info("Allowed multiple login.");
        }
      } catch (final LineEditingDoneException e1) {
        throw new RuntimeException("This should not happen", e1);
      }
    }
    // User was authenticated! Now check if they are allowed to log in.
    //
    final UserInfo user = m_session.getLoggedInUser();
    if (!user.hasRights(UserPermissions.LOGIN)) {
      throw new LoginNotAllowedException();
    }

    // Everything seems fine! We're in!
    //
    m_in.setSession(m_session);
    m_loggedIn = true;
    ClientSession.LOG.info("Successfully logged in as: " + m_session.getLoggedInUser().getUserid());
    return user;
  }

  public String[] selfRegister()
      throws LineOverflowException, StopCharException, LineUnderflowException,
          EventDeliveredException, LineEditingDoneException, OperationInterruptedException,
          IOException, InterruptedException, NoSuchModuleException, AuthorizationException,
          UnexpectedException {
    // Find backend. We are going to need it
    //
    final ServerSessionFactory ssf = ServerSessionFactoryImpl.getInstance();

    // Ask for character set
    //
    String charSet;
    final ArrayList<String> list = new ArrayList<>();
    for (final StringTokenizer st = new StringTokenizer(ClientSettings.getCharsets(), ",");
        st.hasMoreTokens(); ) {
      list.add(st.nextToken());
    }
    for (; ; ) {
      m_out.println();
      try {
        final int n =
            Parser.askForResolution(
                this,
                list,
                "parser.parameter.charset.ask",
                true,
                "parser.parameter.charset.ask",
                false,
                "charset");
        charSet = list.get(n);
        m_in.setCharset(charSet);
        m_out.setCharset(charSet);
        break;
      } catch (final InvalidChoiceException e) {
        // Try again
      }
    }

    for (; ; ) {
      String password;
      String userid;
      String fullname;
      for (; ; ) {
        m_out.print(m_formatter.format("login.self.register.login"));
        m_out.flush();
        userid = m_in.readLine(null, null, 100, LineEditor.FLAG_ECHO);
        if (userid.length() == 0) {
          continue;
        }
        if (!ssf.loginExits(userid)) {
          break;
        }
        m_out.println(m_formatter.format("login.self.register.duplicate"));
        m_out.println();
      }
      for (; ; ) {
        m_out.print(m_formatter.format("login.self.register.password"));
        m_out.flush();
        password = m_in.readLine(null, null, 100, 0);
        if (password.length() == 0) {
          continue;
        }
        m_out.print(m_formatter.format("login.self.register.password.verify"));
        m_out.flush();
        final String verify = m_in.readLine(null, null, 100, 0);
        if (password.equals(verify)) {
          break;
        }
        m_out.println(m_formatter.format("login.self.register.password.mismatch"));
        m_out.println();
      }
      do {
        m_out.print(m_formatter.format("login.self.register.fullname"));
        m_out.flush();
        fullname = m_in.readLine(null, null, 100, LineEditor.FLAG_ECHO);
      } while (fullname.length() <= 0);

      // Register user
      //
      try {
        ssf.selfRegister(userid, password, fullname, charSet);
        m_out.println();
        return new String[] {userid, password, fullname};
      } catch (final DuplicateNameException | AmbiguousNameException e) {
        m_out.println(m_formatter.format("login.self.register.duplicate.name"));
        m_out.println();
      }
    }
  }

  public synchronized void shutdown() {
    if (m_heartbeatSender != null) {
      m_heartbeatSender.interrupt();
    }
    m_in.shutdown();
    if (m_session != null) {
      try {
        m_session.close();
      } catch (final Exception e) {
        // Trying to close an invalid session. Not much
        // to do really...
        //
        ClientSession.LOG.warn("Error while closing session: " + e.toString());
      }
      m_session = null;
    }
  }

  @Override
  public void executeScript(final String script)
      throws IOException, InterruptedException, KOMException {
    executeScript(new BufferedReader(new StringReader(script)));
  }

  @Override
  public void executeScript(final BufferedReader rdr)
      throws IOException, InterruptedException, KOMException {
    String line;
    while ((line = rdr.readLine()) != null) {
      line = line.trim();
      if (line.length() == 0 || line.charAt(0) == '#') {
        continue;
      }
      final ExecutableCommand executableCommand = m_parser.parseCommandLine(this, line);
      executableCommand.executeBatch(this);
      m_out.println();
    }
  }

  public void mainloop() {
    try {
      printCurrentConference();
    } catch (final ObjectNotFoundException | UnexpectedException e) {
      // TODO: Default conference deleted. What do we do???
      //
      ClientSession.LOG.error(e);
      m_out.println(e.formatMessage(this));
    }
    m_out.println();
    while (m_loggedIn) {
      // Determine default command and print prompt
      //
      try {
        // Print any pending chat messages
        //
        final DisplayController dc = getDisplayController();
        synchronized (m_displayMessageQueue) {
          while (!m_displayMessageQueue.isEmpty()) {
            final Event ev = m_displayMessageQueue.removeFirst();
            ev.dispatch(eventPrinter);
          }
        }
        final Command defaultCommand = getDefaultCommand();
        dc.prompt();

        // Build prompt
        //
        final StringBuilder promptBuffer = new StringBuilder(50);
        promptBuffer.append(defaultCommand.getFullName());
        if ((getCachedUserInfo().getFlags1() & UserFlags.SHOW_NUM_UNREAD) != 0
            && m_state.getNumUnread() > 0) {
          // Add number of unread to prompt
          //
          promptBuffer.append(" [");
          promptBuffer.append(m_state.getNumUnread());
          promptBuffer.append(']');
        }
        promptBuffer.append(" - ");
        final String prompt = promptBuffer.toString();
        m_out.print(prompt);
        dc.input();
        m_out.flush();

        // Clear pager line counter
        //
        m_in.resetLineCount();

        // Read command
        //
        final String cmdString;
        try {
          cmdString =
              m_in.readLine(
                  "",
                  "",
                  0,
                  LineEditor.FLAG_ECHO
                      | LineEditor.FLAG_RECORD_HISTORY
                      | LineEditor.FLAG_STOP_ON_EVENT
                      | LineEditor.FLAG_STOP_ONLY_WHEN_EMPTY
                      | LineEditor.FLAG_ALLOW_HISTORY
                      | ((getCachedUserInfo().getFlags1() & UserFlags.TREAT_SPACE_AS_NEWLINE) != 0
                          ? LineEditor.FLAG_TREAT_SPACE_AS_NEWLINE
                          : 0));
        } catch (final EventDeliveredException e) {
          // Interrupted by an event. Generate prompt and start
          // over again.
          //
          // Erase the prompt
          //
          if ((getCachedUserInfo().getFlags1() & UserFlags.BEEP_ON_NEW_MESSAGES) != 0
              && (e.getEvent() instanceof NewMessageEvent)) {
            m_out.print('\u0007'); // BEEP!
          }
          final int top = prompt.length();
          for (int idx = 0; idx < top; ++idx) {
            m_out.print("\b \b");
          }
          continue;
        }

        if (cmdString.trim().length() > 0) {
          final ExecutableCommand executableCommand = m_parser.parseCommandLine(this, cmdString);
          executableCommand.execute(this);
        } else {
          new ExecutableCommand(defaultCommand, new Object[0]).execute(this);
        }
      } catch (final OutputInterruptedException e) {
        m_out.println();
        m_out.println(e.formatMessage(this));
        m_out.println();
      } catch (final UserException e) {
        m_out.println();
        m_out.println(e.formatMessage(this));
        m_out.println();
      } catch (final InterruptedException | ImmediateShutdownException e) {
        // SOMEONE SET UP US THE BOMB! Let's get out of here!
        // Can happen if connection is lost, or if an admin
        // requested shutdown.
        //
        return;
      } catch (final KOMRuntimeException e) {
        m_out.println(e.formatMessage(this));
        m_out.println();
        ClientSession.LOG.error(e);
      } catch (final Exception e) {
        e.printStackTrace(m_out);
        m_out.println();
        ClientSession.LOG.error(e);
      }
    }
  }

  public Command getDefaultCommand() throws KOMException {
    m_state = m_session.getSessionState();
    final short suggestion = m_state.getSuggestedAction();
    switch (suggestion) {
      case CommandSuggestions.NEXT_SELECTED:
        return m_parser.getCommand(ReadNextSelectedMessage.class);
      case CommandSuggestions.NEXT_MAIL:
        return m_parser.getCommand(ReadNextMail.class);
      case CommandSuggestions.NEXT_REPLY:
        return m_parser.getCommand(ReadNextReply.class);
      case CommandSuggestions.NEXT_MESSAGE:
        return m_parser.getCommand(ReadNextMessage.class);
      case CommandSuggestions.NEXT_CONFERENCE:
        return m_parser.getCommand(GotoNextConference.class);
      case CommandSuggestions.NO_ACTION:
        return m_parser.getCommand(ShowTime.class);
      case CommandSuggestions.ERROR:
        m_out.println("TODO: Add message saying thing are screwed up");
        return m_parser.getCommand(ShowTime.class);
      default:
        ClientSession.LOG.warn("Unknown command suggestion: " + suggestion);
        return m_parser.getCommand(ShowTime.class);
    }
  }

  // Implementation of the Context interface
  //
  @Override
  public LineEditor getIn() {
    return m_in;
  }

  @Override
  public KOMWriter getOut() {
    return m_out;
  }

  @Override
  public MessageFormatter getMessageFormatter() {
    return m_formatter;
  }

  @Override
  public MessagePrinter getMessagePrinter() {
    boolean usecompact = false;
    try {
      usecompact = isFlagSet(0, UserFlags.USE_COMPACT_MESSAGEPRINTER);
    } catch (final UnexpectedException e) {
      // TODO: What to do?
    }
    if (usecompact) {
      return new CompactMessagePrinter();
    } else {
      return new BasicMessagePrinter();
    }
  }

  @Override
  public ServerSession getSession() {
    return m_session;
  }

  @Override
  public String formatObjectName(final Name name, final long id) {
    try {
      String nameStr =
          name.getKind() == NameManager.CONFERENCE_KIND && getLoggedInUserId() == id
              ? getMessageFormatter().format("misc.mailboxtitle")
              : name.getName();

      // Omit user suffixes if requested
      //
      if (name.getKind() == NameManager.USER_KIND
          && (getCachedUserInfo().getFlags1() & UserFlags.SHOW_SUFFIX) == 0) {
        nameStr = NameUtils.stripSuffix(nameStr);
      }
      final StringBuilder sb = new StringBuilder(nameStr.length() + 10);
      if (nameStr.length() == 0) {
        // Protected conference, just show hidden name.
        //
        nameStr = m_formatter.format("misc.protected.conference");
        sb.append(nameStr);
      } else {
        // Normal conference. Show name and maybe object id.
        //
        sb.append(nameStr);
        if ((getCachedUserInfo().getFlags1() & UserFlags.SHOW_OBJECT_IDS) != 0) {
          sb.append(" <");
          sb.append(id);
          sb.append('>');
        }
      }
      return sb.toString();
    } catch (final UnexpectedException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public String formatObjectName(final NameAssociation object) {
    return object == null
        ? m_formatter.format("misc.nobody")
        : formatObjectName(object.getName(), object.getId());
  }

  @Override
  public String smartFormatDate(final Date date) throws UnexpectedException {
    if (date == null) {
      return m_formatter.format("date.never");
    }

    // Check number of days from today
    //
    final Calendar then = Calendar.getInstance();
    then.setTime(date);
    then.setTimeZone(getCachedUserInfo().getTimeZone());
    String answer = m_formatter.format("timestamp.short", then.getTime());

    // Should we even try to be smart?
    //
    if ((getCachedUserInfo().getFlags1() & UserFlags.ALWAYS_PRINT_FULL_DATE) != 0) {
      return answer;
    }

    final Calendar now = Calendar.getInstance(getCachedUserInfo().getTimeZone());
    now.setTimeInMillis(System.currentTimeMillis());

    // Today or yesterday?
    //
    // Future date? Format the usual way
    //
    if (now.before(then)) {
      return answer;
    }
    final int yearNow = now.get(Calendar.YEAR);
    final int yearThen = then.get(Calendar.YEAR);
    int dayNow = now.get(Calendar.DAY_OF_YEAR);
    final int dayThen = then.get(Calendar.DAY_OF_YEAR);
    if (yearNow == yearThen + 1) {
      dayNow += then.getActualMaximum(Calendar.DAY_OF_YEAR);
    } else {
      if (yearNow != yearThen) {
        return answer;
      }
    }
    final int dayDiff = dayNow - dayThen;
    if (dayDiff == 0) {
      answer = m_formatter.format("date.today");
    } else if (dayDiff == 1) {
      answer = m_formatter.format("date.yesterday");
    } else if (dayDiff < 7) {
      answer = m_dateSymbols.getWeekdays()[then.get(Calendar.DAY_OF_WEEK)];
      answer = answer.substring(0, 1).toUpperCase() + answer.substring(1);
    } else {
      return answer;
    }
    return answer + ", " + m_formatter.format("time.short", then.getTime());
  }

  @Override
  public DisplayController getDisplayController() {
    try {
      return (getCachedUserInfo().getFlags1() & UserFlags.ANSI_ATTRIBUTES) != 0
          ? new ANSIDisplayController(m_out)
          : new DummyDisplayController(m_out);
    } catch (final UnexpectedException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public TerminalController getTerminalController() {
    // TODO: Read from configuration
    //
    return new ANSITerminalController(getOut());
  }

  @Override
  public void printCurrentConference() throws ObjectNotFoundException, UnexpectedException {
    // Determine name of conference. Give a generic name
    // to mailboxes.
    //
    getDisplayController().input();
    final ConferenceInfo conf = m_session.getCurrentConference();
    final long id = conf.getId();
    final String confName = formatObjectName(conf.getName(), id);

    // Calculate number of messages and print greeting
    //
    final int numMessages = m_session.countUnread(id);
    m_out.println(
        m_formatter.format(
            numMessages == 1 ? "misc.one.unread.message" : "misc.enter.conference",
            new String[] {
              confName,
              numMessages == 0 ? m_formatter.format("misc.no.messages") : Long.toString(numMessages)
            }));
  }

  @Override
  public MessageEditor getMessageEditor() throws UnexpectedException {
    try {
      return (getCachedUserInfo().getFlags1() & UserFlags.USE_FULL_SCREEN_EDITOR) != 0
          ? new FullscreenMessageEditor(this)
          : new SimpleMessageEditor(this);
    } catch (final IOException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public TerminalSettings getTerminalSettings() {
    return new TerminalSettings(
        m_windowHeight != -1 ? m_windowHeight : 24,
        m_windowWidth != -1 ? m_windowWidth : 80,
        "ANSI");
  }

  @Override
  public void setTerminalHeight(final int height) {
    m_windowHeight = height;
  }

  @Override
  public void setTerminalWidth(final int width) {
    m_windowWidth = width;
  }

  @Override
  public void setListenToTerminalSize(final boolean value) {
    m_ListenToTerminalSize = value;
  }

  @Override
  public void printDebugInfo() {
    m_out.println(m_session.getDebugString());
  }

  @Override
  public long getLoggedInUserId() {
    return m_userId;
  }

  @Override
  public synchronized UserInfo getCachedUserInfo() throws UnexpectedException {
    try {
      if (m_thisUserCache == null) {
        m_thisUserCache = m_session.getUser(getLoggedInUserId());
      }
      return m_thisUserCache;
    } catch (final ObjectNotFoundException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public synchronized void clearUserInfoCache() {
    m_thisUserCache = null;
  }

  @Override
  public boolean isFlagSet(final int flagWord, final long mask) throws UnexpectedException {
    return (getCachedUserInfo().getFlags()[flagWord] & mask) == mask;
  }

  @Override
  public WordWrapper getWordWrapper(final String content) {
    return m_wordWrapperFactory.create(content, getTerminalSettings().getWidth());
  }

  @Override
  public WordWrapper getWordWrapper(final String content, final int width) {
    return m_wordWrapperFactory.create(content, width);
  }

  public WordWrapper getWordWrapper(final String content, final int width, final int offset) {
    return m_wordWrapperFactory.create(content, width, offset);
  }

  @Override
  public String[] getFlagLabels(final String flagTable) {
    return loadFlagTable(flagTable);
  }

  @Override
  public void checkName(final String name)
      throws DuplicateNameException, InvalidNameException, UnexpectedException {
    // Check that name is lexigraphically correct
    //
    if (!NameUtils.isValidName(name)) {
      throw new InvalidNameException(name);
    }

    // Check for conflict with mailbox name
    //
    if (NameUtils.normalizeName(name)
        .equals(NameUtils.normalizeName(m_formatter.format("misc.mailboxtitle")))) {
      throw new DuplicateNameException(name);
    }

    // Check for conflict with existing name, ignoring suffixes
    //
    final String normalized = NameUtils.stripSuffix(NameUtils.normalizeName(name));

    // Nothing left after normalizing name? That's not legal!
    //
    if (normalized.length() == 0) {
      throw new InvalidNameException(name);
    }
    final NameAssociation[] names = m_session.getAssociationsForPattern(normalized);
    for (final NameAssociation nameAssociation : names) {
      final String each = nameAssociation.getName().toString();
      if (NameUtils.stripSuffix(NameUtils.normalizeName(each)).equals(normalized)) {
        throw new DuplicateNameException(name);
      }
    }
  }

  // Implementation of EventTarget
  //
  @Override
  public void onEvent(final Event e) {
    System.out.println("Unknown Event: " + e.toString());
  }

  @Override
  public void onEvent(final ChatMessageEvent event) {
    // Put it on a queue until there's a good time to display it!
    //
    synchronized (m_displayMessageQueue) {
      m_displayMessageQueue.addLast(event);
    }
  }

  @Override
  public void onEvent(final BroadcastMessageEvent event) {
    // Put it on a queue until there's a good time to display it!
    //
    synchronized (m_displayMessageQueue) {
      m_displayMessageQueue.addLast(event);
    }
  }

  @Override
  public void onEvent(final BroadcastAnonymousMessageEvent event) {
    // Put it on a queue until there's a good time to display it!
    //
    synchronized (m_displayMessageQueue) {
      m_displayMessageQueue.addLast(event);
    }
  }

  @Override
  public void onEvent(final ChatAnonymousMessageEvent event) {
    // Put it on a queue until there's a good time to display it!
    //
    synchronized (m_displayMessageQueue) {
      m_displayMessageQueue.addLast(event);
    }
  }

  @Override
  public void onEvent(final UserAttendanceEvent event) {
    // Put it on a queue until there's a good time to display it!
    //
    synchronized (m_displayMessageQueue) {
      m_displayMessageQueue.addLast(event);
    }
  }

  @Override
  public synchronized void onEvent(final ReloadUserProfileEvent event) {
    // Invalidate user info cache
    //
    m_thisUserCache = null;
  }

  @Override
  public void onEvent(final NewMessageEvent event) {
    // Handled in command loop
  }

  @Override
  public void onEvent(final MessageDeletedEvent event) {
    // Handled in command loop
  }

  @Override
  public void onEvent(final TicketDeliveredEvent event) {
    // TODO: Implement
  }

  public void onEvent(final DetachRequestEvent event) {
    detach();
  }

  // Implementation of TerminalSizeListener
  //
  @Override
  public void terminalSizeChanged(final int width, final int height) {
    if (m_ListenToTerminalSize) {
      m_windowWidth = width;
      m_windowHeight = height;
    }
  }

  @Override
  public void environmentChanged(final String name, final String value) {
    if (!"TICKET".equals(name)) {
      return;
    }
    ClientSession.LOG.debug("Received ticket");
    m_in.handleEvent(new TicketDeliveredEvent(getLoggedInUserId(), value));
  }

  protected String waitForTicket() throws InterruptedException, IOException {
    // Do we already have a ticket?
    //
    if (m_ticket != null) {
      return m_ticket;
    }
    for (; ; ) {
      try {
        m_in.readLine("", "", 0, LineEditor.FLAG_STOP_ON_EVENT);
      } catch (final EventDeliveredException e) {
        ClientSession.LOG.debug("Got event while waiting for ticket: " + e.getClass().getName());
        final Event ev = e.getEvent();
        if (ev instanceof TicketDeliveredEvent) {
          return ((TicketDeliveredEvent) ev).getTicket();
        }
      } catch (final LineEditingDoneException e) {
        throw new RuntimeException("This should not happen!", e);
      } catch (final LineUnderflowException | LineOverflowException | StopCharException e) {
        // Ignore
      } catch (final OperationInterruptedException e) {
        throw new InterruptedException();
      }
    }
  }

  protected void installCommands() throws UnexpectedException {

    try {
      m_parser = Parser.load("commands.xml", this);
    } catch (final IOException e) {
      throw new UnexpectedException(-1, e);
    }
  }

  public String[] loadFlagTable(final String prefix) {
    final String[] flagLabels = new String[UserFlags.NUM_FLAGS];
    for (int idx = 0; idx < UserFlags.NUM_FLAGS; ++idx) {
      // Calculate flag word and flag bit index
      //
      final int flagWord = 1 + (idx / 64);
      final long flagBit = (long) 1 << (long) (idx % 64);
      final String hex = Long.toHexString(flagBit);

      // Build message key
      //
      final StringBuilder buf = new StringBuilder();
      buf.append(prefix);
      buf.append('.');
      buf.append(flagWord);
      buf.append('.');
      final int top = 8 - hex.length();
      for (int idx2 = 0; idx2 < top; ++idx2) {
        buf.append('0');
      }
      buf.append(hex);

      // Get flag label
      //
      flagLabels[idx] = m_formatter.getStringOrNull(buf.toString());
    }
    return flagLabels;
  }

  /**
   * Returns an array of all Commands that are available to the user.
   *
   * @return An Command[] of available commands.
   */
  public Command[] getCommandList() {
    return m_parser.getCommandList();
  }

  @Override
  public Parser getParser() {
    return m_parser;
  }

  public void setTicket(final String ticket) {
    m_ticket = ticket;
  }

  public void detach() {
    m_session = null;
    m_loggedIn = false;
  }

  public void logout() {
    m_loggedIn = false;
  }

  private class HeartbeatSender extends Thread implements KeystrokeListener {
    private boolean m_idle = false;

    @Override
    public void keystroke(final char ch) {
      // Are we idle? Send heartbeat immediately!
      //
      if (m_idle) {
        synchronized (this) {
          notify();
        }
        m_idle = false;
      }
    }

    @Override
    public void run() {

      for (; ; ) {
        // Sleep for 30 seconds
        //
        try {
          synchronized (this) {
            wait(30000);
          }
        } catch (final InterruptedException e) {
          break;
        }

        // Any activity the last 30 seconds? Send hearbeat!
        //
        if (System.currentTimeMillis() - m_in.getLastKeystrokeTime() < 30000) {
          getSession().getHeartbeatListener().heartbeat();
        } else {
          m_idle = true;
        }
      }
    }
  }

  private class EventPrinter implements EventTarget {
    private final Context context;

    public EventPrinter(final Context context) {
      this.context = context;
    }

    private void printWrapped(final String message, final int offset) {
      final WordWrapper ww = getWordWrapper(message, getTerminalSettings().getWidth(), offset);
      String line;
      while ((line = ww.nextLine()) != null) {
        m_out.println(line);
      }
    }

    @Override
    public void onEvent(final Event event) {
      // Unknown event. Not much we can do.
    }

    @Override
    public void onEvent(final ChatMessageEvent event) {
      beepMaybe(UserFlags.BEEP_ON_CHAT);
      final String header =
          m_formatter.format(
              "event.chat",
              new Object[] {formatObjectName(event.getUserName(), event.getOriginatingUser())});
      getDisplayController().chatMessageHeader();
      m_out.print(header);
      getDisplayController().chatMessageBody();
      printWrapped(event.getMessage(), header.length());
      m_out.println();
    }

    @Override
    public void onEvent(final BroadcastMessageEvent event) {
      try {
        final boolean more =
            (context.getCachedUserInfo().getFlags1() & UserFlags.MORE_PROMPT_IN_BROADCAST) != 0;
        context.getIn().setPageBreak(more);
        beepMaybe(UserFlags.BEEP_ON_BROADCAST);
        final String name = formatObjectName(event.getUserName(), event.getOriginatingUser());
        final String header =
            event.getKind() == MessageLogKinds.BROADCAST
                ? m_formatter.format("event.broadcast.default", new Object[] {name})
                : name + ' ';
        getDisplayController().broadcastMessageHeader();
        m_out.print(header);
        if (event.getKind() == MessageLogKinds.BROADCAST) {
          getDisplayController().broadcastMessageBody();
        }
        printWrapped(event.getMessage(), header.length());
        m_out.println();
        context.getIn().setPageBreak(true);
      } catch (final UnexpectedException e) {
        ClientSession.LOG.error("Error in broadcast message handler", e);
      }
    }

    @Override
    public void onEvent(final ChatAnonymousMessageEvent event) {
      beepMaybe(UserFlags.BEEP_ON_CHAT);
      final String header = m_formatter.format("event.chat.anonymous");
      getDisplayController().chatMessageHeader();
      m_out.print(header);
      getDisplayController().chatMessageBody();
      printWrapped(event.getMessage(), header.length());
      m_out.println();
    }

    @Override
    public void onEvent(final BroadcastAnonymousMessageEvent event) {
      beepMaybe(UserFlags.BEEP_ON_BROADCAST);
      final String header = m_formatter.format("event.broadcast.anonymous");
      getDisplayController().broadcastMessageHeader();
      m_out.print(header);
      getDisplayController().broadcastMessageBody();
      printWrapped(event.getMessage(), header.length());
      m_out.println();
    }

    @Override
    public void onEvent(final NewMessageEvent event) {
      // This event should not be handled here
    }

    @Override
    public void onEvent(final UserAttendanceEvent event) {
      getDisplayController().broadcastMessageHeader();
      beepMaybe(UserFlags.BEEP_ON_ATTENDANCE);
      m_out.println(
          m_formatter.format(
              "event.attendance." + event.getType(),
              new Object[] {
                context.formatObjectName(event.getUserName(), event.getOriginatingUser())
              }));
      m_out.println();
    }

    @Override
    public void onEvent(final ReloadUserProfileEvent event) {
      // This event should not be handled here
    }

    @Override
    public void onEvent(final MessageDeletedEvent event) {
      // This event should not be handled here
    }

    protected void beepMaybe(final long flag) {
      try {
        // Beep if user wants it
        //
        if ((getCachedUserInfo().getFlags1() & flag) != 0) {
          m_out.print('\u0007');
        }
      } catch (final UnexpectedException e) {
        // Should NOT happen!
        //
        throw new RuntimeException(e);
      }
    }
  }
}
