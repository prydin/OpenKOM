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
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import nu.rydin.kom.backend.CacheManager;
import nu.rydin.kom.backend.SQLUtils;
import nu.rydin.kom.constants.MessageAttributes;
import nu.rydin.kom.constants.Visibilities;
import nu.rydin.kom.exceptions.MessageNotFoundException;
import nu.rydin.kom.exceptions.SelectionOverflowException;
import nu.rydin.kom.exceptions.UnexpectedException;
import nu.rydin.kom.structs.Bookmark;
import nu.rydin.kom.structs.GlobalMessageSearchResult;
import nu.rydin.kom.structs.LocalMessageSearchResult;
import nu.rydin.kom.structs.Message;
import nu.rydin.kom.structs.MessageAttribute;
import nu.rydin.kom.structs.MessageHeader;
import nu.rydin.kom.structs.MessageOccurrence;
import nu.rydin.kom.structs.Name;
import nu.rydin.kom.structs.NameAssociation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author Pontus Rydin
 * @author Jepson
 * @author Henrik Schr�der
 */
public class MessageManager {
  public static final short ACTION_CREATED = 0;
  public static final short ACTION_COPIED = 1;
  public static final short ACTION_MOVED = 2;
  private static final Logger LOG = LogManager.getLogger(MessageManager.class);
  private final PreparedStatement m_loadMessageStmt;
  private final PreparedStatement m_loadMessageInConfStmt;
  private final PreparedStatement m_loadMessageHeaderStmt;
  private final PreparedStatement m_loadMessageOccurrenceStmt;
  private final PreparedStatement m_getNextNumStmt;
  private final PreparedStatement m_addMessageStmt;
  private final PreparedStatement m_addMessageSearchStmt;
  private final PreparedStatement m_addMessageOccurrenceStmt;
  private final PreparedStatement m_listOccurrencesStmt;
  private final PreparedStatement m_getFirstOccurrenceStmt;
  private final PreparedStatement m_getFirstOccurrenceInMyConfsStmt;
  private final PreparedStatement m_getOccurrenceInConferenceStmt;
  private final PreparedStatement m_getGlobalIdStmt;
  private final PreparedStatement m_getRepliesStmt;
  private final PreparedStatement m_getReplyIdsStmt;
  private final PreparedStatement m_loadOccurrenceStmt;
  private final PreparedStatement m_getVisibleOccurrencesStmt;
  private final PreparedStatement m_getMessageAttributesStmt;
  private final PreparedStatement m_getMatchingMessageAttributesStmt;
  private final PreparedStatement m_addMessageAttributeStmt;
  private final PreparedStatement m_dropMessageAttributeStmt;
  private final PreparedStatement m_dropMessageOccurrenceStmt;
  private final PreparedStatement m_countMessageOccurrencesStmt;
  private final PreparedStatement m_dropMessageStmt;
  private final PreparedStatement m_dropMessageSearchStmt;
  private final PreparedStatement m_getLocalIdsInConfStmt;
  private final PreparedStatement m_dropConferenceStmt;
  private final PreparedStatement m_getGlobalBySubjectStmt;
  private final PreparedStatement m_getLocalBySubjectStmt;
  private final PreparedStatement m_findLastOccurrenceInConferenceWithAttrStmt;
  private final PreparedStatement m_getLatestMagicMessageStmt;
  private final PreparedStatement m_updateConferenceLasttext;
  private final PreparedStatement m_listAllMessagesLocally;
  private final PreparedStatement m_listMessagesLocallyByAuthor;
  private final PreparedStatement m_listMessagesGloballyByAuthor;
  private final PreparedStatement m_searchMessagesGlobally;
  private final PreparedStatement m_searchMessagesLocally;
  private final PreparedStatement m_grepMessagesLocally;
  private final PreparedStatement m_countMessagesLocallyByAuthor;
  private final PreparedStatement m_countMessagesGloballyByAuthor;
  private final PreparedStatement m_countSearchMessagesGlobally;
  private final PreparedStatement m_countSearchMessagesLocally;
  private final PreparedStatement m_countGrepMessagesLocally;
  private final PreparedStatement m_countStmt;
  private final PreparedStatement m_setThreadIdStmt;
  private final PreparedStatement m_selectByThreadStmt;
  private final PreparedStatement m_countAllMessagesLocally;
  private final PreparedStatement m_listCommentsGloballyToAuthor;
  private final PreparedStatement m_countCommentsGloballyToAuthor;
  private final PreparedStatement m_addBookmarkStmt;
  private final PreparedStatement m_deleteBookmarkStmt;
  private final PreparedStatement m_listBookmarksStmt;
  private final PreparedStatement m_getThreadIdForMessageStmt;
  private final PreparedStatement m_getMessagesByThreadIdAndStartStmt;

