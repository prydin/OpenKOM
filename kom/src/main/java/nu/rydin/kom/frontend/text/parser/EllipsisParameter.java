/*
 * Created on Aug 25, 2004
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.frontend.text.parser;

import java.io.IOException;
import java.util.ArrayList;
import nu.rydin.kom.exceptions.KOMException;
import nu.rydin.kom.exceptions.KOMRuntimeException;
import nu.rydin.kom.frontend.text.Context;
import nu.rydin.kom.i18n.MessageFormatter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * EllipsisParameter represents a parameter that only accepts a list of it's wrapped type.
 *
 * <p>The "return type" for a EllipsisParameter is an Object[] of the "return type" of the wrapped
 * type.
 *
 * @author Henrik Schroder
 * @author Pontus Rydin
 */
public class EllipsisParameter extends CommandLineParameter {
  private static final Logger LOG = LogManager.getLogger(EllipsisParameter.class);
  private final CommandLineParameter innerParameter;

  private final char separator;

  public EllipsisParameter(
      final String missingObjectQuestionKey,
      final boolean isRequired,
      final CommandLineParameter innerParameter,
      final DefaultStrategy def) {
    this(missingObjectQuestionKey, isRequired, innerParameter, ':', def);
  }

  public EllipsisParameter(
      final String missingObjectQuestionKey,
      final boolean isRequired,
      final CommandLineParameter innerParameter) {
    this(missingObjectQuestionKey, isRequired, innerParameter, ':', null);
  }

  public EllipsisParameter(
      final String missingObjectQuestionKey,
      final boolean isRequired,
      final CommandLineParameter innerParameter,
      final char separator) {
    this(missingObjectQuestionKey, isRequired, innerParameter, separator, null);
  }

  public EllipsisParameter(
      final String missingObjectQuestionKey,
      final boolean isRequired,
      final CommandLineParameter innerParameter,
      final char separator,
      final DefaultStrategy def) {
    super(missingObjectQuestionKey, isRequired, def);
    this.separator = separator;
    this.innerParameter = innerParameter;
    if (!innerParameter.isRequired()) {
      // Uh-oh, this will cause an infinite loop on parsing. Die violently.
      LOG.fatal("Ellipses CANNOT contain optional parameters!");
      throw new KOMRuntimeException("Ellipses CANNOT contain optional parameters!");
    }
  }

  @Override
  protected Match innerMatch(final String matchingPart, final String remainder) {
    String innerCommandLine = matchingPart;

    final ArrayList<Match> innerMatches = new ArrayList<>();
    Match innerMatch;

    // As long as the last innerCommandLine matched, keep trying.
    do {
      innerMatch = innerParameter.match(innerCommandLine);
      if (innerMatch.isMatching()) {
        innerMatches.add(innerMatch);
        innerCommandLine = innerMatch.getRemainder();
      }
    } while (innerMatch.isMatching());

    // If we got any inner matches, return a true match with an array of the
    // inner parsed objects as its parsed object.
    // If not, return a false match.
    if (innerMatches.size() > 0) {
      final Object[] parsedObjects = new Object[innerMatches.size()];
      for (int i = 0; i < innerMatches.size(); i++) {
        parsedObjects[i] = innerMatches.get(i).getParsedObject();
      }
      return new Match(true, matchingPart, remainder, parsedObjects);
    } else {
      return new Match(false, null, null, null);
    }
  }

  @Override
  public Object resolveFoundObject(final Context context, final Match match)
      throws KOMException, IOException, InterruptedException {
    final Object[] parsedObjects = (Object[]) match.getParsedObject();
    final Object[] result = new Object[parsedObjects.length];
    for (int i = 0; i < parsedObjects.length; i++) {
      final String parsed = (String) parsedObjects[i];
      result[i] = innerParameter.resolveFoundObject(context, new Match(true, parsed, "", parsed));
    }
    return result;
  }

  @Override
  public String getUserDescription(final Context context) {
    final MessageFormatter fmt = context.getMessageFormatter();
    final String result =
        fmt.format(innerParameter.getUserDescriptionKey()) + innerParameter.getSeparator() + " ...";
    if (isRequired()) {
      return "<" + result + ">";
    } else {
      return "[" + result + "]";
    }
  }

  @Override
  protected String getUserDescriptionKey() {
    return "parser.parameter.ellipsis.description";
  }

  @Override
  public char getSeparator() {
    return separator;
  }
}
