/*
 * Created on Sep 17, 2004
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.modules;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import nu.rydin.kom.backend.ServerSessionFactory;
import nu.rydin.kom.exceptions.AuthenticationException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** @author Pontus Rydin */
public class AuthenticationServer implements Module, Runnable {
  private static final Logger LOG = LogManager.getLogger(AuthenticationServer.class);
  private Thread m_thread;
  private ServerSocket m_socket;

  @Override
  public void start(final Map<String, String> parameters) {
    final int port = Integer.parseInt((String) parameters.get("port"));
    try {
      m_socket = new ServerSocket(port);
    } catch (final IOException e) {
      // We can't even listen on the socket. Most likely, someone
      // else is already listening to that port. In any case, we're
      // out of here!
      //
      LOG.fatal(e);
      return;
    }
    LOG.info("OpenKOM authentication server is accepting connections at port " + port);

    m_thread = new Thread(this, "AuthenticationServer");
    m_thread.start();
  }

  @Override
  public void stop() {
    m_thread.interrupt();
  }

  @Override
  public void join() throws InterruptedException {
    m_thread.join();
  }

  @Override
  public void run() {
    for (; ; ) {
      try {
        final Socket sock = m_socket.accept();

        // We have an incoming call. Go ahead and process it in a separate thread
        //
        final AuthenticationWorker worker = new AuthenticationWorker(sock);
        worker.start();
      } catch (final IOException e) {
        // Error accepting. Not good. Try again!
        //
        e.printStackTrace();
        continue;
      }
    }
  }

  private static class AuthenticationWorker extends Thread {
    private final Socket m_sock;

    public AuthenticationWorker(final Socket sock) {
      m_sock = sock;
    }

    @Override
    public void run() {
      // We have an incoming call. Go ahead and process it
      //
      try {
        final BufferedReader rdr =
            new BufferedReader(new InputStreamReader(m_sock.getInputStream()));
        final PrintStream out = new PrintStream(m_sock.getOutputStream());

        // Read username and password from client
        //
        final String user = rdr.readLine();
        final String password = rdr.readLine();

        // We have authentication info. Go authenticate
        //
        try {
          final String ticket =
              ((ServerSessionFactory) Modules.getModule("Backend")).generateTicket(user, password);
          out.print("OK:");
          out.print(ticket);
          out.print("\r\n");
        } catch (final AuthenticationException e) {
          // No go!
          //
          out.print("FAIL\r\n");
        }
        out.flush();

      } catch (final Exception e) {
        // Something went terribly wrong!
        //
        LOG.error(e);
      } finally {
        try {
          m_sock.close();
        } catch (final IOException e) {
          LOG.error("Error closing socket", e);
        }
      }
    }
  }
}
