/*
 * Created on Oct 12, 2003
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.backend.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import nu.rydin.kom.backend.CacheManager;
import nu.rydin.kom.backend.KOMCache;
import nu.rydin.kom.constants.ConferencePermissions;
import nu.rydin.kom.exceptions.AlreadyMemberException;
import nu.rydin.kom.exceptions.AuthorizationException;
import nu.rydin.kom.exceptions.ObjectNotFoundException;
import nu.rydin.kom.structs.ConferenceInfo;
import nu.rydin.kom.structs.ConferencePermission;
import nu.rydin.kom.structs.MembershipInfo;
import nu.rydin.kom.structs.MessageRange;
import nu.rydin.kom.structs.MessageRangeList;
import nu.rydin.kom.structs.Name;
import nu.rydin.kom.structs.NameAssociation;
import nu.rydin.kom.structs.PermissionKey;

/**
 * @author Pontus Rydin
 * @author Jepson
 */
public class MembershipManager {
  private static final long PRIO_INCREMENT = 1;

  private final PreparedStatement m_listMbrForUserStmt;
  private final PreparedStatement m_listMbrForConfStmt;
  private final PreparedStatement m_loadMembershipStmt;
  private final PreparedStatement m_addMembershipStmt;
  private final PreparedStatement m_signoffStmt;
  private final PreparedStatement m_getLastPriorityStmt;
  private final PreparedStatement m_updateMarkersStmt;
  private final PreparedStatement m_updatePermissionsStmt;
  private final PreparedStatement m_getPermissionsStmt;
  private final PreparedStatement m_listPermissionsStmt;
  private final PreparedStatement m_activateMembershipStmt;
  private final PreparedStatement m_getDefaultPermissionsStmt;
  private final PreparedStatement m_getNonmemberPermissionsStmt;
  private final PreparedStatement m_updatePriorityStmt;
  private final PreparedStatement m_movePrioritiesUpStmt;
  private final PreparedStatement m_movePrioritiesDownStmt;
  private final PreparedStatement m_getReadMarkersForMessageStmt;

  public MembershipManager(final Connection conn) throws SQLException {
    m_listMbrForUserStmt =
        conn.prepareStatement(
            "SELECT user, conference, active, priority, flags, markers, permissions, negation_mask "
                + "FROM memberships WHERE user = ? AND active = 1 ORDER BY priority");
    m_listMbrForConfStmt =
        conn.prepareStatement(
            "SELECT user, conference, active, priority, flags, markers, permissions, negation_mask "
                + "FROM memberships WHERE conference = ? AND active = 1");
    m_loadMembershipStmt =
        conn.prepareStatement(
            "SELECT user, conference, active, priority, flags, markers, permissions, negation_mask "
                + "FROM memberships WHERE user = ? AND conference = ?");
    m_addMembershipStmt =
        conn.prepareStatement(
            "INSERT INTO memberships(user, conference, active, priority, flags, markers, permissions, negation_mask) "
                + "VALUES(?, ?, ?, ?, ?, NULL, ?, ?)");
    m_signoffStmt =
        conn.prepareStatement(
            "UPDATE memberships SET active = 0 WHERE user = ? AND conference = ?");
    m_getLastPriorityStmt =
        conn.prepareStatement("SELECT MAX(priority) FROM memberships WHERE user = ?");
    m_updateMarkersStmt =
        conn.prepareStatement(
            "UPDATE memberships SET markers = ? WHERE user = ? AND conference = ?");
    m_updatePermissionsStmt =
        conn.prepareStatement(
            "UPDATE memberships SET permissions = ?, negation_mask = ? WHERE user = ? AND conference = ?");
    m_getPermissionsStmt =
        conn.prepareStatement(
            "SELECT m.permissions, m.negation_mask, c.permissions "
                + "FROM conferences c, memberships m "
                + "WHERE m.user = ? AND c.id = ? AND m.conference = c.id");
    m_listPermissionsStmt =
        conn.prepareStatement(
            "SELECT m.user, n.fullname, n.visibility, m.permissions, m.negation_mask FROM memberships m, users u "
                + "WHERE m.user = n.id AND c.conference = ?");
    m_activateMembershipStmt =
        conn.prepareStatement(
            "UPDATE memberships SET active = 1 WHERE user = ? AND conference = ?");
    m_getDefaultPermissionsStmt =
        conn.prepareStatement("SELECT permissions FROM conferences WHERE id = ?");
    m_getNonmemberPermissionsStmt =
        conn.prepareStatement("SELECT nonmember_permissions FROM conferences WHERE id = ?");
    m_updatePriorityStmt =
        conn.prepareStatement(
            "UPDATE memberships SET priority = ? WHERE user = ? AND conference = ?");
    m_movePrioritiesUpStmt =
        conn.prepareStatement(
            "UPDATE memberships SET priority = priority - 1 WHERE user = ? AND priority <= ? AND priority > ?");
    m_movePrioritiesDownStmt =
        conn.prepareStatement(
            "UPDATE memberships SET priority = priority + 1 WHERE user = ? AND priority >= ? AND priority < ?");
    m_getReadMarkersForMessageStmt =
        conn.prepareStatement(
            "select memberships.user, memberships.conference, localnum, markers, fullname "
                + "from memberships join messageoccurrences on memberships.conference=messageoccurrences.conference "
                + "join names on names.id=memberships.user where message = ?");
  }

