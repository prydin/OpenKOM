/*
 * Created on Dec 4, 2005
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.backend;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import nu.rydin.kom.backend.data.MembershipManager;
import nu.rydin.kom.backend.data.RelationshipManager;
import nu.rydin.kom.backend.data.UserManager;
import nu.rydin.kom.constants.FilterFlags;
import nu.rydin.kom.constants.RelationshipKinds;
import nu.rydin.kom.constants.UserFlags;
import nu.rydin.kom.exceptions.ObjectNotFoundException;
import nu.rydin.kom.exceptions.UnexpectedException;
import nu.rydin.kom.structs.Relationship;
import nu.rydin.kom.structs.UserInfo;

/**
 * User context shared across sessions.
 *
 * @author Pontus Rydin
 */
public class UserContext {
  private final long user;
  private MembershipList memberships;
  private Map<Long, Long> filterCache;

  public UserContext(final long user, final MembershipManager mm, final RelationshipManager rm)
      throws UnexpectedException {
    this.user = user;
    loadMemberships(mm);
    loadFilters(rm);
  }

  public long getUserId() {
    return user;
  }

  public Map<Long, Long> getFilterCache() {
    return filterCache;
  }

  public MembershipList getMemberships() {
    return memberships;
  }

  public synchronized void loadMemberships(final MembershipManager mm) throws UnexpectedException {
    try {
      memberships = new MembershipList(mm.listMembershipsByUser(user));
    } catch (final SQLException e) {
      throw new UnexpectedException(user, e);
    }
  }

  public synchronized void loadFilters(final RelationshipManager rm) throws UnexpectedException {
    try {
      filterCache = new HashMap<>();
      final Relationship[] rels = rm.listByRefererAndKind(user, RelationshipKinds.FILTER);
      for (final Relationship jinge : rels) {
        filterCache.put(jinge.getReferee(), jinge.getFlags());
      }
    } catch (final SQLException e) {
      throw new UnexpectedException(user, e);
    }
  }

  public synchronized void saveMemberships(final MembershipManager mm) throws UnexpectedException {
    try {
      memberships.save(user, mm);
    } catch (final SQLException e) {
      throw new UnexpectedException(user, e);
    }
  }

  public boolean allowsChat(final UserManager um, final long sender)
      throws ObjectNotFoundException, UnexpectedException {
    try {
      final UserInfo ui = um.loadUser(user);
      return ui.testFlags(0, UserFlags.ALLOW_CHAT_MESSAGES)
          && !userMatchesFilter(sender, FilterFlags.CHAT);
    } catch (final SQLException e) {
      throw new UnexpectedException(user, e);
    }
  }

  public boolean allowsBroadcast(final UserManager um, final long sender)
      throws ObjectNotFoundException, UnexpectedException {
    try {
      final UserInfo ui = um.loadUser(user);
      return ui.testFlags(0, UserFlags.ALLOW_BROADCAST_MESSAGES)
          && !userMatchesFilter(sender, FilterFlags.BROADCASTS);
    } catch (final SQLException e) {
      throw new UnexpectedException(user, e);
    }
  }

  protected boolean userMatchesFilter(final long user, final long neededFlags) {
    final Long flags = filterCache.get(user);
    if (flags == null) {
      return false;
    }
    return (flags & neededFlags) == neededFlags;
  }

  protected void reloadMemberships(final MembershipManager mm) throws SQLException {
    // Load membership infos into cache
    //
    if (memberships != null) {
      memberships.save(user, mm);
    }
    memberships = new MembershipList(mm.listMembershipsByUser(user));
  }
}
