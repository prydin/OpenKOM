/*
 * Created on Jun 13, 2004
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org/ for details.
 */
package nu.rydin.kom.frontend.text.commands;

import java.io.IOException;
import nu.rydin.kom.constants.FileProtection;
import nu.rydin.kom.exceptions.KOMException;
import nu.rydin.kom.frontend.text.AbstractCommand;
import nu.rydin.kom.frontend.text.Context;
import nu.rydin.kom.structs.UnstoredMessage;

/** @author Jepson */
public class WriteNote extends AbstractCommand {
  public WriteNote(Context context, String fullname, long permissions) {
    super(fullname, AbstractCommand.NO_PARAMETERS, permissions);
  }

  public void execute(Context context, Object[] parameterArray)
      throws KOMException, IOException, InterruptedException {
    // FIXME EDITREFACTOR: We should perhaps not use the message editor for editing the note.
    UnstoredMessage msg = context.getMessageEditor().edit(false);
    // FIXME We're missing a message for successful storing of file.
    context
        .getSession()
        .storeFile(
            context.getLoggedInUserId(), ".note.txt", msg.getBody(), FileProtection.ALLOW_READ);
  }
}
