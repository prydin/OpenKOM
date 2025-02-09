/*
 * Created on Jun 7, 2004
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org/ for details.
 */
package nu.rydin.kom.events;

/** @author Jepson */
public class MessageDeletedEvent extends Event {
  private long m_confId = -1;

  public MessageDeletedEvent() {
    super();
  }

  public MessageDeletedEvent(long user) {
    super(user);
  }

  public MessageDeletedEvent(long user, long conf) {
    super(user);
    m_confId = conf;
  }

  public long getConference() {
    return m_confId;
  }
}
