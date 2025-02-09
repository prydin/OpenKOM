/*
 * Created on Aug 25, 2004
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
import nu.rydin.kom.exceptions.ObjectNotFoundException;
import nu.rydin.kom.structs.FileStatus;

/** @author Pontus Rydin */
public class FileManager {
  private final PreparedStatement m_createStmt;

  private final PreparedStatement m_updateStmt;

  private final PreparedStatement m_statStmt;

  private final PreparedStatement m_listStmt;

  private final PreparedStatement m_readStmt;

  private final PreparedStatement m_chmodStmt;

  private final PreparedStatement m_deleteStmt;

  public FileManager(final Connection conn) throws SQLException {
    m_createStmt =
        conn.prepareStatement(
            "INSERT INTO files(parent, name, protection, created, updated, content) "
                + "VALUES(?, ?, 0, ?, ?, ?)");
    m_updateStmt =
        conn.prepareStatement(
            "UPDATE files SET updated = ?, content = ? WHERE parent = ? AND name = ?");
    m_statStmt =
        conn.prepareStatement(
            "SELECT protection, created, updated FROM files WHERE parent = ? AND name = ?");
    m_listStmt =
        conn.prepareStatement(
            "SELECT name, protection, created, updated FROM files WHERE parent = ? AND name LIKE ?");
    m_readStmt = conn.prepareStatement("SELECT content FROM files WHERE parent = ? AND name = ?");
    m_chmodStmt =
        conn.prepareStatement("UPDATE files SET protection = ? WHERE parent = ? AND name = ?");
    m_deleteStmt = conn.prepareStatement("DELETE FROM files WHERE parent = ? AND name = ?");
  }

  public FileStatus stat(final long parent, final String name) throws SQLException, ObjectNotFoundException {
    m_statStmt.clearParameters();
    m_statStmt.setLong(1, parent);
    m_statStmt.setString(2, name);
    try (final ResultSet rs = m_statStmt.executeQuery()) {
      if (!rs.next()) {
          throw new ObjectNotFoundException(name);
      }
      return new FileStatus(
          parent,
          name,
          rs.getInt(1),
          rs.getTimestamp(2), // Created
          rs.getTimestamp(3)); // Updated
    }
  }

  public void store(final long parent, final String name, final String content) throws SQLException {
    // Check if the file exists
    //
    try {
        stat(parent, name);

      // The file existed. Update
      //
      m_updateStmt.clearParameters();
      m_updateStmt.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
      m_updateStmt.setString(2, content);
      m_updateStmt.setLong(3, parent);
      m_updateStmt.setString(4, name);
      m_updateStmt.executeUpdate();
    } catch (final ObjectNotFoundException e) {
      // Not found. Create
      //
      m_createStmt.clearParameters();
      m_createStmt.setLong(1, parent);
      m_createStmt.setString(2, name);
      m_createStmt.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
      m_createStmt.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
      m_createStmt.setString(5, content);
      m_createStmt.executeUpdate();
    }
  }

  public FileStatus[] list(final long parent, final String pattern) throws SQLException {
    final ArrayList<FileStatus> list = new ArrayList<>();
    m_listStmt.clearParameters();
    m_listStmt.setLong(1, parent);
    m_listStmt.setString(2, pattern);
    try (final ResultSet rs = m_listStmt.executeQuery()) {
      while (rs.next()) {
        list.add(
            new FileStatus(
                parent,
                rs.getString(1),
                rs.getInt(2),
                rs.getTimestamp(3), // created
                rs.getTimestamp(4))); // updated
      }
      final FileStatus[] answer = new FileStatus[list.size()];
      list.toArray(answer);
      return answer;
    }
  }

  public String read(final long parent, final String name) throws SQLException, ObjectNotFoundException {
    m_readStmt.clearParameters();
    m_readStmt.setLong(1, parent);
    m_readStmt.setString(2, name);
    try (final ResultSet rs = m_readStmt.executeQuery()) {
      if (!rs.next()) {
          throw new ObjectNotFoundException();
      }
      return rs.getString(1);
    }
  }

  public void chmod(final long parent, final String name, final int mode)
      throws SQLException, ObjectNotFoundException {
    m_chmodStmt.clearParameters();
    m_chmodStmt.setInt(1, mode);
    m_chmodStmt.setLong(2, parent);
    m_chmodStmt.setString(3, name);
    if (m_chmodStmt.executeUpdate() == 0) {
        throw new ObjectNotFoundException(name);
    }
  }

  public void delete(final long parent, final String name) throws SQLException, ObjectNotFoundException {
    m_deleteStmt.clearParameters();
    m_deleteStmt.setLong(1, parent);
    m_deleteStmt.setString(2, name);
    if (m_deleteStmt.executeUpdate() == 0) {
        throw new ObjectNotFoundException(name);
    }
  }
}
