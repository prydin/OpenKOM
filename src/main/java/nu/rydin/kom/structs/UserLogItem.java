/*
 * Created on Aug 24, 2004
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.structs;

import java.io.Serializable;
import java.sql.Timestamp;

/** @author Pontus Rydin */
public class UserLogItem implements Serializable {
  static final long serialVersionUID = 2005;

  private final long m_userId;

  private String m_userName;

  private Timestamp m_loggedIn;

  private Timestamp m_loggedOut;

  private int m_numPosted;

  private int m_numRead;

  private int m_numChats;

  private int m_numBroadcasts;

  private int m_numCopies;

  public UserLogItem(final long userId) {
    m_userId = userId;
  }

  public UserLogItem(
          final long userId,
          final String userName,
          final Timestamp loggedIn,
          final Timestamp loggedOut,
          final int numPosted,
          final int numRead,
          final int numChats,
          final int numBroadcasts,
          final int numCopies) {
    m_userId = userId;
    m_userName = userName;
    m_loggedIn = loggedIn;
    m_loggedOut = loggedOut;
    m_numPosted = numPosted;
    m_numRead = numRead;
    m_numChats = numChats;
    m_numBroadcasts = numBroadcasts;
    m_numCopies = numCopies;
  }

  public String getUserName() {
    return m_userName;
  }

  public Timestamp getLoggedIn() {
    return m_loggedIn;
  }

  public void setLoggedIn(final Timestamp loggedIn) {
      m_loggedIn = loggedIn;
  }

  public Timestamp getLoggedOut() {
    return m_loggedOut;
  }

  public void setLoggedOut(final Timestamp loggedOut) {
      m_loggedOut = loggedOut;
  }

  public int getNumBroadcasts() {
    return m_numBroadcasts;
  }

  public void incNumBroadcasts() {
    ++m_numBroadcasts;
  }

  public int getNumChats() {
    return m_numChats;
  }

  public void incNumChats() {
    ++m_numChats;
  }

  public int getNumPosted() {
    return m_numPosted;
  }

  public void incNumPosted() {
    ++m_numPosted;
  }

  public int getNumRead() {
    return m_numRead;
  }

  public void incNumRead() {
    ++m_numRead;
  }

  public long getUserId() {
    return m_userId;
  }

  public int getNumCopies() {
    return m_numCopies;
  }

  public void incNumCopies() {
    ++m_numCopies;
  }
}
