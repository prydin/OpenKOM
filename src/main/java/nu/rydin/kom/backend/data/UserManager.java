/*
 * Created on Oct 5, 2003
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.backend.data;

import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import nu.rydin.kom.backend.CacheManager;
import nu.rydin.kom.backend.KOMCache;
import nu.rydin.kom.constants.ConferencePermissions;
import nu.rydin.kom.constants.Visibilities;
import nu.rydin.kom.exceptions.AlreadyMemberException;
import nu.rydin.kom.exceptions.AuthenticationException;
import nu.rydin.kom.exceptions.AuthorizationException;
import nu.rydin.kom.exceptions.DuplicateNameException;
import nu.rydin.kom.exceptions.EmailSenderNotRecognizedException;
import nu.rydin.kom.exceptions.ObjectNotFoundException;
import nu.rydin.kom.exceptions.UnexpectedException;
import nu.rydin.kom.structs.Name;
import nu.rydin.kom.structs.UserInfo;
import nu.rydin.kom.utils.PasswordUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** @author Pontus Rydin */
public class UserManager {
  private static final Logger LOG = LogManager.getLogger(UserManager.class);
  private final PreparedStatement m_getIdByLoginStmt;
  private final PreparedStatement m_authenticateStmt;
  private final PreparedStatement m_authenticateOnIdStmt;
  private final PreparedStatement m_addUserStmt;
  private final PreparedStatement m_updateContactInfoStmt;
  private final PreparedStatement m_loadUserStmt;
  private final PreparedStatement m_updateCharsetStmt;
  private final PreparedStatement m_changePasswordStmt;
  private final PreparedStatement m_changeFlagsStmt;
  private final PreparedStatement m_changePermissionsStmt;
  private final PreparedStatement m_updateLastloginStmt;
  private final PreparedStatement m_updateTimeZoneStmt;
  private final PreparedStatement m_getSysopStmt;
  private final PreparedStatement m_countStmt;
  private final PreparedStatement m_matchEmailSenderStmt;

  private final NameManager m_nameManager;

  private final CacheManager m_cacheManager;

  private final Connection m_conn;

  public UserManager(
      final Connection conn, final CacheManager cacheManager, final NameManager nameManager)
      throws SQLException {
    m_conn = conn;
    m_nameManager = nameManager;
    m_cacheManager = cacheManager;
    m_getIdByLoginStmt = conn.prepareStatement("SELECT id FROM users WHERE userid = ?");
    m_authenticateStmt =
        conn.prepareStatement(
            "SELECT u.id, u.pwddigest FROM users u, names n WHERE u.userid = ? AND n.id = u.id");
    m_authenticateOnIdStmt =
        conn.prepareStatement(
            "SELECT u.id, u.pwddigest FROM users u, names n WHERE u.id = ? AND n.id = u.id");
    m_addUserStmt =
        conn.prepareStatement(
            "INSERT INTO users(userid, pwddigest, address1, address2, "
                + "address3, address4, phoneno1, phoneno2, email1, email2, url, charset, id, "
                + "flags1, flags2, flags3, flags4, rights, locale, created) "
                + "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ? ,?, ?, ?)");
    m_updateContactInfoStmt =
        conn.prepareStatement(
            "UPDATE users SET address1 = ?, address2 = ?, address3 = ?, address4 = ?, "
                + "phoneno1 = ?, phoneno2 = ?, email1 = ?, email2 = ?, url = ? WHERE id = ?");
    m_loadUserStmt =
        conn.prepareStatement(
            "SELECT n.fullname, u.userid, n.keywords, u.address1, u.address2, u.address3, u.address4, "
                + "u.phoneno1, u.phoneno2, u.email1, u.email2, u.url, u.charset, u.flags1, u.flags2, "
                + "u.flags3, u.flags4, u.rights, u.locale, u.timezone, u.created, u.lastlogin, n.visibility, n.emailalias "
                + "FROM users u, names n WHERE u.id = ? AND n.id = u.id");
    m_updateCharsetStmt = conn.prepareStatement("UPDATE users SET charset = ? WHERE id = ?");
    m_changePasswordStmt = conn.prepareStatement("UPDATE users SET pwddigest = ? WHERE id = ?");
    m_changeFlagsStmt =
        conn.prepareStatement(
            "UPDATE users SET flags1 = ?, flags2 = ?, flags3 = ?, flags4 = ? WHERE id = ?");
    m_changePermissionsStmt = conn.prepareStatement("UPDATE users SET rights = ? WHERE id = ?");
    m_updateLastloginStmt = conn.prepareStatement("UPDATE users SET lastlogin = ? WHERE id = ?");
    m_updateTimeZoneStmt = conn.prepareStatement("UPDATE users SET timezone = ? WHERE id = ?");
    m_getSysopStmt = conn.prepareStatement("SELECT id FROM users WHERE userid = 'sysop'");
    m_countStmt = conn.prepareStatement("SELECT COUNT(*) from users");
    m_matchEmailSenderStmt =
        conn.prepareStatement("SELECT id FROM users WHERE email1 LIKE ? OR email2 LIKE ?");
  }

