/*
 * Created on Oct 26, 2003
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.backend;

/** @author Pontus Rydin */
public class ReplyStackFrame {
  private final long[] m_replies;
  private final ReplyStackFrame m_next;
  private int m_idx;

  public ReplyStackFrame(final long[] replies, final ReplyStackFrame next) {
    m_replies = replies;
    m_next = next;
    m_idx = 0;
  }

  public long pop() {
    return m_idx < m_replies.length ? m_replies[m_idx++] : -1;
  }

  public long peek() {
    return m_idx < m_replies.length ? m_replies[m_idx] : -1;
  }

  public boolean hasMore() {
    return m_idx < m_replies.length;
  }

  public ReplyStackFrame next() {
    return m_next;
  }
}
