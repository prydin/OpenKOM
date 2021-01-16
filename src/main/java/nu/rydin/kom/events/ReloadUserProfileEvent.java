/*
 * Created on Jun 6, 2004
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.events;

/** @author Pontus Rydin */
public class ReloadUserProfileEvent extends SingleUserEvent {
  public ReloadUserProfileEvent(long targetUser) {
    super(-1, targetUser);
  }

  public void dispatch(EventTarget target) {
    target.onEvent(this);
  }
}
