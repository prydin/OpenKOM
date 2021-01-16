/*
 * Created on Aug 8, 2004
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.structs;

/** @author Magnus Ihse Bursie (magnus@ihse.net) */
public class TextNumber {
  private final boolean m_global;

  private final long m_number;

  public TextNumber(final long number) {
    m_number = number;
    m_global = false;
  }

  public TextNumber(final long number, final boolean global) {
    m_number = number;
    m_global = global;
  }

  public long getNumber() {
    return m_number;
  }

  public boolean isGlobal() {
    return m_global;
  }
}
