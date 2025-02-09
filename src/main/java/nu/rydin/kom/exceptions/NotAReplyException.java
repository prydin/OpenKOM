/*
 * Created on Nov 10, 2003
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.exceptions;

/** @author Pontus Rydin */
public class NotAReplyException extends UserException {
  static final long serialVersionUID = 2005;

  public NotAReplyException() {
    super();
  }

  public NotAReplyException(String fullname) {
    super(fullname);
  }
}
