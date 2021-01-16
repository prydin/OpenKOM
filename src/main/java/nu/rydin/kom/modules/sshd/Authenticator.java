package nu.rydin.kom.modules.sshd;

import java.util.Map;
import nu.rydin.kom.backend.ServerSessionFactory;
import nu.rydin.kom.backend.ServerSessionFactoryImpl;
import nu.rydin.kom.exceptions.AuthenticationException;
import nu.rydin.kom.exceptions.NoSuchModuleException;
import nu.rydin.kom.exceptions.UnexpectedException;
import nu.rydin.kom.modules.Modules;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.sshd.server.auth.AsyncAuthException;
import org.apache.sshd.server.auth.password.PasswordAuthenticator;
import org.apache.sshd.server.auth.password.PasswordChangeRequiredException;
import org.apache.sshd.server.session.ServerSession;

public class Authenticator implements PasswordAuthenticator {
  private static final Logger LOG = LogManager.getLogger(Authenticator.class);
  private final SSHD server;
  private final int allowedAttempts;
  private final int lockoutTime;

  public Authenticator(final SSHD server) {
    this.server = server;
    final Map<String, String> parameters = server.getParameters();
    allowedAttempts = Integer.parseInt(parameters.get("attempts"));
    lockoutTime = Integer.parseInt(parameters.get("lockout"));
  }

  @Override
  public boolean authenticate(
      final String username, final String password, final ServerSession session)
      throws PasswordChangeRequiredException, AsyncAuthException {
    final String host = session.getRemoteAddress().toString();
    ServerSessionFactory ssf = null;
    try {
      ssf = Modules.getModule("Backend", ServerSessionFactoryImpl.class);
    } catch (final NoSuchModuleException e) {
      Authenticator.LOG.fatal("Backend module not loaded", e);
      return false;
    }
    try {
      final String ticket = ssf.generateTicket(username, password);
      server.putTicket(session, ticket);
      ssf.notifySuccessfulLogin(host);
      return true;
    } catch (final AuthenticationException e) {
      // Failed login attempt
      ssf.notifyFailedLogin(host, allowedAttempts, lockoutTime);
      return false;
    } catch (final UnexpectedException e) {
      Authenticator.LOG.error("Exception while logging in", e);
    }
    return false;
  }

  @Override
  public boolean handleClientPasswordChangeRequest(
      final ServerSession session,
      final String username,
      final String oldPassword,
      final String newPassword) {
    // Not supported
    return false;
  }
}
