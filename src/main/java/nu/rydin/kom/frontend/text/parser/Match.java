/*
 * Created on Aug 8, 2004
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.frontend.text.parser;

/**
 * @author Magnus Ihse Bursie
 * @author Henrik Schr�der
 */
public final class Match {
  private final boolean m_isMatching;
  private final String m_remainder;
  private final String m_matchedString;
  private final Object m_parsedObject;

  public Match(final boolean isMatching, final String matchedString, final String remainder, final Object parsedObject) {
    m_isMatching = isMatching;
    m_matchedString = matchedString;
    m_remainder = remainder;
    m_parsedObject = parsedObject;
  }

  public boolean isMatching() {
    return m_isMatching;
  }

  public Object getParsedObject() {
    return m_parsedObject;
  }

  public String getRemainder() {
    return m_remainder;
  }

  public String getMatchedString() {
    return m_matchedString;
  }
}
