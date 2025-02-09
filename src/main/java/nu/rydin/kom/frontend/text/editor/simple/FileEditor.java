/*
 * Created on Aug 25, 2004
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.frontend.text.editor.simple;

import java.io.IOException;
import nu.rydin.kom.exceptions.KOMException;
import nu.rydin.kom.exceptions.UnexpectedException;
import nu.rydin.kom.frontend.text.Context;
import nu.rydin.kom.structs.UnstoredMessage;

/** @author Pontus Rydin  */
public class FileEditor extends AbstractEditor {

  public FileEditor(Context context) throws IOException, UnexpectedException {
    super("fileeditorcommands.xml", context);
  }

  protected void refresh() throws KOMException {
    new ShowSimpleFile(this, "", 0).execute(this, new Object[0]);
  }

  // FIXME EDITREFACTOR: replyTo is completely irrelevant in this context.
  public UnstoredMessage edit() throws KOMException, InterruptedException, IOException {
    this.mainloop(false);
    return new UnstoredMessage("", this.getBuffer().toString());
  }

  protected String getAbortQuestionFormat() {
    return "simple.editor.abortfilequestion";
  }
}
