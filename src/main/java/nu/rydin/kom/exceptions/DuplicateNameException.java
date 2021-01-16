/*
 * Created on Oct 5, 2003
 */
package nu.rydin.kom.exceptions;

/** @author Pontus Rydin */
public class DuplicateNameException extends UserException {
  static final long serialVersionUID = 2005;

  public DuplicateNameException() {
    super();
  }

  public DuplicateNameException(String msg) {
    super(msg);
  }
}
