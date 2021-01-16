/*
 * Created on Nov 11, 2003
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.backend;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import nu.rydin.kom.events.Event;
import nu.rydin.kom.events.EventTarget;
import nu.rydin.kom.events.SessionShutdownEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Holds the currently active sessions.
 *
 * @author Pontus Rydin
 */
public class SessionManager {
  private static final Logger LOG = LogManager.getLogger(SessionManager.class);
  /** Currently active sessions keyed by session id */
  private final Map<Integer, ServerSession> m_sessionsById =
      Collections.synchronizedMap(new HashMap<>());

  /** Currently active sessions as an ordered list. */
  private final List<ServerSession> m_orderedList =
      Collections.synchronizedList(new LinkedList<>());

  /** Queue of events to be broadcasted */
  private final LinkedList<Event> m_broadcastQueue = new LinkedList<>();

  private Broadcaster m_broadcaster;
  private boolean m_allowLogin;

  public SessionManager() {
    m_allowLogin = true;
  }

  public void start() {
    m_broadcaster = new Broadcaster();
    m_broadcaster.start();
  }

  public void stop() {
    m_broadcaster.interrupt();
  }

  public void join() throws InterruptedException {
    m_broadcaster.join();
  }

  public boolean canLogin() {
    return m_allowLogin;
  }

  public void allowLogin() {
    m_allowLogin = true;
  }

  public void prohibitLogin() {
    m_allowLogin = false;
  }

  public void killSessionById(final int sessionId) throws InterruptedException {
    final ServerSession session = getSessionById(sessionId);

    // Not logged in? Nothing to shut down. Fail silently.
    //
    if (session == null) {
      return;
    }

    // Post shutdown event
    //
    session.postEvent(new SessionShutdownEvent());

    // Wait for session to terminate
    //
    int top = ServerSettings.getSessionShutdownRetries();
    final long delay = ServerSettings.getSessionShutdownDelay();
    while (top-- > 0) {
      // Has it disappeared yet?
      //
      if (getSessionById(sessionId) == null) {
        return;
      }
      Thread.sleep(delay);
    }

    // Bummer! The session did not shut down when we asked
    // it nicely. Mark it as invalid so that the next request
    // to the server is guaranteed to fail.
    //
    final ServerSessionImpl ssi = (ServerSessionImpl) getSessionById(sessionId);
    unRegisterSession(ssi);
    ssi.markAsInvalid();
  }

  /**
   * Registers a session
   *
   * @param session The session
   */
  public synchronized void registerSession(final ServerSession session) {
    m_sessionsById.put(session.getSessionId(), session);
    m_orderedList.add(session);
  }

  /**
   * Unregisteres a session
   *
   * @param session The session
   */
  public synchronized void unRegisterSession(final ServerSession session) {
    m_sessionsById.remove(session.getSessionId());
    m_orderedList.remove(session);
  }

  /**
   * Returns a session based on its user id
   *
   * @param sessionId The session id
   */
  public synchronized ServerSession getSessionById(final int sessionId) {
    return m_sessionsById.get(sessionId);
  }

  /** Lists the sessions in the order they were created. */
  public synchronized List<ServerSession> listSessions() {
    return new LinkedList<>(m_orderedList);
  }

  public List<ServerSession> getSessionsByUser(final long u) {
    final ArrayList<ServerSession> list = new ArrayList<>();
    for (final ServerSession each : m_orderedList) {
      if (each.getLoggedInUserId() == u) {
        list.add(each);
      }
    }
    return list;
  }

  /**
   * Checks if the given user currently has an open session.
   *
   * @param u The user ID.
   */
  public synchronized boolean userHasSession(final long u) {
    return getSessionsByUser(u).size() > 0;
  }

  /**
   * Broadcasts an event to all currently active sessions
   *
   * @param e The event
   */
  public synchronized void broadcastEvent(final Event e) {
    m_broadcastQueue.addLast(e);
    notify();
  }

  /**
   * Sends an event to a specified user
   *
   * @param user The user
   * @param e The event
   */
  public void sendEvent(final long user, final Event e) {
    final List<ServerSession> s;
    synchronized (this) {
      s = getSessionsByUser(user);
    }

    // Fail silently if we couldn't find any sessions for the user
    //
    for (final ServerSession serverSession : s) {
      e.dispatch((EventTarget) serverSession);
    }
  }

  private class Broadcaster extends Thread {
    private static final int INITIAL_RETRY_WAIT = 1000;
    private static final int RETRY_WAIT_INC = 1000;
    private static final int MAX_RETRY_WAIT = 5000;

    public Broadcaster() {
      super("Broadcaster");
      setDaemon(true);
    }

    @Override
    public void run() {
      try {
        for (; ; ) {
          final SessionManager sm = SessionManager.this;
          final List<ServerSession> sessions;
          final Event e;
          synchronized (sm) {
            while (sm.m_broadcastQueue.isEmpty()) {
              sm.wait();
            }
            e = sm.m_broadcastQueue.removeFirst();
            sessions = sm.listSessions();
          }

          // Note: We're working on a snapshot here, which means that
          // we might actually end up sending events to sessions that
          // are on their way down and miss sessions that are appearing
          // as we do this. The alternative would have been to lock the
          // queue while we're doing this. Since the possible race-condition
          // is reasonably benign and the cost of locking the queue might
          // potentially be high (event handlers do not execute in
          // deterministic time), we accept that trade-off.
          //
          int retryWait = INITIAL_RETRY_WAIT;

          // Create snapshot to iterate over
          //
          for (final Iterator<ServerSession> itor = sessions.iterator(); itor.hasNext(); ) {
            for (; ; ) {
              // We absolutely don't want this thread to die, so we need
              // to handle exceptions carefully.
              //
              try {
                final ServerSession each = itor.next();

                // Don't send to originator unless the event explicitly ask
                // for it.
                //
                if (!e.sendToSelf() && each.getLoggedInUserId() == e.getOriginatingUser()) {
                  break;
                }
                final ServerSessionImpl sess = (ServerSessionImpl) each;
                sess.acquireMutex();
                try {
                  e.dispatch((EventTarget) each);
                } finally {
                  sess.releaseMutex();
                }

                // If we get here, we didn't run into any problems
                // and we don't have to retry.
                //
                break;
              } catch (final InterruptedException ex) {
                // Shutting down, we're outta here!
                //
                throw ex;
              } catch (final Throwable t) {
                // Log this!
                //
                LOG.error("Exception in Broadcaster", t);

                // Unhandled exception. Wait and retry. We increase the wait
                // for every failure and eventually give up.
                //
                if (retryWait > MAX_RETRY_WAIT) {
                  // We've exceeded the max retry time. Skip this session!
                  //
                  LOG.warn("Giving up!");
                  retryWait = INITIAL_RETRY_WAIT;
                  break;
                }

                // Try again, but first, wait a little
                //
                LOG.warn("Retrying... ");
                Thread.sleep(retryWait);
                retryWait += RETRY_WAIT_INC;
              }
            }
          }
        }
      } catch (final InterruptedException e) {
        // Graceful shutdown
      }
    }
  }
}
