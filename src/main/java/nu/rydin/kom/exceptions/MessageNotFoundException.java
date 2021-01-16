/*
 * Created on Jul 14, 2004
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.exceptions;

/** @author Pontus Rydin */
public class MessageNotFoundException extends ObjectNotFoundException {
  static final long serialVersionUID = 2005;

  public MessageNotFoundException() {
    super();
  }

  public MessageNotFoundException(Object[] msgArgs) {
    super(msgArgs);
  }

  public MessageNotFoundException(String msg) {
    super(msg);
  }
}
