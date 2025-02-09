/*
 * Created on Aug 30, 2004
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.frontend.text.commands;

import java.io.IOException;
import nu.rydin.kom.exceptions.KOMException;
import nu.rydin.kom.frontend.text.AbstractCommand;
import nu.rydin.kom.frontend.text.Context;
import nu.rydin.kom.frontend.text.parser.CommandLineParameter;
import nu.rydin.kom.frontend.text.parser.RawParameter;

/** @author Pontus Rydin */
public class DeleteProfile extends AbstractCommand {
  public DeleteProfile(Context context, String fullname, long permissions) {
    super(
        fullname,
        new CommandLineParameter[] {new RawParameter("delete.profile.prompt", true)},
        permissions);
  }

  public void execute(Context context, Object[] parameters)
      throws KOMException, IOException, InterruptedException {
    context
        .getSession()
        .deleteFile(context.getLoggedInUserId(), ".profile." + ((String) parameters[0]) + ".cmd");
  }
}
