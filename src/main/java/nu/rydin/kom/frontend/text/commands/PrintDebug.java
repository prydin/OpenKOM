/*
 * Created on Oct 26, 2003
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.frontend.text.commands;

import nu.rydin.kom.frontend.text.AbstractCommand;
import nu.rydin.kom.frontend.text.Context;

/** @author Pontus Rydin */
public class PrintDebug extends AbstractCommand {
  public PrintDebug(Context context, String fullName, long permissions) {
    super(fullName, AbstractCommand.NO_PARAMETERS, permissions);
  }

  public void execute(Context context, Object[] parameterArray) {
    context.printDebugInfo();
  }
}
