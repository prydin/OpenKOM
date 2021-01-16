/*
 * Created on 2004-aug-19
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.frontend.text.parser;

/** @author Magnus Ihse Bursie */
public class IntegerParameter extends CommandLineParameter {

  public IntegerParameter(final String missingObjectQuestionKey, final boolean isRequired) {
    this(missingObjectQuestionKey, isRequired, null);
  }

  public IntegerParameter(final boolean isRequired) {
    this(isRequired, null);
  }

  public IntegerParameter(
          final String missingObjectQuestionKey, final boolean isRequired, final DefaultStrategy def) {
    super(missingObjectQuestionKey, isRequired, def);
  }

  public IntegerParameter(final boolean isRequired, final DefaultStrategy def) {
    super("parser.parameter.integer.ask", isRequired, def);
  }

  @Override
  protected String getUserDescriptionKey() {
    return "parser.parameter.integer.description";
  }

  @Override
  protected Match innerMatch(final String matchingPart, final String remainder) {
    final String cooked = cookString(matchingPart);
    try {
      final int number = Integer.parseInt(cooked);
      return new Match(true, matchingPart, remainder, number);
    } catch (final NumberFormatException e) {
      return new Match(false, null, null, null);
    }
  }
}
