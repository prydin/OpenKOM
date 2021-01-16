package nu.rydin.kom.modules.sshd;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CountDownLatch;
import nu.rydin.kom.exceptions.ModuleException;
import nu.rydin.kom.modules.Module;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.sshd.common.keyprovider.FileKeyPairProvider;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.session.ServerSession;

public class SSHD implements Module {
  private static final Logger LOG = LogManager.getLogger(SSHD.class);
  private final WeakHashMap<ServerSession, String> activeTickets = new WeakHashMap<>();
  private final CountDownLatch stopped = new CountDownLatch(1);
  private Map<String, String> parameters;
  private SshServer sshd;

  public Map<String, String> getParameters() {
    return parameters;
  }

  public void setParameters(final Map<String, String> parameters) {
    this.parameters = parameters;
  }

  public synchronized void putTicket(final ServerSession session, final String token) {
    activeTickets.put(session, token);
  }

  public synchronized String getTicket(final ServerSession session) {
    return activeTickets.get(session);
  }

  public synchronized void revokeTicket(final ServerSession session) {
    activeTickets.remove(session);
  }

  @Override
  public void start(final Map<String, String> parameters) throws ModuleException {
    this.parameters = parameters;
    sshd = SshServer.setUpDefaultServer();
    /*
    <parameter name="privatekey" value="chamgeme"/>
               <parameter name="port" value="22"/>
               <parameter name="maxauthretry" value="3"/>
               <parameter name="maxconn" value="1000"/>
               <parameter name="selfRegister" value="false"/>
               <parameter name="lockout" value="1"/>
               <parameter name="attempts" value="9"/>

    */
    sshd.setPort(Integer.parseInt(parameters.get("port")));
    sshd.setPasswordAuthenticator(new Authenticator(this));
    sshd.setShellFactory(new KOMShell.Factory(this));
    sshd.setKeyPairProvider(new FileKeyPairProvider(Path.of(parameters.get("privatekey"))));
    try {
      sshd.start();
    } catch (final IOException e) {
      throw new ModuleException("Error starting SSHD", e);
    }
  }

  @Override
  public void stop() {
    try {
      sshd.stop();
      stopped.countDown();
    } catch (final IOException e) {
      SSHD.LOG.error("Error when stopping SSHD", e);
    }
  }

  @Override
  public void join() throws InterruptedException {
    stopped.await();
  }
}
