/*
 * Created on Sep 18, 2004
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.events;

/** @author Pontus Rydin */
public interface ClientEventTarget extends EventTarget {
  public void onEvent(TicketDeliveredEvent event);
}