  public MessageManager(final Connection conn) throws SQLException {
    m_loadMessageStmt =
        conn.prepareStatement(
            "SELECT created, author, author_name, reply_to, thread, subject, body FROM messages WHERE id = ?");
    m_loadMessageInConfStmt =
        conn.prepareStatement(
            "SELECT m.id, m.created, m.author, m.author_name, m.reply_to, m.thread, m.subject, m.body "
                + "FROM messages m, messageoccurrences mo "
                + "WHERE m.id = mo.message AND mo.conference = ? AND mo.localnum = ?");
    m_loadMessageHeaderStmt =
        conn.prepareStatement(
            "SELECT created, author, author_name, reply_to, thread, subject FROM messages WHERE id = ?");
    m_loadMessageOccurrenceStmt =
        conn.prepareStatement(
            "SELECT message, action_ts, kind, user, user_name FROM messages "
                + "WHERE conference = ? AND localNum = ?");
    m_getNextNumStmt =
        conn.prepareStatement(
            "SELECT MAX(localnum) FROM messageoccurrences WHERE conference = ? FOR UPDATE");
    m_addMessageStmt =
        conn.prepareStatement(
            "INSERT INTO messages(created, author, author_name, reply_to, subject, body) "
                + "VALUES(?, ?, ?, ?, ?, ?)",
            Statement.RETURN_GENERATED_KEYS);
    m_addMessageSearchStmt =
        conn.prepareStatement("REPLACE INTO messagesearch(id, subject, body) " + "VALUES(?, ?, ?)");
    m_addMessageOccurrenceStmt =
        conn.prepareStatement(
            "INSERT INTO messageoccurrences(message, action_ts, kind, user, user_name, conference, localnum) "
                + "VALUES(?, ?, ?, ?, ?, ?, ?)");
    m_updateConferenceLasttext =
        conn.prepareStatement("UPDATE conferences SET lasttext=? WHERE id=?");
    m_listOccurrencesStmt =
        conn.prepareStatement(
            "SELECT message, action_ts, kind, user, user_name, conference, localnum FROM messageoccurrences "
                + "WHERE message = ? AND kind <> 3 ORDER BY action_ts");
    m_getOccurrenceInConferenceStmt =
        conn.prepareStatement(
            "SELECT message, action_ts, kind, user, user_name, conference, localnum FROM messageoccurrences "
                + "WHERE conference = ? AND message = ? AND kind <> 3");
    m_getFirstOccurrenceInMyConfsStmt =
        conn.prepareStatement(
            "SELECT mo.message, mo.action_ts, mo.kind, mo.user, mo.user_name, mo.conference, mo.localnum FROM messageoccurrences mo, memberships mbr "
                + "WHERE mbr.conference = mo.conference AND mbr.user = ? AND mo.message = ? AND mo.kind <> 3");
    m_getFirstOccurrenceStmt =
        conn.prepareStatement(
            "SELECT message, action_ts, kind, user, user_name, conference, localnum FROM messageoccurrences "
                + "WHERE message = ? ORDER BY action_ts LIMIT 1");
    m_getGlobalIdStmt =
        conn.prepareStatement(
            "SELECT message FROM messageoccurrences WHERE conference = ? AND localnum = ?");
    m_getRepliesStmt =
        conn.prepareStatement(
            "SELECT id, created, author, author_name, reply_to, thread, subject FROM messages "
                + "WHERE reply_to = ?");
    m_getReplyIdsStmt = conn.prepareStatement("SELECT id FROM messages WHERE reply_to = ?");
    m_loadOccurrenceStmt =
        conn.prepareStatement(
            "SELECT message, action_ts, kind, user, conference, localnum FROM messageoccurrences "
                + "WHERE conference = ? AND localnum = ?");
    m_getVisibleOccurrencesStmt =
        conn.prepareStatement(
            "SELECT o.message, o.action_ts, o.kind, o.user, o.user_name, o.conference, o.localnum "
                + "FROM messageoccurrences o, memberships m "
                + "WHERE o.conference = m.conference AND m.active = 1 AND m.user = ? AND o.message = ? AND o.kind <> 3");
    m_getMessageAttributesStmt =
        conn.prepareStatement(
            "SELECT id, message, kind, created, value "
                + "FROM messageattributes "
                + "WHERE message = ? "
                + "order by created asc");
    m_getMatchingMessageAttributesStmt =
        conn.prepareStatement(
            "select id, message, kind, created, value "
                + "from messageattributes "
                + "where message = ? and kind = ? "
                + "order by created asc");
    m_addMessageAttributeStmt =
        conn.prepareStatement(
            "INSERT INTO messageattributes (message, kind, created, value) "
                + "VALUES (?, ?, ?, ?)");
    m_dropMessageAttributeStmt =
        conn.prepareStatement("DELETE FROM messageattributes WHERE id = ? AND message = ?");
    m_dropMessageOccurrenceStmt =
        conn.prepareStatement(
            "UPDATE messageoccurrences SET kind = 3, message = NULL "
                + "WHERE localnum = ? AND conference = ?");
    m_countMessageOccurrencesStmt =
        conn.prepareStatement(
            "select count(*) from messageoccurrences " + "WHERE message = ? AND kind <> 3");
    m_dropMessageStmt = conn.prepareStatement("DELETE FROM messages WHERE id = ?");
    m_dropMessageSearchStmt = conn.prepareStatement("delete from messagesearch " + "where id = ?");

    // Ooooh! There's room for optimization here :-) I suggest we optimize by moving to an
    // RDBMS that supports indexed views (not to mention stored procedures..)
    //
    m_getGlobalBySubjectStmt =
        conn.prepareStatement(
            "select message "
                + "from messageoccurrences as mo "
                + "join memberships as ms on mo.conference = ms.conference "
                + "join messages on mo.message = messages.id "
                + "where ms.user = ? and subject = ? and mo.kind <> 3");
    m_getLocalBySubjectStmt =
        conn.prepareStatement(
            "select mo.localnum "
                + "from messageoccurrences mo, messages m "
                + "where m.id = mo.message and mo.conference = ? and m.subject = ? and mo.kind <> 3");

    // To speed things up, a special version to just retrieve the local ID.
    //
    m_getLocalIdsInConfStmt =
        conn.prepareStatement(
            "select localnum from messageoccurrences where conference = ? and kind <> 3");
    m_dropConferenceStmt = conn.prepareStatement("delete from conferences where id = ?");
    m_findLastOccurrenceInConferenceWithAttrStmt =
        conn.prepareStatement(
            "select localnum "
                + "from messageoccurrences as mo "
                + "join messageattributes as ma on mo.message = ma.message "
                + "where ma.kind = ? and mo.conference = ? and mo.kind <> 3 "
                + "order by mo.message desc "
                + "limit 1 offset 0");
    m_getLatestMagicMessageStmt =
        conn.prepareStatement(
            "select m.id "
                + "from messages as m, messageattributes ma "
                + "where ma.kind = ? and ma.value = ? and ma.message = m.id "
                + "order by m.created desc "
                + "limit 1 offset 0");

    m_searchMessagesLocally =
        conn.prepareStatement(
            "SELECT ms.id, mo.localnum, mo.user, m.author_name, ms.subject, m.reply_to, m.created, ma.value "
                + "FROM messagesearch ms "
                + "JOIN messageoccurrences mo ON ms.id = mo.message "
                + "JOIN messages m ON ms.id = m.id "
                + "LEFT OUTER JOIN messageattributes ma "
                + "ON m.id = ma.message "
                + "AND ma.kind = "
                + String.valueOf(MessageAttributes.MAIL_RECIPIENT)
                + " "
                + "WHERE mo.conference = ? and mo.kind <> 3 "
                + "AND MATCH(ms.subject, ms.body) AGAINST (? IN BOOLEAN MODE) "
                + "ORDER BY localnum DESC "
                + "LIMIT ? OFFSET ?");

    // Selecting from messagesearch is a couple of 1000 times faster. InnoDB...
    //
    m_countStmt = conn.prepareStatement("SELECT COUNT(*) FROM messagesearch");

    m_grepMessagesLocally =
        conn.prepareStatement(
            "SELECT ms.id, mo.localnum, mo.user, m.author_name, ms.subject, m.reply_to, m.created, ma.value "
                + "FROM messagesearch ms "
                + "JOIN messageoccurrences mo ON ms.id = mo.message "
                + "JOIN messages m ON ms.id = m.id "
                + "LEFT OUTER JOIN messageattributes ma "
                + "ON m.id = ma.message "
                + "AND ma.kind = "
                + String.valueOf(MessageAttributes.MAIL_RECIPIENT)
                + " "
                + "WHERE mo.conference = ? and mo.kind <> 3 "
                + "AND (ms.subject LIKE ? OR ms.body LIKE ?) "
                + "ORDER BY localnum DESC "
                + "LIMIT ? OFFSET ?");

    m_listAllMessagesLocally =
        conn.prepareStatement(
            "SELECT m.id, mo.localnum, mo.user, m.author_name, m.subject, m.reply_to, m.created, ma.value "
                + "FROM messages m "
                + "JOIN messageoccurrences mo ON m.id = mo.message "
                + "LEFT OUTER JOIN messageattributes ma "
                + "ON m.id = ma.message "
                + "AND ma.kind = "
                + String.valueOf(MessageAttributes.MAIL_RECIPIENT)
                + " "
                + "WHERE mo.conference = ? AND mo.kind <> 3 "
                + "ORDER BY localnum DESC "
                + "LIMIT ? OFFSET ?");

    m_listMessagesLocallyByAuthor =
        conn.prepareStatement(
            "SELECT m.id, mo.localnum, mo.user, m.author_name, m.subject, m.reply_to, m.created, ma.value "
                + "FROM messages m "
                + "JOIN messageoccurrences mo ON m.id = mo.message "
                + "LEFT OUTER JOIN messageattributes ma "
                + "ON m.id = ma.message "
                + "AND ma.kind = "
                + String.valueOf(MessageAttributes.MAIL_RECIPIENT)
                + " "
                + "WHERE mo.conference = ? AND m.author = ? AND mo.kind <> 3 "
                + "ORDER BY localnum DESC "
                + "LIMIT ? OFFSET ?");

    m_listMessagesGloballyByAuthor =
        conn.prepareStatement(
            "SELECT m.id, mo.localnum, mo.conference, n.fullname, n.visibility, mo.user, m.author_name, m.subject, m.reply_to, m.created "
                + "FROM messages m force index(msg_author_created), messageoccurrences mo, names n "
                + "WHERE m.id = mo.message AND n.id = mo.conference AND m.author = ? and mo.kind <> 3 "
                + "ORDER BY m.created DESC "
                + "LIMIT ? OFFSET ?");

    m_searchMessagesGlobally =
        conn.prepareStatement(
            "SELECT m.id, mo.localnum, mo.conference, n.fullname, n.visibility, mo.user, me.author_name, m.subject, me.reply_to, me.created "
                + "FROM messagesearch m, messages me, messageoccurrences mo, names n "
                + "WHERE m.id = mo.message AND n.id = mo.conference AND m.id = me.id AND mo.kind <> 3 "
                + "AND MATCH(m.subject, m.body) AGAINST (? IN BOOLEAN MODE) "
                + "ORDER BY mo.action_ts DESC "
                + "LIMIT ? OFFSET ?");
    m_setThreadIdStmt = conn.prepareStatement("UPDATE messages SET thread = ? WHERE id = ?");
    m_selectByThreadStmt = conn.prepareStatement("SELECT id FROM messages WHERE thread = ?");
    m_countMessagesLocallyByAuthor =
        conn.prepareStatement(
            "SELECT COUNT(*) FROM messages m, messageoccurrences mo WHERE "
                + "mo.message = m.id AND mo.message = m.id AND mo.conference = ? AND m.author = ? AND mo.kind <> 3");
    m_countMessagesGloballyByAuthor =
        conn.prepareStatement(
            "SELECT COUNT(*) FROM messages m, messageoccurrences mo, memberships me WHERE "
                + "mo.message = m.id AND mo.message = m.id AND m.author = ? AND mo.kind <> 3 "
                + "AND me.user = ? AND me.conference = mo.conference");
    m_countSearchMessagesGlobally =
        conn.prepareStatement(
            "SELECT COUNT(*) FROM messagesearch m, messageoccurrences mo, memberships me WHERE "
                + "mo.message = m.id AND mo.message = m.id AND mo.kind <> 3 "
                + "AND me.user = ? AND me.conference = mo.conference AND MATCH(m.subject, m.body) AGAINST (? IN BOOLEAN MODE)");
    m_countSearchMessagesLocally =
        conn.prepareStatement(
            "SELECT COUNT(*) FROM messagesearch m, messageoccurrences mo WHERE "
                + "mo.message = m.id AND mo.message = m.id AND mo.conference = ? AND mo.kind <> 3 "
                + "AND MATCH(m.subject, m.body) AGAINST (? IN BOOLEAN MODE)");
    m_countGrepMessagesLocally =
        conn.prepareStatement(
            "SELECT COUNT(*) FROM messagesearch m, messageoccurrences mo WHERE "
                + "mo.message = m.id AND mo.message = m.id AND mo.conference = ? AND mo.kind <> 3 "
                + "AND (m.subject LIKE ? OR m.body LIKE ?)");
    m_countAllMessagesLocally =
        conn.prepareStatement(
            "SELECT COUNT(*) FROM messageoccurrences WHERE conference = ? AND kind <> 3 ");

    // No need to filter out deleted occurrences here, since we're acting on the messages table.
    //
    m_listCommentsGloballyToAuthor =
        conn.prepareStatement(
            "SELECT m.id, mo.localnum, mo.conference, n.fullname, n.visibility, mo.user, m.author_name, m.subject, m.reply_to, m.created "
                + "FROM messages m, messages r, messageoccurrences mo, names n "
                + "WHERE m.reply_to = r.id AND m.id = mo.message AND n.id = mo.conference "
                + "AND r.author = ? AND r.created > ? "
                + "ORDER BY mo.action_ts DESC LIMIT ? OFFSET ?");
    m_countCommentsGloballyToAuthor =
        conn.prepareStatement(
            "SELECT COUNT(*) FROM messages m, messages r, messageoccurrences mo, memberships me "
                + "WHERE m.reply_to = r.id AND mo.message = m.id AND mo.message = m.id AND r.author = ? "
                + "AND me.user = ? AND me.conference = mo.conference AND r.created > ?");
    m_addBookmarkStmt =
        conn.prepareStatement(
            "INSERT INTO bookmarks(user, message, created, annotation) VALUES(?, ?, ?, ?)");
    m_deleteBookmarkStmt =
        conn.prepareStatement("DELETE FROM bookmarks WHERE user = ? AND message = ?");
    m_listBookmarksStmt =
        conn.prepareStatement(
            "SELECT message, annotation FROM bookmarks WHERE user = ? ORDER BY created DESC");
    m_getThreadIdForMessageStmt = conn.prepareStatement("select thread from messages where id = ?");
    m_getMessagesByThreadIdAndStartStmt =
        conn.prepareStatement("select id from messages where thread = ? and id > ?");
  }

