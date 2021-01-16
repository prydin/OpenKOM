/*
 * Created on Aug 27, 2004
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.exceptions;

import nu.rydin.kom.frontend.text.Context;

/** @author Pontus Rydin */
public class GenericException extends UserException {
  static final long serialVersionUID = 2005;

  public GenericException() {
    super();
  }

  public GenericException(String message) {
    super(message);
  }

  public String formatMessage(Context context) {
    return this.getMessage();
  }
}