  /** Return resources used by this object */
  public void close() throws SQLException {
    if (m_getIdByLoginStmt != null) {
      m_authenticateStmt.close();
    }
    if (m_authenticateStmt != null) {
      m_authenticateStmt.close();
    }
    if (m_authenticateOnIdStmt != null) {
      m_authenticateOnIdStmt.close();
    }
    if (m_addUserStmt != null) {
      m_addUserStmt.close();
    }
    if (m_updateContactInfoStmt != null) {
      m_updateContactInfoStmt.close();
    }
    if (m_loadUserStmt != null) {
      m_loadUserStmt.close();
    }
    if (m_updateCharsetStmt != null) {
      m_updateCharsetStmt.close();
    }
    if (m_changePasswordStmt != null) {
      m_changePasswordStmt.close();
    }
    if (m_changeFlagsStmt != null) {
      m_changeFlagsStmt.close();
    }
    if (m_changePermissionsStmt != null) {
      m_changePermissionsStmt.close();
    }
    if (m_updateLastloginStmt != null) {
      m_updateLastloginStmt.close();
    }
    if (m_updateTimeZoneStmt != null) {
      m_updateTimeZoneStmt.close();
    }
    if (m_getSysopStmt != null) {
      m_getSysopStmt.close();
    }
    if (m_countStmt != null) {
      m_countStmt.close();
    }
    if (m_matchEmailSenderStmt != null) {
      m_matchEmailSenderStmt.close();
    }
  }

  /**
   * Authenticates a user
   *
   * @param userid The user id
   * @param password The password
   * @throws ObjectNotFoundException The user didn't exist
   * @throws NoSuchAlgorithmException
   * @throws SQLException
   */
  public long authenticate(final String userid, final String password)
      throws ObjectNotFoundException, AuthenticationException, NoSuchAlgorithmException,
          SQLException {
    PreparedStatement ps;
    try {
      final long l = Long.parseLong(userid);
      ps = m_authenticateOnIdStmt;
      ps.clearParameters();
      ps.setLong(1, l);
    } catch (final NumberFormatException e) {
      // Not a numeric login.
      ps = m_authenticateStmt;
      ps.clearParameters();
      ps.setString(1, userid);
    }

    try (final ResultSet rs = ps.executeQuery()) {
      if (!rs.next()) {
        throw new ObjectNotFoundException(userid);
      }
      final long id = rs.getLong(1);
      final String candidate = rs.getString(2);

      // Compare to digest in database
      //
      if (!PasswordUtils.compareDigest(password, candidate)) {
        throw new AuthenticationException(userid);
      }
      return id;
    }
  }