  public void close() throws SQLException {
    if (m_loadMessageStmt != null) {
      m_loadMessageStmt.close();
    }
    if (m_loadMessageInConfStmt != null) {
      m_loadMessageInConfStmt.close();
    }
    if (m_loadMessageOccurrenceStmt != null) {
      m_loadMessageOccurrenceStmt.close();
    }
    if (m_getNextNumStmt != null) {
      m_getNextNumStmt.close();
    }
    if (m_addMessageStmt != null) {
      m_addMessageStmt.close();
    }
    if (m_addMessageOccurrenceStmt != null) {
      m_addMessageOccurrenceStmt.close();
    }
    if (m_listOccurrencesStmt != null) {
      m_listOccurrencesStmt.close();
    }
    if (m_getFirstOccurrenceStmt != null) {
      m_getFirstOccurrenceStmt.close();
    }
    if (m_getFirstOccurrenceInMyConfsStmt != null) {
      m_getFirstOccurrenceInMyConfsStmt.close();
    }
    if (m_getOccurrenceInConferenceStmt != null) {
      m_getOccurrenceInConferenceStmt.close();
    }
    if (m_getGlobalIdStmt != null) {
      m_getGlobalIdStmt.close();
    }
    if (m_getRepliesStmt != null) {
      m_getRepliesStmt.close();
    }
    if (m_getReplyIdsStmt != null) {
      m_getReplyIdsStmt.close();
    }
    if (m_loadOccurrenceStmt != null) {
      m_loadOccurrenceStmt.close();
    }
    if (m_getVisibleOccurrencesStmt != null) {
      m_getVisibleOccurrencesStmt.close();
    }
    if (m_dropMessageOccurrenceStmt != null) {
      m_dropMessageOccurrenceStmt.close();
    }
    if (m_countMessageOccurrencesStmt != null) {
      m_countMessageOccurrencesStmt.close();
    }
    if (m_dropMessageStmt != null) {
      m_dropMessageStmt.close();
    }
    if (m_listAllMessagesLocally != null) {
      m_listAllMessagesLocally.close();
    }
    if (m_getLocalIdsInConfStmt != null) {
      m_getLocalIdsInConfStmt.close();
    }
    if (m_dropConferenceStmt != null) {
      m_dropConferenceStmt.close();
    }
    if (m_getGlobalBySubjectStmt != null) {
      m_getGlobalBySubjectStmt.close();
    }
    if (m_findLastOccurrenceInConferenceWithAttrStmt != null) {
      m_findLastOccurrenceInConferenceWithAttrStmt.close();
    }
    if (m_getLatestMagicMessageStmt != null) {
      m_getLatestMagicMessageStmt.close();
    }
    if (m_countStmt != null) {
      m_countStmt.close();
    }
    if (m_setThreadIdStmt != null) {
      m_setThreadIdStmt.close();
    }
    if (m_selectByThreadStmt != null) {
      m_selectByThreadStmt.close();
    }
    if (m_countMessagesLocallyByAuthor != null) {
      m_countMessagesLocallyByAuthor.close();
    }
    if (m_countMessagesGloballyByAuthor != null) {
      m_countMessagesGloballyByAuthor.close();
    }
    if (m_countSearchMessagesGlobally != null) {
      m_countSearchMessagesGlobally.close();
    }
    if (m_countSearchMessagesLocally != null) {
      m_countSearchMessagesLocally.close();
    }
    if (m_countGrepMessagesLocally != null) {
      m_countGrepMessagesLocally.close();
    }
    if (m_countAllMessagesLocally != null) {
      m_countAllMessagesLocally.close();
    }
    if (m_addBookmarkStmt != null) {
      m_addBookmarkStmt.close();
    }
    if (m_deleteBookmarkStmt != null) {
      m_deleteBookmarkStmt.close();
    }
    if (m_listBookmarksStmt != null) {
      m_listBookmarksStmt.close();
    }
    if (m_getThreadIdForMessageStmt != null) {
      m_getThreadIdForMessageStmt.close();
    }
    if (m_getMessagesByThreadIdAndStartStmt != null) {
      m_getMessagesByThreadIdAndStartStmt.close();
    }
    if (m_getMessageAttributesStmt != null) {
      m_getMessageAttributesStmt.close();
    }
    if (m_getMatchingMessageAttributesStmt != null) {
      m_getMatchingMessageAttributesStmt.close();
    }
    if (m_addMessageAttributeStmt != null) {
      m_addMessageAttributeStmt.close();
    }
    if (m_dropMessageAttributeStmt != null) {
      m_dropMessageAttributeStmt.close();
    }
  }

