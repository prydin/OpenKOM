/*
 * Created on Nov 13, 2003
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.exceptions;

/**
 * Thrown when the current command has been interrupted, typically on a user request. Note that
 * java.lang.InterruptedException interrupts the entire session, whereas this exception just
 * interrupts the current command.
 *
 * @author Pontus Rydin
 */
public class OperationInterruptedException extends UserException {
  static final long serialVersionUID = 2005;

  // Just for classification
}
