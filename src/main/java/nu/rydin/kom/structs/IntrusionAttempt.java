package nu.rydin.kom.structs;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** @author Jepson */
public class IntrusionAttempt {
  private static final Logger LOG = LogManager.getLogger(IntrusionAttempt.class);
  private final String host;
  private final long firstAttempt;
  private final int limit;
  private final long lockout;
  private long lastAttempt;
  private int count;
  private boolean isBlocked;

  public IntrusionAttempt(final String host, final int limit, final long lockout) {
    this.host = host;
    this.limit = limit;
    this.lockout = lockout;
    isBlocked = false;
    firstAttempt = System.currentTimeMillis();
    lastAttempt = firstAttempt;
    addAttempt();
  }

  public long getLockout() {
    return lockout;
  }

  public String getHost() {
    return host;
  }

  public long getFirstAttempt() {
    return firstAttempt;
  }

  public long getLastAttempt() {
    return lastAttempt;
  }

  public boolean isBlocked() {
    return isBlocked;
  }

  public void addAttempt() {
    ++count;
    LOG.debug("Login failed for host: " + host + ". Number of attempts: " + count);
    lastAttempt = System.currentTimeMillis();
    if (limit <= count) {
      isBlocked = true;
      LOG.info("Blacklisted host: " + host + " for " + (lockout / 1000) + " seconds");
    }
  }

  public int expireAttempt() {
    return --count;
  }

  // debug method
  //
  public int getCurrentCount() {
    return count;
  }
}
