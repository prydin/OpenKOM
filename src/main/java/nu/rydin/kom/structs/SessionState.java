/*
 * Created on Sep 20, 2004
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.structs;

import java.io.Serializable;

/** @author Pontus Rydin */
public class SessionState implements Serializable {
  static final long serialVersionUID = 2005;

  private final short suggestedAction;

  private final long currentConference;

  private final int numUnread;

  public SessionState(final short suggestedAction, final long currentConference, final int numUnread) {
    this.suggestedAction = suggestedAction;
    this.currentConference = currentConference;
    this.numUnread = numUnread;
  }

  public long getCurrentConference() {
    return currentConference;
  }

  public int getNumUnread() {
    return numUnread;
  }

  public short getSuggestedAction() {
    return suggestedAction;
  }
}
