/*
 * Created on Sep 21, 2004
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.frontend.text.commands;

import java.io.IOException;
import java.io.PrintWriter;
import nu.rydin.kom.backend.ServerSession;
import nu.rydin.kom.exceptions.KOMException;
import nu.rydin.kom.frontend.text.AbstractCommand;
import nu.rydin.kom.frontend.text.Context;
import nu.rydin.kom.frontend.text.parser.CommandLineParameter;
import nu.rydin.kom.frontend.text.parser.TextNumberParameter;
import nu.rydin.kom.structs.MessageLocator;

/** @author Pontus Rydin */
public class MarkAsUnread extends AbstractCommand {
  public MarkAsUnread(Context context, String fullName, long permissions) {
    super(fullName, new CommandLineParameter[] {new TextNumberParameter(false)}, permissions);
  }

  public void execute(Context context, Object[] parameters)
      throws KOMException, IOException, InterruptedException {
    ServerSession session = context.getSession();
    MessageLocator textNum = (MessageLocator) parameters[0];
    session.markAsUnreadAtLogout(textNum);
    PrintWriter out = context.getOut();
    out.println();
    out.println(context.getMessageFormatter().format("mark.as.unread.confirmation"));
  }
}