  /**
   * Adds a new user
   *
   * @throws DuplicateNameException
   * @throws SQLException
   * @throws NoSuchAlgorithmException
   */
  public long addUser(
      final String userid,
      final String password,
      final String fullname,
      final String keywords,
      final String address1,
      final String address2,
      final String address3,
      final String address4,
      final String phoneno1,
      final String phoneno2,
      final String email1,
      final String email2,
      final String url,
      final String charset,
      final String locale,
      final long flags1,
      final long flags2,
      final long flags3,
      final long flags4,
      final long rights)
      throws DuplicateNameException, SQLException, NoSuchAlgorithmException, UnexpectedException {
    if (userExists(userid)) {
      throw new DuplicateNameException(userid);
    }
    if (m_nameManager.nameExists(fullname)) {
      throw new DuplicateNameException(fullname);
    }
    // First, add the name
    //
    final long nameId =
        m_nameManager.addName(fullname, NameManager.USER_KIND, Visibilities.PUBLIC, keywords);
    final Timestamp now = new Timestamp(System.currentTimeMillis());

    // Now, add the user
    //
    m_addUserStmt.clearParameters();
    m_addUserStmt.setString(1, userid);
    m_addUserStmt.setString(2, PasswordUtils.gerenatePasswordDigest(password));
    m_addUserStmt.setString(3, address1);
    m_addUserStmt.setString(4, address2);
    m_addUserStmt.setString(5, address3);
    m_addUserStmt.setString(6, address4);
    m_addUserStmt.setString(7, phoneno1);
    m_addUserStmt.setString(8, phoneno2);
    m_addUserStmt.setString(9, email1);
    m_addUserStmt.setString(10, email2);
    m_addUserStmt.setString(11, url);
    m_addUserStmt.setString(12, charset);
    m_addUserStmt.setLong(13, nameId);
    m_addUserStmt.setLong(14, flags1);
    m_addUserStmt.setLong(15, flags2);
    m_addUserStmt.setLong(16, flags3);
    m_addUserStmt.setLong(17, flags4);
    m_addUserStmt.setLong(18, rights);
    m_addUserStmt.setString(19, locale);
    m_addUserStmt.setTimestamp(20, now);

    // Lock cache while updating
    //
    m_addUserStmt.executeUpdate();

    // Add a mailbox
    //
    new ConferenceManager(m_conn, m_nameManager).addMailbox(nameId, 0);

    // Make us a member of our mailbox
    //
    try {
      new MembershipManager(m_conn)
          .signup(nameId, nameId, 0, ConferencePermissions.ALL_PERMISSIONS, 0);
      return nameId;
    } catch (final AuthorizationException | AlreadyMemberException | ObjectNotFoundException e) {
      // This can't happen!
      //
      throw new RuntimeException("This can't happen!", e);
    }
  }

  public void changeContactInfo(final UserInfo ui) throws ObjectNotFoundException, SQLException {
    m_updateContactInfoStmt.clearParameters();
    m_updateContactInfoStmt.setString(1, ui.getAddress1());
    m_updateContactInfoStmt.setString(2, ui.getAddress2());
    m_updateContactInfoStmt.setString(3, ui.getAddress3());
    m_updateContactInfoStmt.setString(4, ui.getAddress4());
    m_updateContactInfoStmt.setString(5, ui.getPhoneno1());
    m_updateContactInfoStmt.setString(6, ui.getPhoneno2());
    m_updateContactInfoStmt.setString(7, ui.getEmail1());
    m_updateContactInfoStmt.setString(8, ui.getEmail2());
    m_updateContactInfoStmt.setString(8, ui.getEmail2());
    m_updateContactInfoStmt.setString(9, ui.getUrl());
    m_updateContactInfoStmt.setLong(10, ui.getId());
    if (m_updateContactInfoStmt.executeUpdate() == 0) {
      throw new ObjectNotFoundException();
    }
    m_cacheManager.getUserCache().registerInvalidation(ui.getId());
  }

  public long getUserIdByLogin(final String login) throws ObjectNotFoundException, SQLException {
    m_getIdByLoginStmt.clearParameters();
    m_getIdByLoginStmt.setString(1, login);
    try (final ResultSet rs = m_getIdByLoginStmt.executeQuery()) {
      if (!rs.next()) {
        throw new ObjectNotFoundException(login);
      }
      return rs.getLong(1);
    }
  }

