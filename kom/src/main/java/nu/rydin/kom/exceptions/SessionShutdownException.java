/*
 * Created on Nov 12, 2003
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.exceptions;

import nu.rydin.kom.events.Event;

/**
 * This exception does not signal an error. A session receiving this exception should immedately
 * clean up and terminate itself.
 *
 * @author Pontus Rydin
 */
public class SessionShutdownException extends UrgentEventException {
  static final long serialVersionUID = 2005;

  public SessionShutdownException(Event e, String line, int pos) {
    super(e, line, pos);
  }
}
