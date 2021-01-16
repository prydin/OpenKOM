/*
 * Created on Dec 4, 2005
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.backend;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nu.rydin.kom.backend.data.MembershipManager;
import nu.rydin.kom.backend.data.RelationshipManager;
import nu.rydin.kom.exceptions.UnexpectedException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Hands out user contexts. Only one context is kept per user, even though it may be used from
 * several different sessions.
 *
 * @author Pontus Rydin
 */
public class UserContextFactory {
  private static final Logger LOG = LogManager.getLogger(UserContextFactory.class);

  private static final UserContextFactory s_instance = new UserContextFactory();
  private final Map<Long, UserContextWrapper> contexts = new HashMap<>();

  public static UserContextFactory getInstance() {
    return UserContextFactory.s_instance;
  }

  public synchronized UserContext getOrCreateContext(
      final long user, final MembershipManager mm, final RelationshipManager rm)
      throws UnexpectedException {
    UserContextWrapper ucw = contexts.get(user);
    if (ucw == null) {
      LOG.info("Context not found for user " + user + ". Creating new.");
      ucw = new UserContextWrapper(new UserContext(user, mm, rm));
      contexts.put(user, ucw);
    }
    final int refCount = ucw.grab();
    LOG.info("Grabbing context. Refcount for user " + user + " is now " + refCount);
    return ucw.context;
  }

  public synchronized UserContext getContextOrNull(final long user) {
    final UserContextWrapper cw = contexts.get(user);
    return cw != null ? cw.context : null;
  }

  public synchronized void release(final long user) {
    final UserContextWrapper ucw = contexts.get(user);
    final int refCount = ucw.release();
    if (refCount == 0) {
      LOG.info("Context refcount reached zero for user " + user + ". Destroying context.");
      contexts.remove(user);
    } else {
      LOG.info("Releasing context. Refcount for user " + user + " is now " + refCount);
    }
  }

  public synchronized List<UserContext> listContexts() {
    final List<UserContext> answer = new ArrayList<>(contexts.size());
    for (final UserContextWrapper context : contexts.values()) {
      answer.add(context.context);
    }
    return answer;
  }

  private static class UserContextWrapper {
    private final UserContext context;
    private int refCount = 0;

    public UserContextWrapper(final UserContext context) {
      this.context = context;
    }

    public int grab() {
      return ++refCount;
    }

    public int release() {
      return --refCount;
    }
  }
}
