/*
 * Created on Oct 5, 2003
 *
 * Distributed under the GPL license. See www.gnu.org for details
 */
package nu.rydin.kom.exceptions;

/** @author Pontus Rydin */
public class AmbiguousNameException extends UserException {
  static final long serialVersionUID = 2005;

  public AmbiguousNameException() {
    super();
  }

  public AmbiguousNameException(String msg) {
    super(msg);
  }
}
