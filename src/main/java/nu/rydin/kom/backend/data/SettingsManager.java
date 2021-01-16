/*
 * Created on Sep 15, 2004
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.backend.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import nu.rydin.kom.exceptions.ObjectNotFoundException;

/** @author Pontus Rydin */
public class SettingsManager {
  private final PreparedStatement m_addSettingStmt;
  private final PreparedStatement m_changeSettingStmt;
  private final PreparedStatement m_deleteSettingStmt;
  private final PreparedStatement m_getSettingStmt;

  public SettingsManager(final Connection conn) throws SQLException {
    m_addSettingStmt =
        conn.prepareStatement(
            "INSERT INTO settings(name, string_value, numeric_value) VALUES(?, ?, ?)");
    m_changeSettingStmt =
        conn.prepareStatement(
            "UPDATE settings SET string_value = ?, numeric_value = ? WHERE name = ?");
    m_deleteSettingStmt = conn.prepareStatement("DELETE FROM settings WHERE name = ?");
    m_getSettingStmt =
        conn.prepareStatement("SELECT string_value, numeric_value FROM settings WHERE name = ?");
  }

  public void close() throws SQLException {
    if (m_addSettingStmt != null) {
      m_addSettingStmt.close();
    }
    if (m_changeSettingStmt != null) {
      m_changeSettingStmt.close();
    }
    if (m_deleteSettingStmt != null) {
      m_deleteSettingStmt.close();
    }
    if (m_getSettingStmt != null) {
      m_getSettingStmt.close();
    }
  }

  @Override
  public void finalize() {
    try {
      close();
    } catch (final SQLException e) {
      // Not much we can do!
    }
  }

  public void changeSetting(final String name, final String stringValue, final long numValue)
      throws SQLException {
    // First, try to update
    //
    m_changeSettingStmt.clearParameters();
    m_changeSettingStmt.setString(1, stringValue);
    m_changeSettingStmt.setLong(2, numValue);
    m_changeSettingStmt.setString(3, name);
    if (m_changeSettingStmt.executeUpdate() != 0) {
      return;
    }

    // Nothing there. Insert!
    //
    m_addSettingStmt.clearParameters();
    m_addSettingStmt.setString(1, name);
    m_addSettingStmt.setString(2, stringValue);
    m_addSettingStmt.setLong(3, numValue);
    m_addSettingStmt.executeUpdate();
  }

  public void deleteSetting(final String name) throws SQLException {
    m_deleteSettingStmt.clearParameters();
    m_deleteSettingStmt.setString(1, name);
    m_deleteSettingStmt.executeUpdate();
  }

  public String getString(final String name) throws SQLException, ObjectNotFoundException {
    return runQuery(name).getString(1);
  }

  public long getNumber(final String name) throws SQLException, ObjectNotFoundException {
    return runQuery(name).getLong(2);
  }

  private ResultSet runQuery(final String name) throws SQLException, ObjectNotFoundException {
    m_getSettingStmt.clearParameters();
    m_getSettingStmt.setString(1, name);
    try (final ResultSet rs = m_getSettingStmt.executeQuery()) {
      if (!rs.next()) {
        throw new ObjectNotFoundException(name);
      }
      return rs;
    }
  }
}
