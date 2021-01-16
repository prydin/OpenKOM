/*
 * Created on Oct 7, 2004
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
import nu.rydin.kom.exceptions.ObjectNotFoundException;
import nu.rydin.kom.structs.Relationship;

/** @author Pontus Rydin */
public class RelationshipManager {
  private final PreparedStatement m_addStmt;
  private final PreparedStatement m_changeStmt;
  private final PreparedStatement m_deleteStmt;
  private final PreparedStatement m_deleteByPartiesAndTypeStmt;
  private final PreparedStatement m_getByIdStmt;
  private final PreparedStatement m_listByRefererAndKindStmt;
  private final PreparedStatement m_findStmt;

  public RelationshipManager(final Connection conn) throws SQLException {
    m_addStmt =
        conn.prepareStatement(
            "INSERT INTO relationships(referer, referee, kind, flags) VALUES(?, ?, ?, ?)");
    m_changeStmt = conn.prepareStatement("UPDATE relationships SET flags = ? WHERE id = ?");
    m_deleteStmt = conn.prepareStatement("DELETE FROM relationships WHERE id = ?");
    m_deleteByPartiesAndTypeStmt =
        conn.prepareStatement(
            "DELETE FROM relationships WHERE referer = ? AND referee = ? AND kind = ?");
    m_getByIdStmt =
        conn.prepareStatement(
            "SELECT referer, referee, kind, flags FROM relationships WHERE id = ?");
    m_listByRefererAndKindStmt =
        conn.prepareStatement(
            "SELECT id, referee, flags FROM relationships WHERE referer = ? AND kind = ?");
    m_findStmt =
        conn.prepareStatement(
            "SELECT id, flags FROM relationships WHERE "
                + "referer = ? AND referee = ? AND kind = ?");
  }

  public void close() throws SQLException {
    if (m_addStmt != null) {
      m_addStmt.close();
    }
    if (m_changeStmt != null) {
      m_changeStmt.close();
    }
    if (m_deleteStmt != null) {
      m_deleteStmt.close();
    }
    if (m_deleteByPartiesAndTypeStmt != null) {
      m_deleteByPartiesAndTypeStmt.close();
    }
    if (m_getByIdStmt != null) {
      m_getByIdStmt.close();
    }
    if (m_listByRefererAndKindStmt != null) {
      m_listByRefererAndKindStmt.close();
    }
    if (m_findStmt != null) {
      m_findStmt.close();
    }
  }

  public void addRelationship(
      final long referer, final long referee, final int kind, final long flags)
      throws SQLException {
    m_addStmt.clearParameters();
    m_addStmt.setLong(1, referer);
    m_addStmt.setLong(2, referee);
    m_addStmt.setInt(3, kind);
    m_addStmt.setLong(4, flags);
    m_addStmt.executeUpdate();
  }

  public void changeFlags(final long id, final long flags)
      throws SQLException, ObjectNotFoundException {
    m_changeStmt.clearParameters();
    m_changeStmt.setLong(1, flags);
    m_changeStmt.setLong(2, id);
    if (m_changeStmt.executeUpdate() == 0) {
      throw new ObjectNotFoundException();
    }
  }

  public Relationship getById(final long id) throws SQLException, ObjectNotFoundException {
    m_getByIdStmt.clearParameters();
    m_getByIdStmt.setLong(1, id);
    try (final ResultSet rs = m_getByIdStmt.executeQuery()) {
      if (!rs.next()) {
        throw new ObjectNotFoundException("id=" + id);
      }
      return new Relationship(
          id, // Id
          rs.getLong(1), // Referer
          rs.getLong(2), // Referee
          rs.getInt(3), // Kind
          rs.getLong(4)); // Flags
    }
  }

  public Relationship[] find(final long referer, final long referee, final int kind)
      throws SQLException {
    m_findStmt.clearParameters();
    m_findStmt.setLong(1, referer);
    m_findStmt.setLong(2, referee);
    m_findStmt.setInt(3, kind);
    try (final ResultSet rs = m_findStmt.executeQuery()) {
      final ArrayList<Relationship> list = new ArrayList<>();
      while (rs.next()) {
        list.add(
            new Relationship(
                rs.getLong(1), // Id
                referer, // Referer
                referee, // Referee
                kind, // Kind
                rs.getLong(2))); // Flags
      }
      final Relationship[] answer = new Relationship[list.size()];
      list.toArray(answer);
      return answer;
    }
  }

  public Relationship[] listByRefererAndKind(final long referer, final int kind)
      throws SQLException {
    m_listByRefererAndKindStmt.clearParameters();
    m_listByRefererAndKindStmt.setLong(1, referer);
    m_listByRefererAndKindStmt.setInt(2, kind);
    try (final ResultSet rs = m_listByRefererAndKindStmt.executeQuery()) {
      final ArrayList<Relationship> list = new ArrayList<>();
      while (rs.next()) {
        list.add(
            new Relationship(
                rs.getLong(1), // Id
                referer, // Referer
                rs.getLong(2), // Referee
                kind, // Kind
                rs.getLong(3))); // Flags
      }
      final Relationship[] answer = new Relationship[list.size()];
      list.toArray(answer);
      return answer;
    }
  }

  public void delete(final long id) throws SQLException, ObjectNotFoundException {
    m_deleteStmt.clearParameters();
    m_deleteStmt.setLong(1, id);
    if (m_deleteStmt.executeUpdate() == 0) {
      throw new ObjectNotFoundException("relationship id=" + id);
    }
  }
}
