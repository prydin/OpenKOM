/*
 * Created on Aug 19, 2005
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.frontend.text;

import nu.rydin.kom.exceptions.EscapeException;
import nu.rydin.kom.exceptions.EventDeliveredException;
import nu.rydin.kom.exceptions.ImmediateShutdownException;
import nu.rydin.kom.exceptions.KOMRuntimeException;
import nu.rydin.kom.exceptions.OutputInterruptedException;
import nu.rydin.kom.exceptions.UserException;
import nu.rydin.kom.frontend.text.parser.Parser;
import nu.rydin.kom.frontend.text.parser.Parser.ExecutableCommand;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** @author Pontus Rydin */
public class Shell {
  private static final Logger LOG = LogManager.getLogger(Shell.class);
  private final Parser parser;

  public Shell(final Parser parser) {
    this.parser = parser;
  }

  public void run(final Context context, final String prompt) throws EscapeException {
    final LineEditor in = context.getIn();
    final KOMWriter out = context.getOut();
    for (; ; ) {
      try {
        out.print(prompt);
        out.print("> ");
        out.flush();
        String cmdString = null;
        try {
          cmdString =
              in.readLine(
                  "",
                  "",
                  0,
                  LineEditor.FLAG_ECHO
                      | LineEditor.FLAG_RECORD_HISTORY
                      | LineEditor.FLAG_ALLOW_HISTORY);
        } catch (final EventDeliveredException e) {
          // Shouldn't happen
          //
          continue;
        }

        if (cmdString.trim().length() > 0) {
          final ExecutableCommand executableCommand = parser.parseCommandLine(context, cmdString);
          executableCommand.execute(context);
        }
      } catch (final OutputInterruptedException e) {
        out.println();
        out.println(e.formatMessage(context));
        out.println();
      } catch (final UserException e) {
        out.println();
        out.println(e.formatMessage(context));
        out.println();
      } catch (final EscapeException e) {
        // We're outta here...
        //
        throw e;
      } catch (final InterruptedException e) {
        // SOMEONE SET UP US THE BOMB! Let's get out of here!
        // Can happen if connection is lost, or if an admin
        // requested shutdown.
        //
        return;
      } catch (final ImmediateShutdownException e) {
        // SOMEONE SET UP US THE *BIG* BOMB!
        //
        return;
      } catch (final KOMRuntimeException e) {
        out.println(e.formatMessage(context));
        out.println();
        LOG.error(e);
      } catch (final Exception e) {
        e.printStackTrace(out);
        out.println();
        LOG.error(e);
      }
    }
  }
}
