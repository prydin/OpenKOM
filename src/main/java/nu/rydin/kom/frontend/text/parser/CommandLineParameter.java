/*
 * Created on Aug 8, 2004
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.frontend.text.parser;

import java.io.IOException;
import java.io.PrintWriter;
import nu.rydin.kom.exceptions.InvalidChoiceException;
import nu.rydin.kom.exceptions.LineEditingDoneException;
import nu.rydin.kom.exceptions.OperationInterruptedException;
import nu.rydin.kom.exceptions.UnexpectedException;
import nu.rydin.kom.frontend.text.Context;
import nu.rydin.kom.frontend.text.LineEditor;
import nu.rydin.kom.i18n.MessageFormatter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** @author Magnus Ihse Bursie (magnus@ihse.net) */
public abstract class CommandLineParameter extends CommandLinePart {

  private static final Logger LOG = LogManager.getLogger(CommandLineParameter.class);
  protected final String m_missingObjectQuestionKey;
  protected final boolean m_isRequired;
  protected final DefaultStrategy m_default;

  public CommandLineParameter(
      final String missingObjectQuestionKey,
      final boolean isRequired,
      final DefaultStrategy defaultS) {
    m_missingObjectQuestionKey = missingObjectQuestionKey;
    m_isRequired = isRequired;
    m_default = defaultS;
  }

  @Override
  public char getSeparator() {
    return ',';
  }

  @Override
  public Match fillInMissingObject(final Context context)
      throws IOException, InterruptedException, OperationInterruptedException,
          InvalidChoiceException {
    final PrintWriter out = context.getOut();
    final LineEditor in = context.getIn();
    final MessageFormatter fmt = context.getMessageFormatter();
    for (; ; ) {
      try {
        out.println();
        out.print(fmt.format(m_missingObjectQuestionKey));
        out.flush();
        String defaultString = "";
        if (m_default != null) {
          try {
            defaultString = m_default.getDefault(context);
          } catch (final UnexpectedException e) {
            // Not being able to figure out the default is
            // not the end of the world, so we let this one slide.
            // However, we definitely want to log it!
            //
            LOG.warn(e);
          }
        }
        final String line = in.readLine(defaultString);
        if (line.length() == 0) {
          throw new OperationInterruptedException();
        }
        return innerMatch(line, "");
      } catch (final LineEditingDoneException e) {
        // Nothing to do
      }
    }
  }

  @Override
  public boolean isRequired() {
    return m_isRequired;
  }

  public String getUserDescription(final Context context) {
    final MessageFormatter fmt = context.getMessageFormatter();
    if (isRequired()) {
      return "<" + fmt.format(getUserDescriptionKey()) + ">";
    } else {
      return "[" + fmt.format(getUserDescriptionKey()) + "]";
    }
  }

  protected abstract String getUserDescriptionKey();
}
