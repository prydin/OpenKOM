/*
 * Created on Sep 5, 2004
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.frontend.text.parser;

/**
 * LocalTextNumberParameter represents a parameter that only accepts a local textnumber.
 *
 * <p>The "return type" for a LocalTextNumberParameter is a positive Integer.
 *
 * @author Henrik Schr�der
 */
public class LocalTextNumberParameter extends CommandLineParameter {
  public LocalTextNumberParameter(final String missingObjectQuestionKey, final boolean isRequired) {
    this(missingObjectQuestionKey, isRequired, null);
  }

  public LocalTextNumberParameter(final boolean isRequired) {
    this(isRequired, null);
  }

  public LocalTextNumberParameter(
          final String missingObjectQuestionKey, final boolean isRequired, final DefaultStrategy def) {
    super(missingObjectQuestionKey, isRequired, def);
  }

  public LocalTextNumberParameter(final boolean isRequired, final DefaultStrategy def) {
    super("parser.parameter.localtextnumber.ask", isRequired, def);
  }

  @Override
  protected String getUserDescriptionKey() {
    return "parser.parameter.localtextnumber.description";
  }

  @Override
  protected Match innerMatch(final String matchingPart, final String remainder) {
    final String cooked = matchingPart.trim();

    try {
      final int number = Integer.parseInt(cooked);
      if (number > 0) {
        return new Match(true, matchingPart, remainder, number);
      }
    } catch (final NumberFormatException e) {
    }
    return new Match(false, null, null, null);
  }
}