  public void close() throws SQLException {
    if (m_listMbrForUserStmt != null) {
		m_listMbrForUserStmt.close();
	}
    if (m_listMbrForConfStmt != null) {
		m_listMbrForConfStmt.close();
	}
    if (m_loadMembershipStmt != null) {
		m_loadMembershipStmt.close();
	}
    if (m_addMembershipStmt != null) {
		m_addMembershipStmt.close();
	}
    if (m_signoffStmt != null) {
		m_signoffStmt.close();
	}
    if (m_getLastPriorityStmt != null) {
		m_getLastPriorityStmt.close();
	}
    if (m_updateMarkersStmt != null) {
		m_updateMarkersStmt.close();
	}
    if (m_updatePermissionsStmt != null) {
		m_updatePermissionsStmt.close();
	}
    if (m_getPermissionsStmt != null) {
		m_getPermissionsStmt.close();
	}
    if (m_listPermissionsStmt != null) {
		m_listPermissionsStmt.close();
	}
    if (m_activateMembershipStmt != null) {
		m_activateMembershipStmt.close();
	}
    if (m_getDefaultPermissionsStmt != null) {
		m_getDefaultPermissionsStmt.close();
	}
    if (m_getNonmemberPermissionsStmt != null) {
		m_getNonmemberPermissionsStmt.close();
	}
    if (m_updatePriorityStmt != null) {
		m_updatePriorityStmt.close();
	}
    if (m_movePrioritiesUpStmt != null) {
		m_movePrioritiesUpStmt.close();
	}
    if (m_movePrioritiesDownStmt != null) {
		m_movePrioritiesDownStmt.close();
	}
    if (m_getReadMarkersForMessageStmt != null) {
		m_getReadMarkersForMessageStmt.close();
	}
  }

  @Override
  public void finalize() {
    try {
		close();
    } catch (final SQLException e) {
      // Not much we can do here...
      //
      e.printStackTrace();
    }
  }

  /**
   * Returns the memberships records for a user, i.e. lists the conferences that user is a member
   * of, in order of prioritization.
   *
   * @param user The user id
   * @throws SQLException
   */
  public MembershipInfo[] listMembershipsByUser(final long user) throws SQLException {
    m_listMbrForUserStmt.clearParameters();
    m_listMbrForUserStmt.setLong(1, user);
    try (final ResultSet rs = m_listMbrForUserStmt.executeQuery()) {
      return extractMemberships(rs);
    }
  }

  /**
   * Returns the memberships records for a conference, i.e. lists the members of that conference
   *
   * @param conference The conference id
   * @throws SQLException
   */
  public MembershipInfo[] listMembershipsByConference(final long conference) throws SQLException {
    m_listMbrForConfStmt.clearParameters();
    m_listMbrForConfStmt.setLong(1, conference);
    try (final ResultSet rs = m_listMbrForConfStmt.executeQuery()) {
      return extractMemberships(rs);
    }
  }

