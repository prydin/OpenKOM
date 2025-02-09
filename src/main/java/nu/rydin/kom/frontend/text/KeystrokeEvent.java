/*
 * Created on Nov 11, 2003
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.frontend.text;

import nu.rydin.kom.events.Event;

/**
 * Event carrying information about a typed character.
 *
 * @author Pontus Rydin
 */
public class KeystrokeEvent extends Event {
  private char m_ch;

  public KeystrokeEvent(char ch) {
    m_ch = ch;
  }

  /** Returns the typed character. */
  public char getChar() {
    return m_ch;
  }
}
