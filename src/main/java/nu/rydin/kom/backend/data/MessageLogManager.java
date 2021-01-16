/*
 * Created on Jul 12, 2004
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.backend.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import nu.rydin.kom.constants.MessageLogKinds;
import nu.rydin.kom.exceptions.UnexpectedException;
import nu.rydin.kom.structs.MessageLogItem;
import nu.rydin.kom.structs.Name;
import nu.rydin.kom.structs.NameAssociation;

/** @author Pontus Rydin */
public class MessageLogManager {
  private final PreparedStatement m_storeMessageStmt;
  private final PreparedStatement m_storeMessagePointerStmt;
  private final PreparedStatement m_listMessagesStmt;
  private final PreparedStatement m_listRecipientsStmt;

  public MessageLogManager(final Connection conn) throws SQLException {
    m_storeMessageStmt =
        conn.prepareStatement(
            "INSERT INTO messagelog(body, created, author, author_name) VALUES(?, ?, ?, ?)",
            Statement.RETURN_GENERATED_KEYS);
    m_storeMessagePointerStmt =
        conn.prepareStatement(
            "INSERT INTO messagelogpointers(recipient, logid, sent, kind) VALUES(?, ?, ?, ?)");
    m_listMessagesStmt =
        conn.prepareStatement(
            "SELECT ml.id, mlp.kind, ml.author, ml.author_name, ml.created, mlp.sent, ml.body "
                + "FROM messagelog ml, messagelogpointers mlp WHERE mlp.logid = ml.id AND "
                + "mlp.recipient = ? AND mlp.kind >= ? AND mlp.kind <= ? ORDER BY mlp.logid DESC LIMIT ? OFFSET 0");
    m_listRecipientsStmt =
        conn.prepareStatement(
            "SELECT mlp.recipient, n.fullname, n.visibility FROM messagelogpointers mlp, names n "
                + "WHERE n.id = mlp.recipient AND mlp.logid = ? AND mlp.sent = 0");
  }

  public void close() throws SQLException {
    if (m_storeMessageStmt != null) {
      m_storeMessageStmt.close();
    }
    if (m_storeMessagePointerStmt != null) {
      m_storeMessagePointerStmt.close();
    }
    if (m_listMessagesStmt != null) {
      m_listMessagesStmt.close();
    }
    if (m_listRecipientsStmt != null) {
      m_listRecipientsStmt.close();
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
   * Stores a message log item.
   *
   * @param author The id of the author
   * @param authorName The name of the author
   * @param body Message body
   * @return The log id
   * @throws SQLException
   */
  public long storeMessage(final long author, final String authorName, final String body)
      throws SQLException, UnexpectedException {
    m_storeMessageStmt.clearParameters();
    m_storeMessageStmt.setString(1, body);
    m_storeMessageStmt.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
    m_storeMessageStmt.setLong(3, author);
    m_storeMessageStmt.setString(4, authorName);
    m_storeMessageStmt.executeUpdate();
    try (final ResultSet rs = m_storeMessageStmt.getGeneratedKeys()) {
      if (!rs.next()) {
        throw new UnexpectedException(-1, "Unable to get message log id");
      }
      return rs.getLong(1);
    }
  }

  /**
   * Stores a message log pointer, i.e. a link between a recipient and a message.
   *
   * @param logid The is of the message
   * @param recipient The id of the recipient
   * @param sent True if this is a copy of a sent message
   * @param kind The kind of message, e.g. private chat message or broadcast message.
   * @throws SQLException
   */
  public void storeMessagePointer(
      final long logid, final long recipient, final boolean sent, final short kind)
      throws SQLException {
    m_storeMessagePointerStmt.clearParameters();
    m_storeMessagePointerStmt.setLong(1, recipient);
    m_storeMessagePointerStmt.setLong(2, logid);
    m_storeMessagePointerStmt.setBoolean(3, sent);
    m_storeMessagePointerStmt.setShort(4, kind);
    m_storeMessagePointerStmt.executeUpdate();
  }

  public MessageLogItem[] listChatMessages(final long user, final int limit) throws SQLException {
    return getMessages(user, limit, MessageLogKinds.CHAT, MessageLogKinds.CHAT);
  }

  public MessageLogItem[] listBroadcastMessages(final long user, final int limit)
      throws SQLException {
    return getMessages(user, limit, MessageLogKinds.BROADCAST, MessageLogKinds.CONDENSED_BROADCAST);
  }

  public MessageLogItem[] listMulticastMessages(final long user, final int limit)
      throws SQLException {
    return getMessages(user, limit, MessageLogKinds.MULTICAST, MessageLogKinds.MULTICAST);
  }

  public NameAssociation[] listRecipients(final long logid) throws SQLException {
    m_listRecipientsStmt.clearParameters();
    m_listRecipientsStmt.setLong(1, logid);
    try (final ResultSet rs = m_listRecipientsStmt.executeQuery()) {
      final ArrayList<NameAssociation> list = new ArrayList<>();
      while (rs.next()) {
        list.add(
            new NameAssociation(
                rs.getLong(1), // Id
                new Name(rs.getString(2), rs.getShort(3), NameManager.CONFERENCE_KIND))); // Name
      }
      final NameAssociation[] answer = new NameAssociation[list.size()];
      list.toArray(answer);
      return answer;
    }
  }

  private MessageLogItem[] getMessages(
      final long user, final int limit, final short lowKind, final short highKind)
      throws SQLException {
    m_listMessagesStmt.clearParameters();
    m_listMessagesStmt.setLong(1, user);
    m_listMessagesStmt.setShort(2, lowKind);
    m_listMessagesStmt.setShort(3, highKind);
    m_listMessagesStmt.setInt(4, limit);
    ResultSet rs = null;
    try {
      final ArrayList<MessageLogItem> list = new ArrayList<>(limit);
      rs = m_listMessagesStmt.executeQuery();
      while (rs.next()) {
        list.add(
            new MessageLogItem(
                rs.getShort(2), // Kind
                rs.getLong(3), // Author
                rs.getString(4), // Author name
                rs.getTimestamp(5), // Created
                rs.getBoolean(6), // Sent
                rs.getString(7), // Body
                listRecipients(rs.getLong(1)))); // Recipients
      }
      final MessageLogItem[] answer = new MessageLogItem[list.size()];
      list.toArray(answer);
      return answer;
    } finally {
      if (rs != null) {
        rs.close();
      }
    }
  }
}