  /**
   * Returns a list of user permissions for a conference
   *
   * @param conf The id of the conference
   * @throws SQLException
   */
  public ConferencePermission[] listPermissions(final long conf) throws SQLException {
    m_listPermissionsStmt.clearParameters();
    m_listPermissionsStmt.setLong(1, conf);
    final List<ConferencePermission> list = new ArrayList<>();
    try (final ResultSet rs = m_listPermissionsStmt.executeQuery()) {
      while (rs.next()) {
        list.add(
            new ConferencePermission(
                new NameAssociation(
                    rs.getLong(1), // User id
                    new Name(
                        rs.getString(2), rs.getShort(3), NameManager.CONFERENCE_KIND)), // User name
                rs.getInt(4), // Permissions
                rs.getInt(5) // Negations
                ));
      }
      final ConferencePermission[] answer = new ConferencePermission[list.size()];
      list.toArray(answer);
      return answer;
    }
  }

  /**
   * Loads a membership record for a specified user and conference
   *
   * @param user
   * @param conference
   * @return
   * @throws ObjectNotFoundException
   * @throws SQLException
   */
  protected MembershipInfo loadMembership(final long user, final long conference)
      throws ObjectNotFoundException, SQLException {
    m_loadMembershipStmt.clearParameters();
    m_loadMembershipStmt.setLong(1, user);
    m_loadMembershipStmt.setLong(2, conference);
    try (final ResultSet rs = m_loadMembershipStmt.executeQuery()) {
      if (!rs.next()) {
		  throw new ObjectNotFoundException("Membership(user=" + user + "conf=" + conference + ")");
	  }
      return extractMembership(rs);
    }
  }

  /**
   * Signs up for a conference, i.e. adds a membership record linking a user to a conference.
   *
   * @param user
   * @param conference
   * @param flags
   * @throws ObjectNotFoundException
   * @throws SQLException
   */
  public void signup(final long user, final long conference, final long flags)
      throws ObjectNotFoundException, AlreadyMemberException, AuthorizationException, SQLException {
	  signup(user, conference, flags, 0, 0);
  }

  /**
   * Signs up for a conference, i.e. adds a membership record linking a user to a conference.
   *
   * @param user
   * @param conference
   * @param flags
   * @param permissions
   * @param negationMask
   * @throws ObjectNotFoundException
   * @throws SQLException
   */
  public void signup(final long user, final long conference, final long flags, final int permissions, final int negationMask)
      throws ObjectNotFoundException, AlreadyMemberException, AuthorizationException, SQLException {
    // Check if already member
    //
    try {
      final MembershipInfo mi = loadMembership(user, conference);

      // We may find a membership here for three reasons:
      // 1) The user is already a member
      // 2) The user has signed off, but an inactive (reusable) membership record exists
      // 3) The user has been granted rights to a restricted conference.
      //
      // Already member?
      //
      if (mi.isActive()) {
		  throw new AlreadyMemberException("user=" + user + " conference=" + conference);
	  }

      // Keep permissions.
      // TODO: Is this correct?
      //
      /*
      // Handle inactive membership
      // First, update permissions
      //
      this.updateConferencePermissions(user, conference, permissions, negationMask);
      */

      // Then, activate!
      //
      m_activateMembershipStmt.clearParameters();
      m_activateMembershipStmt.setLong(1, user);
      m_activateMembershipStmt.setLong(2, conference);
      m_activateMembershipStmt.executeUpdate();
    } catch (final ObjectNotFoundException e) {
      // Not member yet a member
      // This is ok, unless the conference is restricted (i.e. no default reaf rights)
      //
      m_getDefaultPermissionsStmt.clearParameters();
      m_getDefaultPermissionsStmt.setLong(1, conference);
      ResultSet rs = null;
      try {
        rs = m_getDefaultPermissionsStmt.executeQuery();
        if (!rs.next()) {
			throw new ObjectNotFoundException(); // Should not happen!
		}
        if (((rs.getInt(1) | permissions) & ConferencePermissions.READ_PERMISSION) == 0) {
			throw new AuthorizationException();
		}
      } finally {
        if (rs != null) {
			rs.close();
		}
      }

      // Get hold of last priority number
      //
      m_getLastPriorityStmt.clearParameters();
      m_getLastPriorityStmt.setLong(1, user);
      rs = null;
      try {
        long prio = 1;
        rs = m_getLastPriorityStmt.executeQuery();
        if (rs.next()) {
			prio = rs.getLong(1) + PRIO_INCREMENT;
		}

        // Now, add membership record
        //
        m_addMembershipStmt.clearParameters();
        m_addMembershipStmt.setLong(1, user);
        m_addMembershipStmt.setLong(2, conference);
        m_addMembershipStmt.setBoolean(3, true); // Active flag
        m_addMembershipStmt.setLong(4, prio);
        m_addMembershipStmt.setLong(5, flags);
        m_addMembershipStmt.setInt(6, permissions);
        m_addMembershipStmt.setInt(7, negationMask);
        m_addMembershipStmt.executeUpdate();
      } finally {
        if (rs != null) {
			rs.close();
		}
      }
    } finally {
      // Invalidate permission cache
      //
      CacheManager.instance()
          .getPermissionCache()
          .registerInvalidation(new PermissionKey(conference, user));
    }
  }

