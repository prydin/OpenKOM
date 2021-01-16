/*
 * Created on Sep 10, 2004
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.structs;

import java.io.Serializable;

/** @author Pontus Rydin */
public class SystemInformation implements Serializable {
  static final long serialVersionUID = 2005;

  private final boolean allowLogins;

  private final CacheInformation nameCache;

  private final CacheInformation userCache;

  private final CacheInformation conferenceCache;

  private final CacheInformation permissionCache;

  private final long numConferences;

  private final long numUsers;

  private final long numMessages;

  public SystemInformation(
          final boolean allowLogins,
          final CacheInformation nameCache,
          final CacheInformation userCache,
          final CacheInformation conferenceCache,
          final CacheInformation permissionCache,
          final long numUser,
          final long numConferences,
          final long numMessages) {
    this.allowLogins = allowLogins;
    this.nameCache = nameCache;
    this.userCache = userCache;
    this.conferenceCache = conferenceCache;
    this.permissionCache = permissionCache;
      numUsers = numUser;
    this.numConferences = numConferences;
    this.numMessages = numMessages;
  }

  public boolean isLoginAllowed() {
    return allowLogins;
  }

  public CacheInformation getConferenceCache() {
    return conferenceCache;
  }

  public CacheInformation getPermissionCache() {
    return permissionCache;
  }

  public CacheInformation getNameCache() {
    return nameCache;
  }

  public CacheInformation getUserCache() {
    return userCache;
  }

  public long getNumConferences() {
    return numConferences;
  }

  public long getNumMessages() {
    return numMessages;
  }

  public long getNumUsers() {
    return numUsers;
  }
}
