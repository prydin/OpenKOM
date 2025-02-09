/*
 * Created on Nov 11, 2003
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.frontend.text.commands;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import nu.rydin.kom.backend.ServerSession;
import nu.rydin.kom.constants.Activities;
import nu.rydin.kom.constants.ChatRecipientStatus;
import nu.rydin.kom.exceptions.GenericException;
import nu.rydin.kom.exceptions.InternalException;
import nu.rydin.kom.exceptions.KOMException;
import nu.rydin.kom.frontend.text.AbstractCommand;
import nu.rydin.kom.frontend.text.Context;
import nu.rydin.kom.frontend.text.DisplayController;
import nu.rydin.kom.frontend.text.editor.WordWrapper;
import nu.rydin.kom.frontend.text.editor.simple.AbstractEditor;
import nu.rydin.kom.frontend.text.editor.simple.SimpleChatEditor;
import nu.rydin.kom.frontend.text.parser.ChatRececipientParameter;
import nu.rydin.kom.frontend.text.parser.CommandLineParameter;
import nu.rydin.kom.frontend.text.parser.EllipsisParameter;
import nu.rydin.kom.frontend.text.parser.RawParameter;
import nu.rydin.kom.i18n.MessageFormatter;
import nu.rydin.kom.structs.NameAssociation;

/**
 * @author Pontus Rydin
 * @author Jepson
 */
public class SendChatMessage extends AbstractCommand {
  public SendChatMessage(Context context, String fullName, long permissions) {
    // S�g foo, bar: Du �r b�st!
    super(
        fullName,
        new CommandLineParameter[] {
          new EllipsisParameter("chat.fulhack.raw.ask", true, new ChatRececipientParameter(true)),
          new RawParameter("chat.fulhack.raw.ask", false)
        },
        permissions);
  }

  public void execute(Context context, Object[] parameterArray)
      throws KOMException, IOException, InterruptedException {
    // Set up
    //
    DisplayController dc = context.getDisplayController();
    PrintWriter out = context.getOut();
    ServerSession session = context.getSession();
    MessageFormatter formatter = context.getMessageFormatter();

    // Retrieve array of id's of receivers.
    Object[] nameAssociations = (Object[]) parameterArray[0];
    long[] destinations = new long[nameAssociations.length];
    String recipients = "";
    for (int i = 0; i < nameAssociations.length; i++) {
      long id = ((NameAssociation) nameAssociations[i]).getId();
      if (id == -1) {
        // "*" detected, abort and send a broadcast instead.
        //
        new SendBroadcastChatMessage(context, "", this.getRequiredPermissions())
            .execute(context, new Object[] {parameterArray[1]});
        return;
      }
      if (notExistsIn(id, destinations)) {
        destinations[i] = id;
        // Build string of recipients...
        recipients += ", " + session.getName(id);
      }
    }

    // Retrieve message.
    String message = (String) parameterArray[1];

    // FIXME These checks should *probably* be moved into the ChatRecipientParameter.
    // Check that all users are logged in and can receive messages
    //
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    boolean error = false;
    boolean multicast = false;
    int[] status = session.verifyChatRecipients(destinations);
    int top = status.length;
    for (int idx = 0; idx < top; ++idx) {
      long each = destinations[idx];
      switch (status[idx]) {
        case ChatRecipientStatus.NONEXISTENT:
          pw.println(formatter.format("chat.nonexistent", session.getName(each)));
          error = true;
          break;
        case ChatRecipientStatus.NOT_LOGGED_IN:
          pw.println(formatter.format("chat.not.logged.in", session.getName(each)));
          error = true;
          break;
        case ChatRecipientStatus.REFUSES_MESSAGES:
          pw.println(formatter.format("chat.refuses.messages", session.getName(each)));
          error = true;
          break;
        case ChatRecipientStatus.OK_CONFERENCE:
          multicast = true;
          break;
        case ChatRecipientStatus.OK_USER:
          break;
        default:
          throw new InternalException("Unexpected chat status");
      }
    }
    if (error) {
      throw new GenericException(sw.toString());
    }

    if (message == null) {
      // Display message editor.

      // Print beginning of prompt for message to a list of users
      //
      if (recipients.length() > 2) {
        recipients = recipients.substring(2);
      }
      dc.normal();
      out.println(context.getMessageFormatter().format("chat.saytouser", recipients));

      out.flush();

      // Update state
      //
      session.setActivity(Activities.CHAT, true);
      if (1 == destinations.length) {
        session.setLastObject(destinations[0]);
      }

      // Read message
      //
      AbstractEditor editor = new SimpleChatEditor(context);
      try {
        message = editor.edit().getBody();
      } finally {
        session.restoreState();
      }
    }

    // Empty message? User interrupted
    //
    if (message.length() == 0) {
      return;
    }

    // Send it
    //
    // Can't make it less ugly than this...
    //
    NameAssociation[] refused = session.sendMulticastMessage(destinations, message, multicast);

    // Print refused destinations (if any)
    //
    int top2 = refused.length;
    if (top2 > 0) {
      // Build message
      //
      StringBuffer sb2 = new StringBuffer(200);
      sb2.append(formatter.format("chat.refused"));
      for (int idx = 0; idx < top2; ++idx) {
        sb2.append(refused[idx].getName());
        if (idx < top2 - 1) sb2.append(", ");
      }

      // Wordwrap it!
      //
      out.println();
      WordWrapper ww = context.getWordWrapper(sb2.toString());
      String line = null;
      while ((line = ww.nextLine()) != null) out.println(line);
    }
  }

  // Linear. Ugly. Really does not belong in this class. I don't care - Skrolle. :-)
  //
  private boolean notExistsIn(long id, long[] destinations) {
    for (int i = 0; i < destinations.length; i++) {
      if (destinations[i] == id) return false;
    }
    return true;
  }
}