  public long prioritizeConference(final long user, final long conference, final long targetconference)
      throws ObjectNotFoundException, SQLException {
    final long oldprio = loadMembership(user, conference).getPriority();
    final long newprio = loadMembership(user, targetconference).getPriority();
    long result = 0;

    if (newprio == oldprio) {
      // Do nothing.
      return result;
    } else if (newprio < oldprio) {
      // Move everyone else in between down one step.
      //
      m_movePrioritiesDownStmt.clearParameters();
      m_movePrioritiesDownStmt.setLong(1, user);
      m_movePrioritiesDownStmt.setLong(2, newprio);
      m_movePrioritiesDownStmt.setLong(3, oldprio);
      result = m_movePrioritiesDownStmt.executeUpdate();
    } else {
      // Move everyone else in between up one step.
      //
      m_movePrioritiesUpStmt.clearParameters();
      m_movePrioritiesUpStmt.setLong(1, user);
      m_movePrioritiesUpStmt.setLong(2, newprio);
      m_movePrioritiesUpStmt.setLong(3, oldprio);
      result = -m_movePrioritiesUpStmt.executeUpdate();
    }

    // Then, move the target conference to it's new position.
    //
    m_updatePriorityStmt.clearParameters();
    m_updatePriorityStmt.setLong(1, newprio);
    m_updatePriorityStmt.setLong(2, user);
    m_updatePriorityStmt.setLong(3, conference);
    m_updatePriorityStmt.executeUpdate();

    return result;
  }

  public void updateMarkers(final long user, final long conference, final MessageRangeList markers)
      throws ObjectNotFoundException, SQLException {
    m_updateMarkersStmt.clearParameters();
    m_updateMarkersStmt.setString(1, MembershipInfo.encodeMessageRanges(markers));
    m_updateMarkersStmt.setLong(2, user);
    m_updateMarkersStmt.setLong(3, conference);
    if (m_updateMarkersStmt.executeUpdate() == 0) {
		throw new ObjectNotFoundException("Membership(user=" + user + "conf=" + conference + ")");
	}
  }

  public NameAssociation[] listReaders(final long message) throws SQLException {
    m_getReadMarkersForMessageStmt.clearParameters();
    m_getReadMarkersForMessageStmt.setLong(1, message);
    ResultSet rs = null;
    try {
      final Set<NameAssociation> s = new HashSet<>();
      rs = m_getReadMarkersForMessageStmt.executeQuery();
      while (rs.next()) {
        if (null != rs.getString(4)) {
          if (MembershipInfo.decodeMessageRanges(rs.getString(4)).includes(rs.getInt(3))) {
            // I should be taken out and shot for this, but until we move object kinds to their
            // own constants file, this is a stopgap measure.
            //
            s.add(new NameAssociation(rs.getLong(1), rs.getString(5), (short) 0));
          }
        }
      }
      final NameAssociation[] retval = new NameAssociation[s.size()];
      s.toArray(retval);
      return retval;
    } finally {
      if (null != rs) {
        rs.close();
      }
    }
  }

