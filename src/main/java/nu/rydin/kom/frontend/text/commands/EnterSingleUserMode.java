/*
 * Created on Sep 10, 2004
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.frontend.text.commands;

import java.io.IOException;
import java.io.PrintWriter;
import nu.rydin.kom.constants.UserPermissions;
import nu.rydin.kom.exceptions.AuthorizationException;
import nu.rydin.kom.exceptions.KOMException;
import nu.rydin.kom.exceptions.OperationInterruptedException;
import nu.rydin.kom.frontend.text.AbstractCommand;
import nu.rydin.kom.frontend.text.Context;
import nu.rydin.kom.frontend.text.LineEditor;
import nu.rydin.kom.i18n.MessageFormatter;

/** @author Pontus Rydin */
public class EnterSingleUserMode extends AbstractCommand {
  public EnterSingleUserMode(Context context, String fullName, long permissions) {
    super(fullName, AbstractCommand.NO_PARAMETERS, permissions);
  }

  public void checkAccess(Context context) throws AuthorizationException {
    context.getSession().checkRights(UserPermissions.ADMIN);
  }

  public void execute(Context context, Object[] parameters)
      throws KOMException, IOException, InterruptedException {
    MessageFormatter mf = context.getMessageFormatter();
    LineEditor in = context.getIn();
    int choice =
        in.getChoice(
            mf.format("single.user.prompt"),
            new String[] {mf.format("misc.no"), mf.format("misc.yes")},
            0,
            mf.format("nu.rydin.kom.exceptions.InvalidChoiceException.format"));
    if (choice == 0) throw new OperationInterruptedException();
    context.getSession().killAllSessions();
    PrintWriter out = context.getOut();
    out.println(context.getMessageFormatter().format("single.user.confirmation"));
  }
}
