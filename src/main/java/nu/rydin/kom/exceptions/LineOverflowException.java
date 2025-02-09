/*
 * Created on Jun 19, 2004
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.exceptions;

/** @author Pontus Rydin */
public class LineOverflowException extends LineEditorException {
  static final long serialVersionUID = 2005;

  public LineOverflowException(String line, int pos) {
    super(line, pos);
  }
}
