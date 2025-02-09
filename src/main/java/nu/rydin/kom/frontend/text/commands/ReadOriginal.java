/*
 * Created on Nov 10, 2003
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.frontend.text.commands;

import nu.rydin.kom.exceptions.GenericException;
import nu.rydin.kom.exceptions.KOMException;
import nu.rydin.kom.exceptions.ObjectNotFoundException;
import nu.rydin.kom.frontend.text.AbstractCommand;
import nu.rydin.kom.frontend.text.Context;
import nu.rydin.kom.structs.Envelope;

/** @author Pontus Rydin */
public class ReadOriginal extends AbstractCommand {
  public ReadOriginal(Context context, String fullName, long permissions) {
    super(fullName, AbstractCommand.NO_PARAMETERS, permissions);
  }

  public void execute(Context context, Object[] parameterArray) throws KOMException {
    try {
      // Retreive message
      //
      Envelope env = context.getSession().readOriginalMessage();

      // Print it using the default MessagePrinter
      //
      context.getMessagePrinter().printMessage(context, env);
    } catch (ObjectNotFoundException e) {
      throw new GenericException(context.getMessageFormatter().format("read.message.not.found"));
    }
  }
}
