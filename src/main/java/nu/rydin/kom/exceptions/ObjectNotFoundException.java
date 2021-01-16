/*
 * Created on Oct 5, 2003
 *
 * Distributed under the GPL license. See www.gnu.org for details
 */
package nu.rydin.kom.exceptions;

/** @author Pontus Rydin */
public class ObjectNotFoundException extends UserException {
  static final long serialVersionUID = 2005;

  public ObjectNotFoundException() {
    super();
  }

  // TODO: Why is this done here?
  public ObjectNotFoundException(String msg) {
    super(new Object[] {msg});
  }

  public ObjectNotFoundException(Object[] msgArgs) {
    super(msgArgs);
  }
}
