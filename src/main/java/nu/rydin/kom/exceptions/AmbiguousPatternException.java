/*
 * Created on Jul 9, 2004
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.exceptions;

/** @author Pontus Rydin */
public class AmbiguousPatternException extends Exception {
  static final long serialVersionUID = 2005;

  public AmbiguousPatternException() {
    super();
  }

  public AmbiguousPatternException(String message) {
    super(message);
  }
}
