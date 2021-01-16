/*
 * Created on Nov 5, 2003
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.exceptions;

/** @author Pontus Rydin */
public class NoMoreNewsException extends UserException {
  static final long serialVersionUID = 2005;

  public NoMoreNewsException() {
    super();
  }

  public NoMoreNewsException(String fullname) {
    super(fullname);
  }
}
