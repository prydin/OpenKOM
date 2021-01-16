/*
 * Created on Nov 11, 2003
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.exceptions;

/** @author Pontus Rydin */
public class NotLoggedInException extends UserException {
  static final long serialVersionUID = 2005;

  public NotLoggedInException() {
    super();
  }

  public NotLoggedInException(String fullname) {
    super(new Object[] {fullname});
  }
}