  @Override
  public void finalize() {
    try {
      close();
    } catch (final SQLException e) {
      // Not much we can do...
      //
      e.printStackTrace();
    }
  }

  /**
   * Loads a message using its id
   *
   * @param id The id
   * @return The message
   * @throws MessageNotFoundException
   * @throws SQLException
   */
  public Message loadMessage(final long id) throws MessageNotFoundException, SQLException {
    m_loadMessageStmt.clearParameters();
    m_loadMessageStmt.setLong(1, id);
    try (final ResultSet rs = m_loadMessageStmt.executeQuery()) {
      if (!rs.next()) {
        throw new MessageNotFoundException("Message id=" + id);
      }
      return new Message(
          id,
          rs.getTimestamp(1), // created
          rs.getLong(2), // author
          new Name(rs.getString(3), Visibilities.PUBLIC, NameManager.USER_KIND), // Author name
          rs.getObject(4) != null ? rs.getLong(4) : -1, // reply to
          rs.getObject(5) != null ? rs.getLong(5) : -1, // thread
          rs.getString(6), // subject
          rs.getString(7), // body
          getOccurrences(id));
    }
  }

  /**
   * Loads a message using a conference id and a local number
   *
   * @return The message
   * @throws MessageNotFoundException
   * @throws SQLException
   */
  public Message loadMessage(final long conf, final int localNum)
      throws MessageNotFoundException, SQLException {
    m_loadMessageInConfStmt.clearParameters();
    m_loadMessageInConfStmt.setLong(1, conf);
    m_loadMessageInConfStmt.setInt(2, localNum);
    try (final ResultSet rs = m_loadMessageInConfStmt.executeQuery()) {
      if (!rs.next()) {
        throw new MessageNotFoundException("Message conf=" + conf + " localnum=" + localNum);
      }
      final long id = rs.getLong(1);
      return new Message(
          id,
          rs.getTimestamp(2), // created
          rs.getLong(3), // author
          new Name(rs.getString(4), Visibilities.PUBLIC, NameManager.USER_KIND), // author name
          rs.getObject(5) != null ? rs.getLong(5) : -1, // reply to
          rs.getObject(6) != null ? rs.getLong(6) : -1, // thread
          rs.getString(7), // subject
          rs.getString(8), // body
          getOccurrences(id));
    }
  }

  /**
   * Loads a message header (i.e. message, less the body) using its id
   *
   * @param id The id
   * @return The message
   * @throws MessageNotFoundException
   * @throws SQLException
   */
  public MessageHeader loadMessageHeader(final long id)
      throws MessageNotFoundException, SQLException {
    m_loadMessageHeaderStmt.clearParameters();
    m_loadMessageHeaderStmt.setLong(1, id);
    try (final ResultSet rs = m_loadMessageHeaderStmt.executeQuery()) {
      if (!rs.next()) {
        throw new MessageNotFoundException("Message id=" + id);
      }
      return new MessageHeader(
          id,
          rs.getTimestamp(1), // created
          rs.getLong(2), // author
          new Name(rs.getString(3), Visibilities.PUBLIC, NameManager.USER_KIND), // author name
          rs.getObject(4) != null ? rs.getLong(4) : -1, // reply to
          rs.getObject(5) != null ? rs.getLong(5) : -1, // reply to
          rs.getString(6));
    }
  }

  public MessageOccurrence createMessageOccurrence(
      final long globalId,
      final short kind,
      final long user,
      final String userName,
      final long conference)
      throws MessageNotFoundException, SQLException {
    ResultSet rs = null;
    try {
      // Get next message number
      //
      m_getNextNumStmt.clearParameters();
      m_getNextNumStmt.setLong(1, conference);
      rs = m_getNextNumStmt.executeQuery();
      if (!rs.next()) {
        throw new MessageNotFoundException("conference id=" + conference);
      }
      int num = rs.getInt(1) + 1;
      final Timestamp now = new Timestamp(System.currentTimeMillis());

      // Create message occurrence record
      //
      for (; ; ) {
        m_addMessageOccurrenceStmt.clearParameters();
        m_addMessageOccurrenceStmt.setLong(1, globalId);
        m_addMessageOccurrenceStmt.setTimestamp(2, now);
        m_addMessageOccurrenceStmt.setShort(3, kind);
        m_addMessageOccurrenceStmt.setLong(4, user);
        m_addMessageOccurrenceStmt.setString(5, userName);
        m_addMessageOccurrenceStmt.setLong(6, conference);
        m_addMessageOccurrenceStmt.setInt(7, num);
        try {
          m_addMessageOccurrenceStmt.executeUpdate();
          break;
        } catch (final SQLException e) {
          // SELECT MAX(localnum) ... FOR UPDATE does not seem to
          // put a lock on the entire table (IMHO, it should).
          // Anyway... Check for index uniqueness violations.
          //
          if (e.getMessage().startsWith("Duplicate key")) {
            // Message number clash! Bump number and try again!
            //
            LOG.info("Duplicate message number, trying again...");
            ++num;
            continue;
          }
          throw e;
        }
      }

      // Update conference records "last text" field
      //
      m_updateConferenceLasttext.clearParameters();
      m_updateConferenceLasttext.setTimestamp(1, now);
      m_updateConferenceLasttext.setLong(2, conference);
      m_updateConferenceLasttext.executeUpdate();

      // This changes the number of messages in a conference.
      //
      CacheManager.instance().getConferenceCache().registerInvalidation(conference);

      return new MessageOccurrence(
          globalId,
          now,
          kind,
          new NameAssociation(user, userName, NameManager.USER_KIND),
          conference,
          num);
    } finally {
      if (rs != null) {
        rs.close();
      }
    }
  }

