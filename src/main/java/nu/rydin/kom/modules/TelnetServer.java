/*
 * Created on Nov 10, 2003
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.modules;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import nu.rydin.kom.backend.ServerSessionFactory;
import nu.rydin.kom.backend.ServerSessionFactoryImpl;
import nu.rydin.kom.frontend.text.ClientSession;
import nu.rydin.kom.frontend.text.TelnetInputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A simple telnet server. Listens to a port, and kicks of threads handling the sessions when an
 * incoming connection is detected.
 *
 * @author Pontus Rydin
 */
public class TelnetServer implements Module, Runnable {
  private static final Logger LOG = LogManager.getLogger(TelnetServer.class);
  private ServerSocket m_socket;
  private Thread m_thread;
  private boolean m_useTicket;
  private boolean m_selfRegister;
  private Map<String, String> parameters;

  public static void main(final String[] args) {
    TelnetServer.LOG.fatal(
        "Starting TelnetServer directly is no longer supported. Use rydin.nu.kom.boot.Bootstrap instead.");
  }

  @Override
  public void start(final Map<String, String> parameters) {
    this.parameters = parameters;

    // Perform checks before start.
    //
    final int port = Integer.parseInt(parameters.get("port"));
    m_useTicket = "ticket".equals(parameters.get("authentication"));
    m_selfRegister = "true".equals(parameters.get("selfRegister"));

    try {
      m_socket = new ServerSocket(port);
      m_socket.setReceiveBufferSize(65536);
    } catch (final IOException e) {
      // We can't even listen on the socket. Most likely, someone
      // else is already listening to that port. In any case, we're
      // out of here!
      //
      TelnetServer.LOG.fatal(e);
      return;
    }
    TelnetServer.LOG.info("OpenKOM telnet server is accepting connections at port " + port);

    // Start server thread
    //
    m_thread = new Thread(this, "Telnet Server");
    m_thread.start();
  }

  @Override
  public void stop() {
    try {
      m_thread.interrupt();
      m_socket.close();
    } catch (final IOException e) {
      TelnetServer.LOG.error("Exception while shutting down", e);
    }
  }

  @Override
  public void join() throws InterruptedException {
    if (m_thread != null) {
      m_thread.join();
    }
  }

  @Override
  public void run() {
    final ServerSessionFactory ssf;
    ssf = ServerSessionFactoryImpl.getInstance();

    for (; ; ) {
      try {
        // Wait for someone to connect
        //
        final Socket incoming = m_socket.accept();
        incoming.setKeepAlive(true);
        final String clientName = incoming.getInetAddress().getHostAddress();

        // Check if connection is blacklisted
        //
        if (ssf.isBlacklisted(clientName)) {
          TelnetServer.LOG.info("Rejecting blacklisted client: " + clientName);
        }
        TelnetServer.LOG.info(
            "Incoming connection from "
                + clientName
                + ". Buffer size="
                + incoming.getReceiveBufferSize());
        try {
          // Create session
          //
          final TelnetInputStream eis =
              new TelnetInputStream(incoming.getInputStream(), incoming.getOutputStream());
          final ClientSession client =
              new ClientSession(
                  eis,
                  incoming.getOutputStream(),
                  m_useTicket,
                  m_selfRegister,
                  clientName,
                  parameters);
          eis.addSizeListener(client);
          eis.addEnvironmentListener(client);

          // Create a thread to handle the session and kick it off!
          // Also create a SessionReaper that will be woken up when the
          // session dies to perform post-mortem cleanup.
          //
          final Thread clientThread = new Thread(client, "Session (not logged in)");
          final Thread reaper = new SessionReaper(incoming, clientThread, client);
          reaper.start();
          clientThread.start();
        } catch (final Exception e) {
          // Couldn't create session. Kill connection!
          //
          e.printStackTrace();
          incoming.close();
        }
      } catch (final IOException e) {
        // Error accepting. Not good. Try again!
        //
        e.printStackTrace();
      }
    }
  }

  /**
   * Cleans up after dead telnet session threads.
   *
   * @author Pontus Rydin
   */
  private static class SessionReaper extends Thread {
    private Socket m_socket;

    private Thread m_clientThread;

    private ClientSession m_session;

    public SessionReaper(
        final Socket socket, final Thread clientThread, final ClientSession session) {
      super("SessionReaper");
      m_socket = socket;
      m_clientThread = clientThread;
      m_session = session;
    }

    @Override
    public void run() {
      // Wait for the client to die
      //
      try {
        m_clientThread.join();
        m_session.shutdown();
        TelnetServer.LOG.info("Disconnected from " + m_socket.getInetAddress().getHostAddress());
      } catch (final InterruptedException e) {
        // Interrupted while waiting? We're going down, so
        // just fall thru and kill the connection.
        //
      } finally {
        try {
          m_socket.close();
        } catch (final IOException e) {
          // IO error here? Tough luck...
          //
          e.printStackTrace();
        }

        // Release references
        //
        m_socket = null;
        m_clientThread = null;
        m_session = null;
      }
    }
  }
}
