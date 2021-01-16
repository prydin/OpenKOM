/*
 * Created on Oct 7, 2003
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.structs;

import java.sql.Timestamp;
import java.util.TimeZone;

/** @author Pontus Rydin */
public class UserInfo extends NamedObject {
  public static final int ADDRESS1 = 0x00000001;
  public static final int ADDRESS2 = 0x00000002;
  public static final int ADDRESS3 = 0x00000004;
  public static final int ADDRESS4 = 0x00000008;
  public static final int PHONENO1 = 0x00000010;
  public static final int PHONENO2 = 0x00000020;
  public static final int EMAIL1 = 0x00000040;
  public static final int EMAIL2 = 0x00000080;
  public static final int URL = 0x00000100;
  public static final int CHARSET = 0x00000200;
  public static final int LOCALE = 0x00000400;
  public static final int TIMEZONE = 0x00000800;
  static final long serialVersionUID = 2005;
  private final String m_userid;
  private final long[] m_flags = new long[4];
  private final long m_rights;
  private String m_address1;
  private String m_address2;
  private String m_address3;
  private String m_address4;
  private String m_phoneno1;
  private String m_phoneno2;
  private String m_email1;
  private String m_email2;
  private String m_url;
  private String m_charset;
  private String m_locale;
  private TimeZone m_timeZone;
  private final Timestamp m_created;
  private final Timestamp m_lastlogin;

  private int m_changeMask = 0;

  public UserInfo(
          final long id,
          final Name name,
          final String userid,
          final String keywords,
          final String emailAlias,
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
          final long flags1,
          final long flags2,
          final long flags3,
          final long flags4,
          final long rights,
          final String locale,
          final String timeZone,
          final Timestamp created,
          final Timestamp lastlogin) {
    super(id, name, keywords, emailAlias);
    m_userid = userid;
    m_address1 = address1;
    m_address2 = address2;
    m_address3 = address3;
    m_address4 = address4;
    m_phoneno1 = phoneno1;
    m_phoneno2 = phoneno2;
    m_email1 = email1;
    m_email2 = email2;
    m_url = url;
    m_charset = charset;
    m_flags[0] = flags1;
    m_flags[1] = flags2;
    m_flags[2] = flags3;
    m_flags[2] = flags4;
    m_rights = rights;
    m_locale = locale;
    m_timeZone = timeZone != null ? TimeZone.getTimeZone(timeZone) : TimeZone.getDefault();
    m_created = created;
    m_lastlogin = lastlogin;
  }

  public String getAddress1() {
    return m_address1;
  }

  public void setAddress1(final String string) {
    if (!string.equals(m_address1)) {
      m_changeMask |= UserInfo.ADDRESS1;
      m_address1 = string;
    }
  }

  public String getAddress2() {
    return m_address2;
  }

  public void setAddress2(final String string) {
    if (!string.equals(m_address2)) {
      m_changeMask |= UserInfo.ADDRESS2;
      m_address2 = string;
    }
  }

  public String getAddress3() {
    return m_address3;
  }

  public void setAddress3(final String string) {
    if (!string.equals(m_address3)) {
      m_changeMask |= UserInfo.ADDRESS3;
      m_address3 = string;
    }
  }

  public String getAddress4() {
    return m_address4;
  }

  public void setAddress4(final String string) {
    if (!string.equals(m_address4)) {
      m_changeMask |= UserInfo.ADDRESS4;
      m_address4 = string;
    }
  }

  public String getEmail1() {
    return m_email1;
  }

  public void setEmail1(final String string) {
    if (!string.equals(m_email1)) {
      m_changeMask |= UserInfo.EMAIL1;
      m_email1 = string;
    }
  }

  public String getEmail2() {
    return m_email2;
  }

  public void setEmail2(final String string) {
    if (!string.equals(m_email2)) {
      m_changeMask |= UserInfo.EMAIL2;
      m_email2 = string;
    }
  }

  public long getFlags1() {
    return m_flags[0];
  }

  public long getFlags2() {
    return m_flags[1];
  }

  public long getFlags3() {
    return m_flags[2];
  }

  public long getFlags4() {
    return m_flags[3];
  }

  public long[] getFlags() {
    return m_flags;
  }

  public long getRights() {
    return m_rights;
  }

  public boolean hasRights(final long mask) {
    return (m_rights & mask) == mask;
  }

  public String getLocale() {
    return m_locale;
  }

  public void setLocale(final String string) {
    if (!string.equals(m_locale)) {
      m_changeMask |= UserInfo.LOCALE;
      m_locale = string;
    }
  }

  public TimeZone getTimeZone() {
    return m_timeZone;
  }

  public void setTimeZone(final TimeZone tz) {
    if (!tz.equals(m_timeZone)) {
      m_changeMask |= UserInfo.TIMEZONE;
      m_timeZone = tz;
    }
  }

  public String getPhoneno1() {
    return m_phoneno1;
  }

  public void setPhoneno1(final String string) {
    if (!string.equals(m_phoneno1)) {
      m_changeMask |= UserInfo.PHONENO1;
      m_phoneno1 = string;
    }
  }

  public String getPhoneno2() {
    return m_phoneno2;
  }

  public void setPhoneno2(final String string) {
    if (!string.equals(m_phoneno2)) {
      m_changeMask |= UserInfo.PHONENO2;
      m_phoneno2 = string;
    }
  }

  public String getUrl() {
    return m_url;
  }

  public void setUrl(final String string) {
    if (!string.equals(m_url)) {
      m_changeMask |= UserInfo.URL;
      m_url = string;
    }
  }

  public String getUserid() {
    return m_userid;
  }

  public String getCharset() {
    return m_charset;
  }

  public void setCharset(final String string) {
    if (!string.equals(m_charset)) {
      m_changeMask |= UserInfo.CHARSET;
      m_charset = string;
    }
  }

  public Timestamp getCreated() {
    return m_created;
  }

  public Timestamp getLastlogin() {
    return m_lastlogin;
  }

  public boolean testFlags(final int flagword, final long mask) {
    return (m_flags[flagword] & mask) == mask;
  }
}
