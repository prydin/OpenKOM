/*
 * Created on Oct 25, 2003
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.structs;

/** @author Pontus Rydin */
public class UnstoredMessage {
  private final String m_subject;

  private final String m_body;

  public UnstoredMessage(final String subject, final String body) {
    m_subject = subject;
    m_body = body;
  }

  public String getSubject() {
    return m_subject;
  }

  public String getBody() {
    return m_body;
  }
}
