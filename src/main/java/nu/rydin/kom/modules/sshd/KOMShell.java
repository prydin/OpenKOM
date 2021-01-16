package nu.rydin.kom.modules.sshd;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import nu.rydin.kom.exceptions.InternalException;
import nu.rydin.kom.exceptions.UnexpectedException;
import nu.rydin.kom.frontend.text.ClientSession;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.shell.ShellFactory;

public class KOMShell implements Command {
  private final SSHD server;

  private InputStream in;

  private OutputStream out;

  private OutputStream err;

  private ClientSession session;

  public KOMShell(final SSHD server) {
    this.server = server;
  }

  public SSHD getServer() {
    return server;
  }

  @Override
  public void setInputStream(final InputStream in) {
    this.in = in;
  }

  @Override
  public void setOutputStream(final OutputStream out) {
    this.out = out;
  }

  @Override
  public void setErrorStream(final OutputStream err) {
    this.err = err;
  }

  // @Override
  @Override
  public void setExitCallback(final org.apache.sshd.server.ExitCallback callback) {}

  @Override
  public void start(final ChannelSession channel, final Environment env) throws IOException {
    try {
      session =
          new ClientSession(
              in,
              out,
              true,
              false,
              channel.getServerSession().getClientAddress().toString(),
              server.getParameters());
      session.setTicket(server.getTicket(channel.getSession()));
      final Map<String, String> e = env.getEnv();

      // Set initial terminal size
      if (e.containsKey("COLUMNS")) {
        session.setTerminalWidth(Integer.parseInt(e.get("COLUMNS")));
      }
      if (e.containsKey("LINES")) {
        session.setTerminalHeight(Integer.parseInt(e.get("LINES")));
      }

      /// And subscribe to any future changes
      channel.addRequestHandler(
          (channel1, request, wantReply, buffer) -> {
            if (!"window-change".equals(request)) {
              return null;
            }
            final int width = buffer.getInt();
            final int height = buffer.getInt();
            session.setTerminalHeight(height);
            session.setTerminalWidth(width);
            return null;
          });
      new Thread(
              () -> {
                try {
                  session.run();
                } finally {
                  server.revokeTicket(channel.getSession());
                }
              })
          .start();
    } catch (final UnexpectedException | InternalException e) {
      throw new IOException("Error initializing KOM session", e);
    }
  }

  @Override
  public void destroy(final ChannelSession channel) throws Exception {
    session.shutdown();
  }

  public static class Factory implements ShellFactory {
    private final SSHD server;

    public Factory(final SSHD server) {
      this.server = server;
    }

    @Override
    public Command createShell(final ChannelSession channel) throws IOException {
      return new KOMShell(server);
    }
  }
}