  public MessageOccurrence addMessage(
      final long author,
      final String authorName,
      final long conference,
      final long replyTo,
      final String subject,
      final String body)
      throws MessageNotFoundException, SQLException, UnexpectedException {
    // Create message
    //
    final Timestamp now = new Timestamp(System.currentTimeMillis());
    m_addMessageStmt.clearParameters();
    m_addMessageStmt.setTimestamp(1, now);
    m_addMessageStmt.setLong(2, author);
    m_addMessageStmt.setString(3, authorName);
    if (replyTo != -1L) {
      m_addMessageStmt.setLong(4, replyTo);
    } else {
      m_addMessageStmt.setObject(4, null);
    }
    m_addMessageStmt.setString(5, subject);
    m_addMessageStmt.setString(6, body);
    m_addMessageStmt.executeUpdate();

    // Get hold of id of newly created record
    //
    final long id;
    try (final ResultSet rs = m_addBookmarkStmt.getResultSet()) {
      if (!rs.next()) {
        throw new UnexpectedException(-1, "No message ID generated");
      }
      id = rs.getLong(1);
    }

    // Create message occurrence record
    //
    final MessageOccurrence result =
        createMessageOccurrence(id, MessageManager.ACTION_CREATED, author, authorName, conference);

    // Add thread id if needed
    //
    final long thread;
    if (replyTo == -1) // We're the root of a thread, so use our own id
    {
      thread = id;
    } else {
      // This is a reply, so the thread id is the thread id
      // of the message we're replying to.
      //
      final MessageHeader mh = loadMessageHeader(replyTo);
      thread = mh.getThread();
    }

    // Update with thread id if needed
    //
    if (thread != -1) {
      m_setThreadIdStmt.clearParameters();
      m_setThreadIdStmt.setLong(1, thread);
      m_setThreadIdStmt.setLong(2, id);
      m_setThreadIdStmt.executeUpdate();
    }
    // Duplicate into search table
    //
    m_addMessageSearchStmt.clearParameters();
    m_addMessageSearchStmt.setLong(1, id);
    m_addMessageSearchStmt.setString(2, subject);
    m_addMessageSearchStmt.setString(3, body);
    m_addMessageSearchStmt.executeUpdate();

    return result;
  }

  /**
   * Returns the occurrence records for a message.
   *
   * @param messageId The global message id
   */
  public MessageOccurrence[] getOccurrences(final long messageId) throws SQLException {
    m_listOccurrencesStmt.clearParameters();
    m_listOccurrencesStmt.setLong(1, messageId);
    ResultSet rs = null;
    try {
      final List<MessageOccurrence> list = new ArrayList<>();
      rs = m_listOccurrencesStmt.executeQuery();
      while (rs.next()) {
        list.add(
            new MessageOccurrence(
                rs.getLong(1), // message id
                rs.getTimestamp(2), // Timestamp
                rs.getShort(3), // Kind,
                new NameAssociation(
                    rs.getLong(4), // User
                    rs.getString(5),
                    NameManager.USER_KIND), // User name
                rs.getLong(6), // Conference
                rs.getInt(7) // Localnum
                ));
      }
      final MessageOccurrence[] answer = new MessageOccurrence[list.size()];
      list.toArray(answer);
      return answer;
    } finally {
      if (rs != null) {
        rs.close();
      }
    }
  }

  /**
   * Returns a <tt>MessageOccurrence</tt> record representing the earliest message
   *
   * @param messageId The message id
   * @throws MessageNotFoundException
   * @throws SQLException
   */
  public MessageOccurrence getFirstOccurrence(final long messageId)
      throws MessageNotFoundException, SQLException {
    m_getFirstOccurrenceStmt.clearParameters();
    m_getFirstOccurrenceStmt.setLong(1, messageId);
    try (final ResultSet rs = m_getFirstOccurrenceStmt.executeQuery()) {
      if (!rs.next()) {
        throw new MessageNotFoundException("Message id=" + messageId);
      }

      return new MessageOccurrence(
          rs.getLong(1), // Global id
          rs.getTimestamp(2), // Timestamp
          rs.getShort(3), // Kind,
          new NameAssociation(
              rs.getLong(4), // User
              rs.getString(5),
              NameManager.USER_KIND), // User name
          rs.getLong(6), // Conference
          rs.getInt(7) // Localnum
          );
    }
  }

  /**
   * Returns a global message id based on a conference id and a local message number
   *
   * @param conference The conference id
   * @param localnum The local message number
   * @throws MessageNotFoundException
   * @throws SQLException
   */
  public long getGlobalMessageId(final long conference, final int localnum)
      throws MessageNotFoundException, SQLException {
    m_getGlobalIdStmt.clearParameters();
    m_getGlobalIdStmt.setLong(1, conference);
    m_getGlobalIdStmt.setInt(2, localnum);
    try (final ResultSet rs = m_getGlobalIdStmt.executeQuery()) {
      if (!rs.next()) {
        throw new MessageNotFoundException(
            "Message conference=" + conference + " localnum=" + localnum);
      }
      return rs.getLong(1);
    }
  }

  /**
   * Loads a <tt>MessageOccurrence</tt> for a local message number and a conference
   *
   * @param conference The conference
   * @param localnum The local message number
   * @throws MessageNotFoundException
   * @throws SQLException
   */
  public MessageOccurrence loadMessageOccurrence(final long conference, final int localnum)
      throws MessageNotFoundException, SQLException {
    m_loadOccurrenceStmt.clearParameters();
    m_loadOccurrenceStmt.setLong(1, conference);
    m_loadOccurrenceStmt.setInt(2, localnum);
    try (final ResultSet rs = m_loadOccurrenceStmt.executeQuery()) {
      if (!rs.next()) {
        throw new MessageNotFoundException(
            "Message conference=" + conference + " localnum=" + localnum);
      }
      return new MessageOccurrence(
          rs.getLong(1), // Global id
          rs.getTimestamp(2), // Timestamp
          rs.getShort(3), // Kind,
          new NameAssociation(
              rs.getLong(4), // User
              rs.getString(5),
              NameManager.USER_KIND), // User name
          conference,
          localnum);
    }
  }

  /**
   * Reurns an occurrence of a message in a conference
   *
   * @param conferenceId The conference
   * @param messageId The message
   * @throws MessageNotFoundException If the message didn't exist in this conference
   * @throws SQLException
   */
  public MessageOccurrence getOccurrenceInConference(final long conferenceId, final long messageId)
      throws MessageNotFoundException, SQLException {
    m_getOccurrenceInConferenceStmt.clearParameters();
    m_getOccurrenceInConferenceStmt.setLong(1, conferenceId);
    m_getOccurrenceInConferenceStmt.setLong(2, messageId);
    try (final ResultSet rs = m_getOccurrenceInConferenceStmt.executeQuery()) {
      if (!rs.next()) {
        throw new MessageNotFoundException("Message id=" + messageId);
      }

      return new MessageOccurrence(
          rs.getLong(1), // Global id
          rs.getTimestamp(2), // Timestamp
          rs.getShort(3), // Kind,
          new NameAssociation(
              rs.getLong(4), // User
              rs.getString(5),
              NameManager.USER_KIND), // User name
          rs.getLong(6), // Conference
          rs.getInt(7) // Localnum
          );
    }
  }

  /**
   * Given a message id and a conference, this method returns the message occurrence the user is
   * most likely interested in. The occurrence is determined as follows: <br>
   * 1: If the message exists in the current conference, pick that one <br>
   * 2: Otherwise, pick the earliest occurrence
   *
   * @param user The id of the requestor
   * @param conference The conference id
   * @param id The message id
   * @throws MessageNotFoundException
   * @throws SQLException
   */
  public MessageOccurrence getMostRelevantOccurrence(
      final long user, final long conference, final long id)
      throws MessageNotFoundException, SQLException {
    try {
      return getOccurrenceInConference(conference, id);
    } catch (final MessageNotFoundException e) {
      ResultSet rs = null;
      try {
        // Get first occurrence in a conference we're members of
        //
        m_getFirstOccurrenceInMyConfsStmt.clearParameters();
        m_getFirstOccurrenceInMyConfsStmt.setLong(1, user);
        m_getFirstOccurrenceInMyConfsStmt.setLong(2, id);
        rs = m_getFirstOccurrenceInMyConfsStmt.executeQuery();
        if (rs.next()) {
          return new MessageOccurrence(
              rs.getLong(1), // Global id
              rs.getTimestamp(2), // Timestamp
              rs.getShort(3), // Kind,
              new NameAssociation(
                  rs.getLong(4), // User
                  rs.getString(5),
                  NameManager.USER_KIND), // User name
              rs.getLong(6), // Conference
              rs.getInt(7) // Localnum
              );
        }
      } finally {
        if (rs != null) {
          rs.close();
        }
      }
      //
      // Does not exist in a conference we're members of. Pick the first occurrence!
      //
      return getFirstOccurrence(id);
    }
  }