  /**
   * Signs off from a conference
   *
   * @param user The user to sign off
   * @param conference The conference to sign of from
   * @throws ObjectNotFoundException
   * @throws SQLException
   */
  public void signoff(final long user, final long conference) throws ObjectNotFoundException, SQLException {
    m_signoffStmt.clearParameters();
    m_signoffStmt.setLong(1, user);
    m_signoffStmt.setLong(2, conference);
    if (m_signoffStmt.executeUpdate() == 0) {
		throw new ObjectNotFoundException("Membership(user=" + user + "conf=" + conference + ")");
	}
  }

  /**
   * Returns <tt>true</tt> if the specified user is a member of the specified conference.
   *
   * @param user The user
   * @param conference The conference
   * @return
   */
  public boolean isMember(final long user, final long conference) throws SQLException {
    // TODO: This can be speeded up (but I'm not sure we'll need to)
    //
    try {
      final MembershipInfo mi = loadMembership(user, conference);
      return mi.isActive();
    } catch (final ObjectNotFoundException e) {
      return false;
    }
  }

  /**
   * Returns the number of unread messages a user has in a conference
   *
   * @param user The user
   * @param conference The conference
   * @return
   */
  public int countUnread(final long user, final ConferenceInfo conference)
      throws ObjectNotFoundException, SQLException {
    return countUnread(loadMembership(user, conference.getId()), conference);
  }

  /**
   * Returns the number of unread messages a user has in a conference based on a membership record
   *
   * @param membership The membership record
   * @param conference The conference
   * @return
   */
  public int countUnread(final MembershipInfo membership, final ConferenceInfo conference) {
    final MessageRange total =
        new MessageRange(conference.getFirstMessage(), conference.getLastMessage());
    return total.countOverlapping(membership.getReadMessages());
  }

  /**
   * Grants or removes conference permissions for a user
   *
   * @param user The is of the user
   * @param conference The id of the conference
   * @param permissions The permission bitmap
   * @param negations The negations mask
   * @throws SQLException
   */
  public void updateConferencePermissions(
		  final long user, final long conference, final int permissions, final int negations) throws SQLException {
    // Assume we're already members (or at least have a membership record)
    //
    try {
      @SuppressWarnings("unused") final MembershipInfo mi = loadMembership(user, conference);
      m_updatePermissionsStmt.clearParameters();
      m_updatePermissionsStmt.setInt(1, permissions);
      m_updatePermissionsStmt.setInt(2, negations);
      m_updatePermissionsStmt.setLong(3, user);
      m_updatePermissionsStmt.setLong(4, conference);
      m_updatePermissionsStmt.executeUpdate();
    } catch (final ObjectNotFoundException e) {
      // Not members. We need to create a new membership record, but with the
      // "active" flag cleared. This means that we're not members of the conference,
      // but we have some rights in it. Useful for example when replies are sent
      // to a conference that the user is not a member of.
      //
      ResultSet rs = null;
      try {
        // Get hold of last priority number
        //
        long prio = 1;
        m_getLastPriorityStmt.clearParameters();
        m_getLastPriorityStmt.setLong(1, user);
        rs = m_getLastPriorityStmt.executeQuery();
        if (rs.next()) {
			prio = rs.getLong(1) + PRIO_INCREMENT;
		}

        // Now, add membership record
        //
        m_addMembershipStmt.clearParameters();
        m_addMembershipStmt.setLong(1, user);
        m_addMembershipStmt.setLong(2, conference);
        m_addMembershipStmt.setBoolean(3, false); // Active flag
        m_addMembershipStmt.setLong(4, prio);
        m_addMembershipStmt.setLong(5, 0);
        m_addMembershipStmt.setInt(6, permissions);
        m_addMembershipStmt.setInt(7, negations);
        m_addMembershipStmt.executeUpdate();
      } finally {
        if (rs != null) {
			rs.close();
		}
      }
    } finally {
      // Invalidate cache
      //
      CacheManager.instance()
          .getPermissionCache()
          .registerInvalidation(new PermissionKey(conference, user));
    }
  }

