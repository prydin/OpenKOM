/*
 * Created on Nov 12, 2003
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.exceptions;

import nu.rydin.kom.events.Event;

/**
 * This exception does not signal an error. Instead, it is used as a signal that the system is about
 * to be shut down immediately and that the receiving thread should immediatly clean up and
 * terminate.
 *
 * @author Pontus Rydin
 */
public class SystemShutdownException extends UrgentEventException {
  static final long serialVersionUID = 2005;

  public SystemShutdownException(Event e, String line, int pos) {
    super(e, line, pos);
  }
}
