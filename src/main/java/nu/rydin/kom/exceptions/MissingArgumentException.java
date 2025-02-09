/*
 * Created on Oct 12, 2003
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.exceptions;

/** @author Pontus Rydin */
public class MissingArgumentException extends UserException {
  static final long serialVersionUID = 2005;

  public MissingArgumentException() {
    super();
  }

  public MissingArgumentException(String fullname) {
    super(fullname);
  }
}
