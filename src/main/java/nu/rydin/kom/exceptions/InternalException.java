/*
 * Created on Sep 17, 2004
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.exceptions;

/** @author Pontus Rydin */
public class InternalException extends SystemException {
  static final long serialVersionUID = 2005;

  public InternalException() {
    super();
  }

  public InternalException(String msg) {
    super(msg);
  }

  public InternalException(Throwable t) {
    super(t);
  }

  public InternalException(String msg, Throwable t) {
    super(msg, t);
  }
}
