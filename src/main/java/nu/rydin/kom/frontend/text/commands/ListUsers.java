/*
 * Created on Oct 5, 2003
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.frontend.text.commands;

import java.io.PrintWriter;
import nu.rydin.kom.backend.data.NameManager;
import nu.rydin.kom.exceptions.KOMException;
import nu.rydin.kom.frontend.text.AbstractCommand;
import nu.rydin.kom.frontend.text.Context;
import nu.rydin.kom.structs.NameAssociation;

/** @author Pontus Rydin */
public class ListUsers extends AbstractCommand {
  public ListUsers(Context context, String fullName, long permissions) {
    super(fullName, AbstractCommand.NO_PARAMETERS, permissions);
  }

  public void execute(Context context, Object[] parameterArray) throws KOMException {
    PrintWriter out = context.getOut();
    NameAssociation[] names =
        context.getSession().getAssociationsForPatternAndKind("%", NameManager.USER_KIND);
    int top = names.length;
    for (int idx = 0; idx < top; ++idx)
      out.println(context.formatObjectName(names[idx].getName(), names[idx].getId()));
  }
}
