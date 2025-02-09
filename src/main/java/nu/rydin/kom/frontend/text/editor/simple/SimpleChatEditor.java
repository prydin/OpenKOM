/*
 * Created on Jul 11, 2004
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.frontend.text.editor.simple;

import java.io.IOException;
import nu.rydin.kom.constants.UserFlags;
import nu.rydin.kom.exceptions.KOMException;
import nu.rydin.kom.exceptions.UnexpectedException;
import nu.rydin.kom.frontend.text.Context;
import nu.rydin.kom.structs.UnstoredMessage;

/** @author Pontus Rydin */
public class SimpleChatEditor extends AbstractEditor {
  public SimpleChatEditor(Context context) throws IOException, UnexpectedException {
    super("chateditorcommands.xml", context);
  }

  protected void refresh() throws KOMException {
    new ShowSimpleChatMessage(this, "", 0).execute(this, new Object[0]);
  }

  // FIXME EDITREFACTOR: replyTo is completely irrelevant in this context.
  public UnstoredMessage edit() throws KOMException, InterruptedException, IOException {
    this.mainloop((this.getCachedUserInfo().getFlags1() & UserFlags.EMPTY_LINE_FINISHES_CHAT) != 0);
    return new UnstoredMessage("", this.getBuffer().toString());
  }

  protected String getAbortQuestionFormat() {
    return "simple.editor.abortchatquestion";
  }
}
