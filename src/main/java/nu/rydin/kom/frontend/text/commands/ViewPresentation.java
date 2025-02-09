/*
 * Created on Jun 13, 2004
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org/ for details.
 */
package nu.rydin.kom.frontend.text.commands;

import nu.rydin.kom.constants.MessageAttributes;
import nu.rydin.kom.exceptions.GenericException;
import nu.rydin.kom.exceptions.KOMException;
import nu.rydin.kom.exceptions.UnexpectedException;
import nu.rydin.kom.frontend.text.AbstractCommand;
import nu.rydin.kom.frontend.text.Context;
import nu.rydin.kom.frontend.text.parser.CommandLineParameter;
import nu.rydin.kom.frontend.text.parser.NamedObjectParameter;
import nu.rydin.kom.i18n.MessageFormatter;
import nu.rydin.kom.structs.NameAssociation;

/** @author Jepson */
public class ViewPresentation extends AbstractCommand {
  public ViewPresentation(Context context, String fullname, long permissions) {
    super(fullname, new CommandLineParameter[] {new NamedObjectParameter(true)}, permissions);
  }

  public void execute(Context context, Object[] parameterArray) throws KOMException {
    try {
      long objectId = ((NameAssociation) parameterArray[0]).getId();
      context
          .getMessagePrinter()
          .printMessage(
              context,
              context.getSession().readTaggedMessage(MessageAttributes.PRESENTATION, objectId));
    } catch (UnexpectedException e) {
      MessageFormatter formatter = context.getMessageFormatter();
      throw new GenericException(formatter.format("read.message.not.found"));
    }
  }
}
