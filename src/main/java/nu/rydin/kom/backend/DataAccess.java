/*
 * Created on Nov 4, 2003
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.backend;

import java.sql.Connection;
import java.sql.SQLException;
import nu.rydin.kom.backend.data.ConferenceManager;
import nu.rydin.kom.backend.data.FileManager;
import nu.rydin.kom.backend.data.MembershipManager;
import nu.rydin.kom.backend.data.MessageLogManager;
import nu.rydin.kom.backend.data.MessageManager;
import nu.rydin.kom.backend.data.NameManager;
import nu.rydin.kom.backend.data.RelationshipManager;
import nu.rydin.kom.backend.data.SettingsManager;
import nu.rydin.kom.backend.data.UserLogManager;
import nu.rydin.kom.backend.data.UserManager;
import nu.rydin.kom.exceptions.UnexpectedException;

/** @author Pontus Rydin */
public class DataAccess {
  /** JDBC connection to use for all database operations */
  private final Connection m_conn;

  private final NameManager m_nameManager;

  /** Toolkit object dealing with users */
  private final UserManager m_userManager;

  /** Toolkit object dealing with conferences */
  private final ConferenceManager m_conferenceManager;

  /** Toolkit object dealing with users */
  private final MembershipManager m_membershipManager;

  /** Toolkit object dealing with users */
  private final MessageManager m_messageManager;

  /** Toolkit object dealing with message logs */
  private final MessageLogManager m_messageLogManager;

  /** Toolkit object dealing with user logs */
  private final UserLogManager m_userLogManager;

  /** Toolkit object dealing with settings */
  private final SettingsManager m_settingManager;

  /** Toolkit object dealing with files */
  private final FileManager m_fileManager;

  /** Toolkit object handling relationships */
  private final RelationshipManager m_relationshipManager;

  public DataAccess(Connection conn) throws UnexpectedException {
    m_conn = conn;
    try {
      m_nameManager = new NameManager(conn);
      m_userManager = new UserManager(conn, CacheManager.instance(), m_nameManager);
      m_conferenceManager = new ConferenceManager(conn, m_nameManager);
      m_membershipManager = new MembershipManager(conn);
      m_messageManager = new MessageManager(conn);
      m_messageLogManager = new MessageLogManager(conn);
      m_userLogManager = new UserLogManager(conn);
      m_fileManager = new FileManager(conn);
      m_settingManager = new SettingsManager(conn);
      m_relationshipManager = new RelationshipManager(conn);
    } catch (SQLException e) {
      throw new UnexpectedException(-1, "Error while creating DataAccess", e);
    }
  }

  public NameManager getNameManager() {
    return m_nameManager;
  }

  public UserManager getUserManager() {
    return m_userManager;
  }

  public ConferenceManager getConferenceManager() {
    return m_conferenceManager;
  }

  public MembershipManager getMembershipManager() {
    return m_membershipManager;
  }

  public MessageManager getMessageManager() {
    return m_messageManager;
  }

  public MessageLogManager getMessageLogManager() {
    return m_messageLogManager;
  }

  public UserLogManager getUserLogManager() {
    return m_userLogManager;
  }

  public FileManager getFileManager() {
    return m_fileManager;
  }

  public SettingsManager getSettingManager() {
    return m_settingManager;
  }

  public RelationshipManager getRelationshipManager() {
    return m_relationshipManager;
  }

  public void commit() throws UnexpectedException {
    try {
      m_conn.commit();
    } catch (SQLException e) {
      throw new UnexpectedException(-1, "Exception while committing transaction", e);
    }
  }

  public void rollback() throws UnexpectedException {
    try {
      m_conn.rollback();
    } catch (SQLException e) {
      throw new UnexpectedException(-1, "Exception while rolling back transaction", e);
    }
  }

  public boolean isValid() {
    try {
      // Create and execute a dummy statement just to make
      // sure the connection is still alive
      //
      m_conn.createStatement().execute("select 0");
      return true;
    } catch (SQLException e) {
      // Something is wrtong with this connection!
      //
      return false;
    }
  }
}