  /**
   * Returns the original occurrence of a message, which is defined as: 1) If there is one with
   * kind=ACTION_CREATED, pick it, otherwise 2) Pick the one with kind=ACTION_MOVED, or if that also
   * does not exist, 3) Throw MessageNotFoundException.
   *
   * @param messageId The message ID
   * @return the original occurrence
   * @throws MessageNotFoundException
   * @throws SQLException
   */
  public MessageOccurrence getOriginalMessageOccurrence(final long messageId)
      throws MessageNotFoundException, SQLException {
    final MessageOccurrence[] occurrences = getOccurrences(messageId);
    for (final MessageOccurrence occurrence : occurrences) {
      // Note: In the highly unlikely event that there is more than one of either
      // a CREATED or MOVED occurrence, we'll pick the first one. The only way
      // this can happen is if someone screws up, THERE CAN BE ONLY ONE!
      //
      if (occurrence.getKind() == MessageManager.ACTION_CREATED
          || occurrence.getKind() == MessageManager.ACTION_MOVED) {
        return occurrence;
      }
    }
    throw new MessageNotFoundException();
  }

  /**
   * Returns the visible occurrences of a message, i.e. the occurrences that appear in conferences
   * the specified user is a member of.
   *
   * @param userId The user
   * @param globalId Global message id
   * @throws SQLException
   */
  public MessageOccurrence[] getVisibleOccurrences(final long userId, final long globalId)
      throws SQLException {
    final List<MessageOccurrence> list = new ArrayList<>();
    m_getVisibleOccurrencesStmt.clearParameters();
    m_getVisibleOccurrencesStmt.setLong(1, userId);
    m_getVisibleOccurrencesStmt.setLong(2, globalId);
    ResultSet rs = null;
    try {
      rs = m_getVisibleOccurrencesStmt.executeQuery();
      while (rs.next()) {
        list.add(
            new MessageOccurrence(
                rs.getLong(1), // Global id
                rs.getTimestamp(2), // Timestamp
                rs.getShort(3), // Kind
                new NameAssociation(
                    rs.getLong(4), // User
                    rs.getString(5),
                    NameManager.USER_KIND), // User name
                rs.getLong(6), // Conference
                rs.getInt(7) // Localnum
                ));
      }
      final MessageOccurrence[] answer = new MessageOccurrence[list.size()];
      list.toArray(answer);
      return answer;
    } finally {
      if (rs != null) {
        rs.close();
      }
    }
  }

  /**
   * Returns the <tt>MessageHeaders</tt> for the replies to a message
   *
   * @param originalId The message id of the original message
   * @return
   * @throws SQLException
   */
  public MessageHeader[] getReplies(final long originalId) throws SQLException {
    final List<MessageHeader> list = new ArrayList<>();
    m_getRepliesStmt.clearParameters();
    m_getRepliesStmt.setLong(1, originalId);
    try (final ResultSet rs = m_getRepliesStmt.executeQuery()) {
      while (rs.next()) {
        list.add(
            new MessageHeader(
                rs.getLong(1),
                rs.getTimestamp(2), // created
                rs.getLong(3), // author
                new Name(
                    rs.getString(4), Visibilities.PUBLIC, NameManager.USER_KIND), // author name
                rs.getObject(5) != null ? rs.getLong(5) : -1, // reply to
                rs.getObject(6) != null ? rs.getLong(6) : -1, // reply to
                rs.getString(7) // subject
                ));
      }
      final MessageHeader[] answer = new MessageHeader[list.size()];
      list.toArray(answer);
      return answer;
    }
  }

  /**
   * Returns the global ids of the replies to a message
   *
   * @param originalId
   * @throws SQLException
   */
  public long[] getReplyIds(final long originalId) throws SQLException {
    m_getReplyIdsStmt.clearParameters();
    m_getReplyIdsStmt.setLong(1, originalId);
    try (final ResultSet rs = m_getReplyIdsStmt.executeQuery()) {
      return SQLUtils.extractLongs(rs, 1);
    }
  }

  /**
   * Returns the attributes to a message
   *
   * @param messageId
   * @throws SQLException
   */
  public MessageAttribute[] getMessageAttributes(final long messageId) throws SQLException {
    final List<MessageAttribute> list = new ArrayList<>();
    m_getMessageAttributesStmt.clearParameters();
    m_getMessageAttributesStmt.setLong(1, messageId);
    try (final ResultSet rs = m_getMessageAttributesStmt.executeQuery()) {
      while (rs.next()) {
        list.add(
            new MessageAttribute(
                rs.getLong(1), // id
                rs.getLong(2), // message
                rs.getShort(3), // kind
                rs.getTimestamp(4), // created
                rs.getString(5))); // value
      }
      final MessageAttribute[] result = new MessageAttribute[list.size()];
      list.toArray(result);
      return result;
    }
  }

  public MessageAttribute[] getMatchingMessageAttributes(final long messageId, final short kind)
      throws SQLException {
    final List<MessageAttribute> list = new ArrayList<>();
    m_getMatchingMessageAttributesStmt.clearParameters();
    m_getMatchingMessageAttributesStmt.setLong(1, messageId);
    m_getMatchingMessageAttributesStmt.setShort(2, kind);
    try (final ResultSet rs = m_getMatchingMessageAttributesStmt.executeQuery()) {
      while (rs.next()) {
        list.add(
            new MessageAttribute(
                rs.getLong(1), // id
                rs.getLong(2), // message
                rs.getShort(3), // kind
                rs.getTimestamp(4), // created
                rs.getString(5))); // value
      }
      final MessageAttribute[] result = new MessageAttribute[list.size()];
      list.toArray(result);
      return result;
    }
  }

  public void addMessageAttribute(final long message, final short kind, final String value)
      throws SQLException {
    // Create messageattribute
    //
    final Timestamp now = new Timestamp(System.currentTimeMillis());
    m_addMessageAttributeStmt.clearParameters();
    m_addMessageAttributeStmt.setLong(1, message);
    m_addMessageAttributeStmt.setShort(2, kind);
    m_addMessageAttributeStmt.setTimestamp(3, now);
    m_addMessageAttributeStmt.setString(4, value);
    m_addMessageAttributeStmt.executeUpdate();
  }

  public void dropMessageAttribute(final long attributeid, final long messageid)
      throws SQLException {
    m_dropMessageAttributeStmt.clearParameters();
    m_dropMessageAttributeStmt.setLong(1, attributeid);
    m_dropMessageAttributeStmt.setLong(2, messageid);
    m_dropMessageAttributeStmt.executeUpdate();
  }

  public int getMessageOccurrenceCount(final long globalId) throws SQLException {
    ResultSet rs = null;
    try {
      m_countMessageOccurrencesStmt.clearParameters();
      m_countMessageOccurrencesStmt.setLong(1, globalId);
      rs = m_countMessageOccurrencesStmt.executeQuery();
      rs.first();
      return rs.getInt(1);
    } finally {
      if (rs != null) {
        rs.close();
      }
    }
  }

  public void dropMessageOccurrence(final int localNum, final long conference)
      throws SQLException, MessageNotFoundException {
    final long globalNum = getGlobalMessageId(conference, localNum);

    m_dropMessageOccurrenceStmt.clearParameters();
    m_dropMessageOccurrenceStmt.setInt(1, localNum);
    m_dropMessageOccurrenceStmt.setLong(2, conference);
    m_dropMessageOccurrenceStmt.execute();

    if (0 == getMessageOccurrenceCount(globalNum)) {
      // last occurrence deleted, so drop the message
      dropMessage(globalNum);
    }
  }

