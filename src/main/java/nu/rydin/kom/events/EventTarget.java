/*
 * Created on Nov 11, 2003
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.events;

/**
 * @author Pontus Rydin
 * @author Jepson
 * @author fuffenz
 */
public interface EventTarget {
  public void onEvent(Event event);

  public void onEvent(ChatMessageEvent event);

  public void onEvent(BroadcastMessageEvent event);

  public void onEvent(ChatAnonymousMessageEvent event);

  public void onEvent(BroadcastAnonymousMessageEvent event);

  public void onEvent(NewMessageEvent event);

  public void onEvent(UserAttendanceEvent event);

  public void onEvent(ReloadUserProfileEvent event);

  public void onEvent(MessageDeletedEvent event);
}
