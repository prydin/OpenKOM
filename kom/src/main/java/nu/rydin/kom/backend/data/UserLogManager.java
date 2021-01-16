/*
 * Created on Aug 24, 2004
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.backend.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import nu.rydin.kom.structs.UserLogItem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** @author Pontus Rydin */
public class UserLogManager {
  private static final Logger LOG = LogManager.getLogger(UserLogManager.class);
  private final PreparedStatement m_storeStmt;

  private final PreparedStatement m_getByDateStmt;

  private final PreparedStatement m_getByUserStmt;

  public UserLogManager(final Connection conn) throws SQLException {
    m_storeStmt =
        conn.prepareStatement(
            "INSERT INTO userlog(user, logged_in, logged_out, num_posted, num_read, num_chat_messages, num_broadcasts, "
                + "num_copies) VALUES(?, ?, ?, ?, ?, ?, ?, ?)");
    m_getByDateStmt =
        conn.prepareStatement(
            "SELECT l.user, n.fullname, l.logged_in, l.logged_out, l.num_posted, l.num_read, "
                + "l.num_chat_messages, l.num_broadcasts, l.num_copies "
                + "FROM userlog l, names n "
                + "WHERE l.user = n.id AND l.logged_in > ? AND l.logged_in < ? "
                + "ORDER BY l.logged_in DESC LIMIT ? OFFSET ?");
    m_getByUserStmt =
        conn.prepareStatement(
            "SELECT l.user, n.fullname, l.logged_in, l.logged_out, l.num_posted, l.num_read, "
                + "l.num_chat_messages, l.num_broadcasts, l.num_copies "
                + "FROM userlog l, names n "
                + "WHERE l.user = n.id AND l.user = ? AND l.logged_in > ? AND l.logged_in < ? "
                + "ORDER BY l.logged_in DESC LIMIT ? OFFSET ?");
  }

  public void close() throws SQLException {
    if (m_storeStmt != null) {
      m_storeStmt.close();
    }
    if (m_getByDateStmt != null) {
      m_getByDateStmt.close();
    }
    if (m_getByUserStmt != null) {
      m_getByUserStmt.close();
    }
  }

  @Override
  public void finalize() {
    try {
      close();
    } catch (final SQLException e) {
      LOG.error("Exception in finalizer", e);
    }
  }

  public void store(final UserLogItem ul) throws SQLException {
    m_storeStmt.clearParameters();
    m_storeStmt.setLong(1, ul.getUserId());
    m_storeStmt.setTimestamp(2, ul.getLoggedIn());
    m_storeStmt.setTimestamp(3, ul.getLoggedOut());
    m_storeStmt.setInt(4, ul.getNumPosted());
    m_storeStmt.setInt(5, ul.getNumRead());
    m_storeStmt.setInt(6, ul.getNumChats());
    m_storeStmt.setInt(7, ul.getNumBroadcasts());
    m_storeStmt.setInt(8, ul.getNumCopies());
    m_storeStmt.executeUpdate();
  }

  public UserLogItem[] getByDate(
      final Timestamp start, final Timestamp end, final int offset, final int limit)
      throws SQLException {
    m_getByDateStmt.clearParameters();
    m_getByDateStmt.setTimestamp(1, start);
    m_getByDateStmt.setTimestamp(2, end);
    m_getByDateStmt.setInt(3, limit);
    m_getByDateStmt.setInt(4, offset);
    final ArrayList<UserLogItem> list = new ArrayList<>(limit);
    try (final ResultSet rs = m_getByDateStmt.executeQuery()) {
      while (rs.next()) {
        list.add(extractLogItem(rs));
      }
      final UserLogItem[] answer = new UserLogItem[list.size()];
      list.toArray(answer);
      return answer;
    }
  }

  public UserLogItem[] getByUser(
      final long userId,
      final Timestamp start,
      final Timestamp end,
      final int offset,
      final int limit)
      throws SQLException {
    m_getByUserStmt.clearParameters();
    m_getByUserStmt.setLong(1, userId);
    m_getByUserStmt.setTimestamp(2, start);
    m_getByUserStmt.setTimestamp(3, end);
    m_getByUserStmt.setInt(4, limit);
    m_getByUserStmt.setInt(5, offset);
    final ArrayList<UserLogItem> list = new ArrayList<>(limit);
    try (final ResultSet rs = m_getByUserStmt.executeQuery()) {
      while (rs.next()) {
        list.add(extractLogItem(rs));
      }
      final UserLogItem[] answer = new UserLogItem[list.size()];
      list.toArray(answer);
      return answer;
    }
  }

  private UserLogItem extractLogItem(final ResultSet rs) throws SQLException {
    return new UserLogItem(
        rs.getLong(1), // Userid
        rs.getString(2), // User name
        rs.getTimestamp(3), // login
        rs.getTimestamp(4), // logout
        rs.getInt(5), // num posted
        rs.getInt(6), // num read
        rs.getInt(7), // num chats
        rs.getInt(8), // num broadcasts
        rs.getInt(9)); // num copies
  }
}