  public void dropMessage(final long globalNum) throws SQLException {
    // Invalidate cache for all occurrences
    //
    final MessageOccurrence[] occs = getOccurrences(globalNum);
    for (final MessageOccurrence occ : occs) {
      CacheManager.instance().getConferenceCache().registerInvalidation(occ.getConference());
    }

    // Add "original deleted" attribute to replies
    //
    try {
      final MessageHeader[] replies = getReplies(globalNum);
      final MessageHeader original = loadMessageHeader(globalNum);
      for (final MessageHeader reply : replies) {
        addMessageAttribute(
            reply.getId(),
            MessageAttributes.ORIGINAL_DELETED,
            MessageAttribute.constructUsernamePayload(
                original.getAuthor(), original.getAuthorName().getName()));
      }
    } catch (final MessageNotFoundException e) {
      // Well, uhh, if we got here it means we tried to delete a non-existing
      // message, which will fail naturally anyway. No worries, mate.
    }

    // Delete the actual message.
    //
    m_dropMessageStmt.clearParameters();
    m_dropMessageStmt.setLong(1, globalNum);
    m_dropMessageStmt.execute();

    m_dropMessageSearchStmt.clearParameters();
    m_dropMessageSearchStmt.setLong(1, globalNum);
    m_dropMessageSearchStmt.execute();
  }

  public void deleteConference(final long conference) throws SQLException {
    CacheManager.instance().getConferenceCache().registerInvalidation(conference);
    m_getLocalIdsInConfStmt.clearParameters();
    m_getLocalIdsInConfStmt.setLong(1, conference);
    final ResultSet rs = m_getLocalIdsInConfStmt.executeQuery();
    while (rs.next()) {
      try {
        dropMessageOccurrence(rs.getInt(1), conference);
      } catch (final MessageNotFoundException f) {
        // Ignore. This exception is probably due to someone deleting the message before
        // we got to it.
      }
    }
  }

  public long[] getMessagesBySubject(final String subject, final long user) throws SQLException {
    m_getGlobalBySubjectStmt.clearParameters();
    m_getGlobalBySubjectStmt.setLong(1, user);
    m_getGlobalBySubjectStmt.setString(2, subject);
    final ResultSet rs = m_getGlobalBySubjectStmt.executeQuery();
    return SQLUtils.extractLongs(rs, 1);
  }

  public int[] getLocalMessagesBySubject(final String subject, final long conference)
      throws SQLException {
    m_getLocalBySubjectStmt.clearParameters();
    m_getLocalBySubjectStmt.setLong(1, conference);
    m_getLocalBySubjectStmt.setString(2, subject);
    final ResultSet rs = m_getLocalBySubjectStmt.executeQuery();
    return SQLUtils.extractInts(rs, 1);
  }

  public int findLastOccurrenceInConferenceWithAttrStmt(final short attrKind, final long conference)
      throws SQLException {
    m_findLastOccurrenceInConferenceWithAttrStmt.clearParameters();
    m_findLastOccurrenceInConferenceWithAttrStmt.setShort(1, attrKind);
    m_findLastOccurrenceInConferenceWithAttrStmt.setLong(2, conference);
    try (final ResultSet rs = m_findLastOccurrenceInConferenceWithAttrStmt.executeQuery()) {
      rs.first();
      return rs.getInt(1);
    }
  }

  public long getTaggedMessage(final long objectId, final short kind)
      throws SQLException, MessageNotFoundException {
    ResultSet rs = null;
    try {
      m_getLatestMagicMessageStmt.clearParameters();
      m_getLatestMagicMessageStmt.setShort(1, kind);
      m_getLatestMagicMessageStmt.setString(2, Long.toString(objectId));
      rs = m_getLatestMagicMessageStmt.executeQuery();
      if (!rs.next()) {
        throw new MessageNotFoundException("object=" + objectId + ", kind=" + kind);
      }
      return rs.getLong(1);
    } finally {
      if (null != rs) {
        rs.close();
      }
    }
  }

  public LocalMessageSearchResult[] searchMessagesLocally(
      final long conference, final String searchterm, final int offset, final int length)
      throws SQLException {
    m_searchMessagesLocally.clearParameters();
    m_searchMessagesLocally.setLong(1, conference);
    m_searchMessagesLocally.setString(2, searchterm);
    m_searchMessagesLocally.setLong(3, length);
    m_searchMessagesLocally.setLong(4, offset);

    return innerLocalSearch(m_searchMessagesLocally);
  }

  public long countSearchMessagesLocally(final long conference, final String searchterm)
      throws SQLException {
    m_countSearchMessagesLocally.clearParameters();
    m_countSearchMessagesLocally.setLong(1, conference);
    m_countSearchMessagesLocally.setString(2, searchterm);
    return innerCount(m_countSearchMessagesLocally);
  }

  public long countGrepMessagesLocally(final long conference, final String searchterm)
      throws SQLException {
    m_countGrepMessagesLocally.clearParameters();
    m_countGrepMessagesLocally.setLong(1, conference);
    m_countGrepMessagesLocally.setString(2, "%" + searchterm + "%");
    m_countGrepMessagesLocally.setString(3, "%" + searchterm + "%");
    return innerCount(m_countGrepMessagesLocally);
  }

  public long countAllMessagesLocally(final long conference) throws SQLException {
    m_countAllMessagesLocally.clearParameters();
    m_countAllMessagesLocally.setLong(1, conference);
    return innerCount(m_countAllMessagesLocally);
  }

  public long countMessagesLocallyByAuthor(final long conference, final long user)
      throws SQLException {
    m_countMessagesLocallyByAuthor.clearParameters();
    m_countMessagesLocallyByAuthor.setLong(1, conference);
    m_countMessagesLocallyByAuthor.setLong(2, user);
    return innerCount(m_countMessagesLocallyByAuthor);
  }

  public long countMessagesGloballyByAuthor(final long user, final long requester)
      throws SQLException {
    m_countMessagesGloballyByAuthor.clearParameters();
    m_countMessagesGloballyByAuthor.setLong(1, user);
    m_countMessagesGloballyByAuthor.setLong(2, requester);
    return innerCount(m_countMessagesGloballyByAuthor);
  }

  public long countSearchMessagesGlobally(final String searchterm, final long requester)
      throws SQLException {
    m_countSearchMessagesGlobally.clearParameters();
    m_countSearchMessagesGlobally.setLong(1, requester);
    m_countSearchMessagesGlobally.setString(2, searchterm);
    return innerCount(m_countSearchMessagesGlobally);
  }

  public long innerCount(final PreparedStatement stmt) throws SQLException {
    try (final ResultSet rs = stmt.executeQuery()) {
      if (!rs.next()) {
        return 0; // Hmmm...
      }
      return rs.getLong(1);
    }
  }

  public LocalMessageSearchResult[] grepMessagesLocally(
      final long conference, final String searchterm, final int offset, final int length)
      throws SQLException {
    m_grepMessagesLocally.clearParameters();
    m_grepMessagesLocally.setLong(1, conference);
    m_grepMessagesLocally.setString(2, "%" + searchterm + "%");
    m_grepMessagesLocally.setString(3, "%" + searchterm + "%");
    m_grepMessagesLocally.setLong(4, length);
    m_grepMessagesLocally.setLong(5, offset);

    return innerLocalSearch(m_grepMessagesLocally);
  }

  public LocalMessageSearchResult[] listAllMessagesLocally(
      final long conference, final int offset, final int length) throws SQLException {
    m_listAllMessagesLocally.clearParameters();
    m_listAllMessagesLocally.setLong(1, conference);
    m_listAllMessagesLocally.setInt(2, length);
    m_listAllMessagesLocally.setInt(3, offset);

    return innerLocalSearch(m_listAllMessagesLocally);
  }

