/*
 * Created on Nov 12, 2003
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.exceptions;

import java.util.List;
import nu.rydin.kom.structs.SessionListItem;

/** @author Pontus Rydin */
public class AlreadyLoggedInException extends SystemException {
  static final long serialVersionUID = 2005;

  public List<SessionListItem> sessions;

  public AlreadyLoggedInException(List<SessionListItem> sessions) {
    super();
    this.sessions = sessions;
  }

  public AlreadyLoggedInException(String name) {
    super(name);
  }

  public List<SessionListItem> getSessions() {
    return this.sessions;
  }
}
