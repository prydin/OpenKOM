/*
 * Created on Aug 8, 2004
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.frontend.text.parser;

import nu.rydin.kom.frontend.text.Context;

/** @author Magnus Ihse Bursie (magnus@ihse.net) */
public class CommandNamePart extends CommandLinePart {
  private final String m_cookedName;
  private final boolean m_isRequired;

  public CommandNamePart(final String cookedName, final boolean isRequired) {
    m_cookedName = cookedName;
    m_isRequired = isRequired;
  }

  @Override
  public char getSeparator() {
    return ' ';
  }

  @Override
  public boolean isRequired() {
    return m_isRequired;
  }

  @Override
  protected Match innerMatch(final String matchingPart, final String remainder) {
    final String cooked = cookString(matchingPart);

    if (cooked.length() == 0) {
      // We were fooled. Probably the user entered something like "(ful) runk".
      // Ignore this part and go get next.
      final Match recursiveMatch = match(remainder);
      return new Match(
          recursiveMatch.isMatching(),
          matchingPart + recursiveMatch.getRemainder(),
          recursiveMatch.getRemainder(),
          recursiveMatch.getParsedObject());
    }

    // If the entered string is the beginning of the complete command,
    // we should still match to allow for shortening of commands.
    if (m_cookedName.indexOf(cooked) == 0) {
      return new Match(true, matchingPart, remainder, null);
    } else {
      return new Match(false, null, null, null);
    }
  }

  /* (non-Javadoc)
   * @see nu.rydin.kom.frontend.text.parser.CommandLinePart#fillInMissingObject(nu.rydin.kom.frontend.text.Context)
   */
  @Override
  public Match fillInMissingObject(final Context context) {
    return new Match(true, m_cookedName, null, null);
  }
}