  public boolean userExists(final String login) throws SQLException {
    try {
      getUserIdByLogin(login);
      return true;
    } catch (final ObjectNotFoundException e) {
      return false;
    }
  }

  /**
   * Returns a list of user names based on a search pattern
   *
   * @param pattern The search pattern
   * @throws SQLException
   */
  public Name[] getUserNamesByPattern(final String pattern) throws SQLException {
    return m_nameManager.getNamesByPatternAndKind(pattern, NameManager.USER_KIND);
  }

  /**
   * Returns a list of user ids based on a search pattern
   *
   * @param pattern The search pattern
   * @throws SQLException
   */
  public long[] getUserIdsByPattern(final String pattern) throws SQLException {
    return m_nameManager.getIdsByPatternAndKind(pattern, NameManager.USER_KIND);
  }

  public UserInfo loadUser(final long id) throws ObjectNotFoundException, SQLException {
    // First, try cache
    //
    final KOMCache cache = m_cacheManager.getUserCache();
    final UserInfo cached = (UserInfo) cache.get(id);
    if (cached != null) {
      return cached;
    }

    // Load from database
    //
    m_loadUserStmt.clearParameters();
    m_loadUserStmt.setLong(1, id);
    try (final ResultSet rs = m_loadUserStmt.executeQuery()) {
      if (!rs.next()) {
        throw new ObjectNotFoundException("id=" + id);
      }
      final UserInfo answer =
          new UserInfo(
              id, // id
              new Name(rs.getString(1), rs.getShort(23), NameManager.USER_KIND), // name
              rs.getString(2), // userid
              rs.getString(3), // keywords
              rs.getString(23), // emailalias
              rs.getString(4), // address1
              rs.getString(5), // address2
              rs.getString(6), // address3
              rs.getString(7), // address4
              rs.getString(8), // phoneno1
              rs.getString(9), // phoneno2
              rs.getString(10), // email1
              rs.getString(11), // email2
              rs.getString(12), // url,
              rs.getString(13), // charset
              rs.getLong(14), // flags1,
              rs.getLong(15), // flags2,
              rs.getLong(16), // flags3,
              rs.getLong(17), // flags4,
              rs.getLong(18), // rights
              rs.getString(19), // locale
              rs.getString(20), // time zone
              rs.getTimestamp(21), // created
              rs.getTimestamp(22) // last login
              );
      cache.deferredPut(id, answer);
      return answer;
    }
  }

  /**
   * Updates the character set setting of a user
   *
   * @param userId
   * @param charset
   * @throws ObjectNotFoundException
   * @throws SQLException
   */
  public void updateCharacterset(final long userId, final String charset)
      throws ObjectNotFoundException, SQLException {
    m_updateCharsetStmt.clearParameters();
    m_updateCharsetStmt.setString(1, charset);
    m_updateCharsetStmt.setLong(2, userId);
    final int n = m_updateCharsetStmt.executeUpdate();
    if (n == 0) {
      throw new ObjectNotFoundException();
    }
    m_cacheManager.getUserCache().registerInvalidation(userId);
  }

  /**
   * Changes the password of a user
   *
   * @param userId
   * @param password
   * @throws ObjectNotFoundException
   * @throws SQLException
   */
  public void changePassword(final long userId, final String password)
      throws ObjectNotFoundException, NoSuchAlgorithmException, SQLException {
    m_changePasswordStmt.clearParameters();
    m_changePasswordStmt.setString(1, PasswordUtils.gerenatePasswordDigest(password));
    m_changePasswordStmt.setLong(2, userId);
    if (m_changePasswordStmt.executeUpdate() == 0) {
      throw new ObjectNotFoundException("user id=" + userId);
    }
    m_cacheManager.getUserCache().registerInvalidation(userId);
  }