  public LocalMessageSearchResult[] listMessagesLocallyByAuthor(
      final long conference, final long user, final int offset, final int length)
      throws SQLException {
    m_listMessagesLocallyByAuthor.clearParameters();
    m_listMessagesLocallyByAuthor.setLong(1, conference);
    m_listMessagesLocallyByAuthor.setLong(2, user);
    m_listMessagesLocallyByAuthor.setInt(3, length);
    m_listMessagesLocallyByAuthor.setInt(4, offset);

    return innerLocalSearch(m_listMessagesLocallyByAuthor);
  }

  private LocalMessageSearchResult[] innerLocalSearch(final PreparedStatement localSearchStatement)
      throws SQLException {
    final ResultSet rs = localSearchStatement.executeQuery();
    final List<LocalMessageSearchResult> l = new ArrayList<>();
    while (rs.next()) {

      NameAssociation mailRecipient = null;
      final String mailRecipientMessageAttributeValue = rs.getString(8);
      if (mailRecipientMessageAttributeValue != null) {
        mailRecipient =
            new NameAssociation(
                MessageAttribute.parseUserIdPayload(
                    mailRecipientMessageAttributeValue, MessageAttributes.MAIL_RECIPIENT),
                MessageAttribute.parseUserNamePayload(
                    mailRecipientMessageAttributeValue, MessageAttributes.MAIL_RECIPIENT));
      }

      l.add(
          new LocalMessageSearchResult(
              rs.getLong(1), // globalid
              rs.getInt(2), // localid
              new NameAssociation(
                  rs.getLong(3), // authorid
                  new Name(
                      rs.getString(4), Visibilities.PUBLIC, NameManager.USER_KIND)), // authorname
              rs.getString(5), // subject
              rs.getLong(6), // Reply to
              rs.getTimestamp(7), // Timestamp
              mailRecipient));
    }
    final LocalMessageSearchResult[] lmsr = new LocalMessageSearchResult[l.size()];
    l.toArray(lmsr);
    return lmsr;
  }

  public GlobalMessageSearchResult[] listMessagesGloballyByAuthor(
      final long user, final int offset, final int length) throws SQLException {
    m_listMessagesGloballyByAuthor.clearParameters();
    m_listMessagesGloballyByAuthor.setLong(1, user);
    m_listMessagesGloballyByAuthor.setInt(2, length);
    m_listMessagesGloballyByAuthor.setInt(3, offset);

    return innerGlobalSearch(m_listMessagesGloballyByAuthor);
  }

  public GlobalMessageSearchResult[] searchMessagesGlobally(
      final String searchterm, final int offset, final int length) throws SQLException {
    m_searchMessagesGlobally.clearParameters();
    m_searchMessagesGlobally.setString(1, searchterm);
    m_searchMessagesGlobally.setInt(2, length);
    m_searchMessagesGlobally.setInt(3, offset);

    return innerGlobalSearch(m_searchMessagesGlobally);
  }

  private GlobalMessageSearchResult[] innerGlobalSearch(
      final PreparedStatement globalSearchStatement) throws SQLException {
    final ResultSet rs = globalSearchStatement.executeQuery();
    final List<GlobalMessageSearchResult> l = new ArrayList<>();
    while (rs.next()) {
      l.add(
          new GlobalMessageSearchResult(
              rs.getLong(1), // globalid
              rs.getInt(2), // localid
              new NameAssociation(
                  rs.getLong(3), // conferenceid
                  new Name(rs.getString(4), rs.getShort(5), NameManager.USER_KIND)),
              new NameAssociation(
                  rs.getLong(6), // authorid
                  new Name(
                      rs.getString(7), Visibilities.PUBLIC, NameManager.USER_KIND)), // authorname
              rs.getString(8), // subject
              rs.getLong(9), // reply to
              rs.getTimestamp(10)) // Timestamp
          );
    }
    final GlobalMessageSearchResult[] lmsr = new GlobalMessageSearchResult[l.size()];
    l.toArray(lmsr);
    return lmsr;
  }

  public long countMessages() throws SQLException {
    try (final ResultSet rs = m_countStmt.executeQuery()) {
      rs.first();
      return rs.getLong(1);
    }
  }

  public long[] selectByThread(final long threadId, final int max)
      throws SQLException, SelectionOverflowException {
    ResultSet rs = null;
    final long[] buffer = new long[max];
    try {
      m_selectByThreadStmt.clearParameters();
      m_selectByThreadStmt.setLong(1, threadId);
      rs = m_selectByThreadStmt.executeQuery();
      int idx = 0;
      while (rs.next()) {
        buffer[idx++] = rs.getLong(1);
      }
      final long[] result = new long[idx];
      System.arraycopy(buffer, 0, result, 0, idx);
      return result;
    } catch (final ArrayIndexOutOfBoundsException e) {
      throw new SelectionOverflowException(buffer);
    } finally {
      if (rs != null) {
        rs.close();
      }
    }
  }

  public GlobalMessageSearchResult[] listCommentsGloballyToAuthor(
      final long user, final Timestamp startDate, final int offset, final int length)
      throws SQLException {
    m_listCommentsGloballyToAuthor.clearParameters();
    m_listCommentsGloballyToAuthor.setLong(1, user);
    m_listCommentsGloballyToAuthor.setTimestamp(2, startDate);
    m_listCommentsGloballyToAuthor.setInt(3, length);
    m_listCommentsGloballyToAuthor.setInt(4, offset);

    return innerGlobalSearch(m_listCommentsGloballyToAuthor);
  }

  public long countCommentsGloballyToAuthor(
      final long user, final long requester, final Timestamp startDate) throws SQLException {
    m_countCommentsGloballyToAuthor.clearParameters();
    m_countCommentsGloballyToAuthor.setLong(1, user);
    m_countCommentsGloballyToAuthor.setLong(2, requester);
    m_countCommentsGloballyToAuthor.setTimestamp(3, startDate);
    return innerCount(m_countCommentsGloballyToAuthor);
  }

  public void addBookmark(final long user, final long message, final String annotation)
      throws SQLException {
    m_addBookmarkStmt.clearParameters();
    m_addBookmarkStmt.setLong(1, user);
    m_addBookmarkStmt.setLong(2, message);
    m_addBookmarkStmt.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
    m_addBookmarkStmt.setString(4, annotation);
    m_addBookmarkStmt.executeUpdate();
  }

  public void deleteBookmark(final long user, final long message) throws SQLException {
    m_deleteBookmarkStmt.clearParameters();
    m_deleteBookmarkStmt.setLong(1, user);
    m_deleteBookmarkStmt.setLong(2, message);
    m_deleteBookmarkStmt.executeUpdate();
  }

  public Bookmark[] listBookmarks(final long user) throws SQLException {
    m_listBookmarksStmt.clearParameters();
    m_listBookmarksStmt.setLong(1, user);
    ResultSet rs = null;
    try {
      final List<Bookmark> list = new ArrayList<>();
      rs = m_listBookmarksStmt.executeQuery();
      while (rs.next()) {
        list.add(
            new Bookmark(
                rs.getLong(1), // User
                rs.getLong(2), // Message
                rs.getString(3))); // Annotation
      }
      final Bookmark[] answer = new Bookmark[list.size()];
      list.toArray(answer);
      return answer;
    } finally {
      if (rs != null) {
        rs.close();
      }
    }
  }

  public long getThreadIdForMessage(final long msgid) throws SQLException {
    m_getThreadIdForMessageStmt.clearParameters();
    m_getThreadIdForMessageStmt.setLong(1, msgid);

    try (final ResultSet rs = m_getThreadIdForMessageStmt.executeQuery()) {
      rs.first();
      return rs.getLong(1);
    }
  }
}
