/*
 * Created on Nov 5, 2003
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.exceptions;

/** @author Pontus Rydin */
public class AuthorizationException extends UserException {
  static final long serialVersionUID = 2005;

  public AuthorizationException() {
    super();
  }

  public AuthorizationException(String msg) {
    super(msg);
  }

  public AuthorizationException(Object[] msgArgs) {
    super(msgArgs);
  }
}
