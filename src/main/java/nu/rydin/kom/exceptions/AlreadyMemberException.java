/*
 * Created on Oct 12, 2003
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.exceptions;

/** @author Pontus Rydin */
public class AlreadyMemberException extends UserException {
  static final long serialVersionUID = 2005;

  public AlreadyMemberException() {
    super();
  }

  public AlreadyMemberException(String name) {
    super(name);
  }

  public AlreadyMemberException(Object[] msgArgs) {
    super(msgArgs);
  }
}
