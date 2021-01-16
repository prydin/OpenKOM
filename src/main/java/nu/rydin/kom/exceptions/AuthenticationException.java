/*
 * Created on Oct 5, 2003
 */
package nu.rydin.kom.exceptions;

/** @author Pontus Rydin */
public class AuthenticationException extends SystemException {
  static final long serialVersionUID = 2005;

  public AuthenticationException() {
    super();
  }

  public AuthenticationException(String msg) {
    super(msg);
  }
}
