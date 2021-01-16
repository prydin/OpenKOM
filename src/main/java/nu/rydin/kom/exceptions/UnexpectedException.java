/*
 * Created on Oct 28, 2003
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.exceptions;

/** @author Pontus Rydin */
public class UnexpectedException extends SystemException {
  static final long serialVersionUID = 2005;

  private final long user;

  public UnexpectedException(final long user, final Throwable t) {
    super(t);
    this.user = user;
  }

  public UnexpectedException(final long user, final String msg, final Throwable t) {
    super(msg, t);
    this.user = user;
  }

  public UnexpectedException(final long user, final String msg) {
    super(msg);
    this.user = user;
  }

  public long getUser() {
    return user;
  }
}
