/*
 * Created on Oct 25, 2003
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.frontend.text;

import java.io.IOException;
import nu.rydin.kom.exceptions.KOMException;
import nu.rydin.kom.structs.MessageLocator;
import nu.rydin.kom.structs.Name;
import nu.rydin.kom.structs.NameAssociation;
import nu.rydin.kom.structs.UnstoredMessage;

/**
 * @author Pontus Rydin
 * @author Jepson
 */
public interface MessageEditor {
  public UnstoredMessage edit() throws KOMException, InterruptedException, IOException;

  public UnstoredMessage edit(boolean askForSubject)
      throws KOMException, InterruptedException, IOException;

  public UnstoredMessage edit(
      MessageLocator replyTo,
      long recipientId,
      Name recipientName,
      long replyToAuthor,
      Name replyToAuthorName,
      String oldSubject)
      throws KOMException, InterruptedException;

  public NameAssociation getRecipient();

  public void setRecipient(NameAssociation recipient);

  public void setReplyTo(MessageLocator replyTo);
}
