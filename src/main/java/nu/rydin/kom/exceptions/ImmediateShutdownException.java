/*
 * Created on Sep 9, 2004
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.exceptions;

/** @author Pontus Rydin */
public class ImmediateShutdownException extends KOMRuntimeException {
  static final long serialVersionUID = 2005;

  public ImmediateShutdownException() {
    super();
  }

  public ImmediateShutdownException(String msg) {
    super(msg);
  }

  public ImmediateShutdownException(Throwable t) {
    super(t);
  }

  public ImmediateShutdownException(String msg, Throwable t) {
    super(msg, t);
  }
}
