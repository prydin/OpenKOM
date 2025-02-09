/*
 * Created on Sep 2, 2004
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.frontend.text.editor.simple;

import java.io.IOException;
import java.io.PrintWriter;
import nu.rydin.kom.exceptions.KOMException;
import nu.rydin.kom.frontend.text.AbstractCommand;
import nu.rydin.kom.frontend.text.Context;
import nu.rydin.kom.frontend.text.editor.EditorContext;
import nu.rydin.kom.frontend.text.parser.CommandLineParameter;
import nu.rydin.kom.frontend.text.parser.RawParameter;

/** @author Pontus Rydin */
public class ChangeSubject extends AbstractCommand {
  private static class DefaultStrategy
      implements nu.rydin.kom.frontend.text.parser.DefaultStrategy {
    public String getDefault(Context ctx) {
      return ((EditorContext) ctx).getSubject();
    }
  }

  private static DefaultStrategy s_default = new DefaultStrategy();

  public ChangeSubject(Context context, String fullName, long permissions) {
    super(
        fullName,
        new CommandLineParameter[] {new RawParameter("simple.editor.subject", true, s_default)},
        permissions);
  }

  public void execute(Context context, Object[] parameters)
      throws KOMException, IOException, InterruptedException {
    ((EditorContext) context).setSubject((String) parameters[0]);
  }

  public void printPreamble(PrintWriter out) {
    // Nothing
  }

  public void printPostamble(PrintWriter out) {
    // Nothing
  }
}