  /**
   * Returns the permission bitmap for a user in a conference. Returns 0 id the user didn't have any
   * permissions in that conference.
   *
   * @param conference The id of the conference
   * @param user The id of the user
   * @throws SQLException
   */
  public int getPermissions(final long user, final long conference)
      throws ObjectNotFoundException, SQLException {
    // First try cache
    //
    final PermissionKey pKey = new PermissionKey(conference, user);
    final KOMCache pCache = CacheManager.instance().getPermissionCache();
    final Integer iPerm = (Integer) pCache.get(pKey);
    if (iPerm != null) {
		return iPerm;
	}

    // Not found in cache, check database
    //
    m_getPermissionsStmt.clearParameters();
    m_getPermissionsStmt.setLong(1, user);
    m_getPermissionsStmt.setLong(2, conference);
    ResultSet rs = null;
    try {
      rs = m_getPermissionsStmt.executeQuery();
      if (!rs.next()) {
        // Not a member. Check for nonmember permissions
        //
        rs.close();
        rs = null;
        m_getNonmemberPermissionsStmt.clearParameters();
        m_getNonmemberPermissionsStmt.setLong(1, conference);
        rs = m_getNonmemberPermissionsStmt.executeQuery();
        if (!rs.next()) {
			throw new ObjectNotFoundException("conf id=" + conference);
		}
        final int perm = rs.getInt(1);

        // Update cache and return
        //
        pCache.deferredPut(pKey, new Integer(perm));
        return perm;
      }

      // Get user permission, negation mask and conference permissions
      //
      final int u = rs.getInt(1);
      final int m = rs.getInt(2);
      final int c = rs.getInt(3);

      // A permission should be granted if the conference permission
      // or set user permission is granted, unless the negation bit is
      // set.
      //
      final int perm = ((u | c) & ~m);
      pCache.deferredPut(pKey, new Integer(perm));
      return perm;
    } finally {
      if (rs != null) {
		  rs.close();
	  }
    }
  }

  /**
   * Returns true if a user has the specified set of permissions.
   *
   * @param conference The id of the conference
   * @param user The id of the user
   * @param mask The permission mask. Logically ANDed with stored permissions
   * @throws SQLException
   */
  public boolean hasPermission(final long user, final long conference, final int mask)
      throws ObjectNotFoundException, SQLException {
    return (getPermissions(user, conference) & mask) == mask;
  }

  protected MembershipInfo[] extractMemberships(final ResultSet rs) throws SQLException {
    final List<MembershipInfo> list = new ArrayList<MembershipInfo>();
    while (rs.next()) {
      list.add(extractMembership(rs));
    }
    final MembershipInfo[] answer = new MembershipInfo[list.size()];
    list.toArray(answer);
    return answer;
  }

  protected MembershipInfo extractMembership(final ResultSet rs) throws SQLException {
    return new MembershipInfo(
        rs.getLong(1), // user
        rs.getLong(2), // conference
        rs.getBoolean(3), // active
        rs.getLong(4), // priority
        rs.getLong(5), // flags
        rs.getString(6), // markers
        rs.getInt(7), // permissions
        rs.getInt(8) // negation_mask
        );
  }

  /**
   * Reprioritizes the conference for the user.
   *
   * @param newprio New priority (starting at zero)
   * @param user User for whom to reprioritize conference
   * @param conference Conference ID
   * @throws SQLException
   */
  public void updatePriority(final long newprio, final long user, final long conference) throws SQLException {
    final ResultSet rs = null;
    try {
      m_updatePriorityStmt.clearParameters();
      m_updatePriorityStmt.setLong(1, newprio);
      m_updatePriorityStmt.setLong(2, user);
      m_updatePriorityStmt.setLong(3, conference);
      m_updatePriorityStmt.executeUpdate();
    } finally {
      if (rs != null) {
		  rs.close();
	  }
    }
  }
}
