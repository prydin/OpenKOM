/*
 * Created on Jul 12, 2004
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.frontend.text.commands;

import nu.rydin.kom.exceptions.KOMException;
import nu.rydin.kom.frontend.text.AbstractCommand;
import nu.rydin.kom.frontend.text.Context;

/** @author Pontus Rydin */
public class WhoAmI extends AbstractCommand {
  public WhoAmI(Context context, String fullName, long permissions) {
    super(fullName, AbstractCommand.NO_PARAMETERS, permissions);
  }

  public void execute(Context context, Object[] parameterArray) throws KOMException {
    context
        .getOut()
        .println(
            context
                .getMessageFormatter()
                .format("who.am.i.message", context.getCachedUserInfo().getName()));
  }
}
