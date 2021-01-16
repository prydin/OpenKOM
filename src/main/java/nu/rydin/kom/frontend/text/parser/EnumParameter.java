/*
 * Created on 2004-aug-19
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.frontend.text.parser;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import nu.rydin.kom.exceptions.InvalidChoiceException;
import nu.rydin.kom.exceptions.KOMException;
import nu.rydin.kom.exceptions.LineEditingDoneException;
import nu.rydin.kom.exceptions.OperationInterruptedException;
import nu.rydin.kom.frontend.text.Context;
import nu.rydin.kom.frontend.text.LineEditor;
import nu.rydin.kom.i18n.MessageFormatter;

/** @author Magnus Ihse Bursie */
public abstract class EnumParameter extends CommandLineParameter {
  private final List<String> m_alternatives;

  private final String m_headingKey;

  private final String m_promptKey;

  private final String m_legendKeyPrefix;

  private final boolean m_allowPrefixes;

  public EnumParameter(
      final String missingObjectQuestionKey,
      final String headingKey,
      final String promptKey,
      final String[] alternatives,
      final boolean allowPrefixes,
      final String legendKeyPrefix,
      final boolean isRequired) {
    this(
        missingObjectQuestionKey,
        headingKey,
        promptKey,
        alternatives,
        allowPrefixes,
        legendKeyPrefix,
        isRequired,
        null);
  }

  public EnumParameter(
      final String missingObjectQuestionKey,
      final String headingKey,
      final String promptKey,
      final String[] alternatives,
      final boolean allowPrefixes,
      final String legendKeyPrefix,
      final boolean isRequired,
      final DefaultStrategy def) {
    super(missingObjectQuestionKey, isRequired, def);
    m_headingKey = headingKey;
    m_promptKey = promptKey;
    m_legendKeyPrefix = legendKeyPrefix;
    m_allowPrefixes = allowPrefixes;
    m_alternatives = new ArrayList<>(alternatives.length);
    for (final String each : alternatives) {
      if (each != null) {
        m_alternatives.add(each);
      }
    }
  }

  @Override
  protected Match innerMatch(final String matchingPart, final String remainder) {
    final String cooked = cookString(matchingPart);

    if (cooked.length() > 0) {
      // well, this _could_ be a flag... check it later
      return new Match(true, matchingPart, remainder, cooked);
    } else {
      return new Match(false, null, null, null);
    }
  }

  @Override
  public Object resolveFoundObject(final Context context, final Match match)
      throws IOException, InterruptedException, KOMException {
    return Parser.resolveString(
        context,
        match.getMatchedString(),
        m_alternatives,
        m_headingKey,
        m_promptKey,
        m_allowPrefixes,
        m_legendKeyPrefix);
  }

  @Override
  public Match fillInMissingObject(final Context context)
      throws InvalidChoiceException, OperationInterruptedException, IOException,
          InterruptedException {
    final PrintWriter out = context.getOut();
    final LineEditor in = context.getIn();
    final MessageFormatter fmt = context.getMessageFormatter();

    for (; ; ) {
      try {
        out.print(
            fmt.format(m_missingObjectQuestionKey)
                + fmt.format("parser.parameter.enum.prompt.listall"));
        out.flush();
        final String line = in.readLine();
        if (line.length() == 0) {
          throw new OperationInterruptedException();
        }
        if (line.trim().equals("?")) {
          final int selection =
              Parser.askForResolution(
                  context,
                  m_alternatives,
                  m_promptKey,
                  false,
                  m_headingKey,
                  m_allowPrefixes,
                  m_legendKeyPrefix);
          return innerMatch(m_alternatives.get(selection), "");
        } else {
          return innerMatch(line, "");
        }
      } catch (final LineEditingDoneException e) {
        // Nothing we need to do
      }
    }
  }
}
