/*
 * Created on Nov 6, 2003
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.exceptions;

/** @author Pontus Rydin */
public class NoMoreMessagesException extends UserException {
  static final long serialVersionUID = 2005;

  public NoMoreMessagesException() {
    super();
  }

  public NoMoreMessagesException(String fullname) {
    super(fullname);
  }
}
