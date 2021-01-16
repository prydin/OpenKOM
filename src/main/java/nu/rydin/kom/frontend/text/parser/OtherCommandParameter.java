/*
 * Created on 2004-aug-19
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.frontend.text.parser;

import java.io.IOException;
import nu.rydin.kom.exceptions.KOMException;
import nu.rydin.kom.frontend.text.Context;

/**
 * OtherCommandParameter represents a parameter that accepts a (shortened) name of another command.
 *
 * <p>The "return type" for an OtherCommandParameter is a Command object.
 *
 * @author Magnus Ihse Bursie
 */
public class OtherCommandParameter extends CommandLineParameter {
  public OtherCommandParameter(final String missingObjectQuestionKey, final boolean isRequired) {
    this(missingObjectQuestionKey, isRequired, null);
  }

  public OtherCommandParameter(final boolean isRequired) {
    this(isRequired, null);
  }

  public OtherCommandParameter(
          final String missingObjectQuestionKey, final boolean isRequired, final DefaultStrategy def) {
    super(missingObjectQuestionKey, isRequired, def);
  }

  public OtherCommandParameter(final boolean isRequired, final DefaultStrategy def) {
    super("parser.parameter.command.ask", isRequired, def);
  }

  @Override
  protected String getUserDescriptionKey() {
    return "parser.parameter.command.description";
  }

  @Override
  protected Match innerMatch(final String matchingPart, final String remainder) {
    final String cooked = cookString(matchingPart);

    if (cooked.length() > 0) {
      // well, this _could_ be a command... check it later
      return new Match(true, matchingPart, remainder, cooked);
    }
    return new Match(false, null, null, null);
  }

  @Override
  public Object resolveFoundObject(final Context context, final Match match)
      throws IOException, InterruptedException, KOMException {
    final Parser parser = context.getParser();
    return parser.getMatchingCommand(context, match.getMatchedString());
  }
}