  /**
   * Changes user flags
   *
   * @param userId The user id to change
   * @param flags The flag words
   * @throws ObjectNotFoundException
   * @throws SQLException
   */
  public void changeFlags(final long userId, final long[] flags)
      throws ObjectNotFoundException, SQLException {
    m_changeFlagsStmt.clearParameters();
    m_changeFlagsStmt.setLong(1, flags[0]);
    m_changeFlagsStmt.setLong(2, flags[1]);
    m_changeFlagsStmt.setLong(3, flags[2]);
    m_changeFlagsStmt.setLong(4, flags[3]);
    m_changeFlagsStmt.setLong(5, userId);
    if (m_changeFlagsStmt.executeUpdate() == 0) {
      throw new ObjectNotFoundException("id=" + userId);
    }
    m_cacheManager.getUserCache().registerInvalidation(userId);
  }

  /**
   * Changes the user permissions
   *
   * @param userId The user to change
   * @param permissions The permissions
   * @throws ObjectNotFoundException
   * @throws SQLException
   */
  public void changePermissions(final long userId, final long permissions)
      throws ObjectNotFoundException, SQLException {
    m_changePermissionsStmt.clearParameters();
    m_changePermissionsStmt.setLong(1, permissions);
    m_changePermissionsStmt.setLong(2, userId);
    if (m_changePermissionsStmt.executeUpdate() == 0) {
      throw new ObjectNotFoundException("id=" + userId);
    }
    m_cacheManager.getUserCache().registerInvalidation(userId);
  }

  /**
   * update last login date
   *
   * @param userId
   * @throws ObjectNotFoundException
   * @throws SQLException
   */
  public void updateLastlogin(final long userId) throws ObjectNotFoundException, SQLException {
    final Timestamp now = new Timestamp(System.currentTimeMillis());

    m_updateLastloginStmt.clearParameters();
    m_updateLastloginStmt.setTimestamp(1, now);
    m_updateLastloginStmt.setLong(2, userId);
    if (m_updateLastloginStmt.executeUpdate() == 0) {
      throw new ObjectNotFoundException("user id=" + userId);
    }
    m_cacheManager.getUserCache().registerInvalidation(userId);
  }

  /**
   * Changes the time zone setting
   *
   * @param userId Id of user to change
   * @param timeZone New tiome zone
   * @throws ObjectNotFoundException
   * @throws SQLException
   */
  public void changeTimezone(final long userId, final String timeZone)
      throws ObjectNotFoundException, SQLException {
    m_updateTimeZoneStmt.clearParameters();
    m_updateTimeZoneStmt.setString(1, timeZone);
    m_updateTimeZoneStmt.setLong(2, userId);
    if (m_updateTimeZoneStmt.executeUpdate() == 0) {
      throw new ObjectNotFoundException("user id=" + userId);
    }
    m_cacheManager.getUserCache().registerInvalidation(userId);
  }

  /** Returns the id of the "root" sysop. */
  public long getSysopId() throws SQLException, ObjectNotFoundException {
    m_getSysopStmt.clearParameters();
    try (final ResultSet rs = m_getSysopStmt.executeQuery()) {
      if (!rs.next()) {
        throw new ObjectNotFoundException("Sysop not found!!!");
      }
      return rs.getLong(1);
    }
  }

  public long countUsers() throws SQLException, UnexpectedException {
    try (final ResultSet rs = m_countStmt.executeQuery()) {
      if (!rs.next()) {
        throw new UnexpectedException(-1, "Empty rowset");
      }
      return rs.getLong(1);
    }
  }

  public long matchEmailSender(final String email)
      throws SQLException, EmailSenderNotRecognizedException {
    final String pattern = '%' + email + '%';
    m_matchEmailSenderStmt.clearParameters();
    m_matchEmailSenderStmt.setString(1, pattern);
    m_matchEmailSenderStmt.setString(2, pattern);
    try (final ResultSet rs = m_matchEmailSenderStmt.executeQuery()) {
      if (!rs.next()) {
        throw new EmailSenderNotRecognizedException(email);
      }
      final long id = rs.getLong(1);
      if (rs.next()) {
        LOG.warn("More than one match for " + email);
      }
      return id;
    }
  }
}
