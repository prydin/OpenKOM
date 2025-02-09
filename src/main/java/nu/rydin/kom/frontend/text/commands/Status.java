/*
 * Created on Oct 9, 2003
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.frontend.text.commands;

import java.io.IOException;
import java.io.PrintWriter;
import nu.rydin.kom.constants.ConferencePermissions;
import nu.rydin.kom.constants.Visibilities;
import nu.rydin.kom.exceptions.AuthorizationException;
import nu.rydin.kom.exceptions.KOMException;
import nu.rydin.kom.exceptions.ObjectNotFoundException;
import nu.rydin.kom.exceptions.UnexpectedException;
import nu.rydin.kom.frontend.text.AbstractCommand;
import nu.rydin.kom.frontend.text.Context;
import nu.rydin.kom.frontend.text.parser.CommandLineParameter;
import nu.rydin.kom.frontend.text.parser.NamedObjectParameter;
import nu.rydin.kom.i18n.MessageFormatter;
import nu.rydin.kom.structs.ConferenceInfo;
import nu.rydin.kom.structs.NameAssociation;
import nu.rydin.kom.structs.NamedObject;
import nu.rydin.kom.structs.UserInfo;
import nu.rydin.kom.utils.PrintUtils;

/**
 * @author Pontus Rydin
 * @author Jepson
 */
public class Status extends AbstractCommand {
  private final int LABEL_LENGTH = 25;

  public Status(Context context, String fullName, long permissions) {
    super(fullName, new CommandLineParameter[] {new NamedObjectParameter(false)}, permissions);
  }

  public void execute(Context context, Object[] parameters)
      throws KOMException, IOException, InterruptedException {

    // Got null? That means user skipped optional parameter
    // Default to showing status for current user.
    //
    long id;
    if (parameters[0] == null) {
      id = context.getLoggedInUserId();
    } else {
      id = ((NameAssociation) parameters[0]).getId();
    }

    // Call backend
    //
    NamedObject no = context.getSession().getNamedObject(id);
    if (no instanceof ConferenceInfo) this.printConferenceStatus(context, (ConferenceInfo) no);
    else if (no instanceof UserInfo) this.printUserStatus(context, (UserInfo) no);
  }

  protected void printUserStatus(Context context, UserInfo info)
      throws ObjectNotFoundException, UnexpectedException {
    PrintWriter out = context.getOut();
    MessageFormatter formatter = context.getMessageFormatter();
    PrintUtils.printLabelled(
        out, formatter.format("status.user.userid"), LABEL_LENGTH, info.getUserid());
    PrintUtils.printLabelled(
        out, formatter.format("status.user.id"), LABEL_LENGTH, Long.toString(info.getId()));
    PrintUtils.printLabelled(
        out,
        formatter.format("status.user.name"),
        LABEL_LENGTH,
        context.formatObjectName(info.getName(), info.getId()));
    PrintUtils.printLabelledIfDefined(
        out, formatter.format("status.user.keywords"), LABEL_LENGTH, info.getKeywords());
    PrintUtils.printLabelledIfDefined(
        out, formatter.format("status.user.email.alias"), LABEL_LENGTH, info.getEmailAlias());
    PrintUtils.printLabelledIfDefined(
        out, formatter.format("status.user.address1"), LABEL_LENGTH, info.getAddress1());
    PrintUtils.printLabelledIfDefined(
        out, formatter.format("status.user.address2"), LABEL_LENGTH, info.getAddress2());
    PrintUtils.printLabelledIfDefined(
        out, formatter.format("status.user.address3"), LABEL_LENGTH, info.getAddress3());
    PrintUtils.printLabelledIfDefined(
        out, formatter.format("status.user.address4"), LABEL_LENGTH, info.getAddress4());
    PrintUtils.printLabelledIfDefined(
        out, formatter.format("status.user.phone1"), LABEL_LENGTH, info.getPhoneno1());
    PrintUtils.printLabelledIfDefined(
        out, formatter.format("status.user.phone2"), LABEL_LENGTH, info.getPhoneno2());
    PrintUtils.printLabelledIfDefined(
        out, formatter.format("status.user.email1"), LABEL_LENGTH, info.getEmail1());
    PrintUtils.printLabelledIfDefined(
        out, formatter.format("status.user.email2"), LABEL_LENGTH, info.getEmail2());
    PrintUtils.printLabelledIfDefined(
        out, formatter.format("status.user.url"), LABEL_LENGTH, info.getUrl());
    PrintUtils.printLabelledIfDefined(
        out, formatter.format("status.user.locale"), LABEL_LENGTH, info.getLocale().toString());
    PrintUtils.printLabelledIfDefined(
        out, formatter.format("status.user.time.zone"), LABEL_LENGTH, info.getTimeZone().getID());
    PrintUtils.printLabelledIfDefined(
        out,
        formatter.format("status.user.created"),
        LABEL_LENGTH,
        context.smartFormatDate(info.getCreated()));
    PrintUtils.printLabelledIfDefined(
        out,
        formatter.format("status.user.lastlogin"),
        LABEL_LENGTH,
        context.smartFormatDate(info.getLastlogin()));
    out.println();

    // Has the user left a note?
    //
    try {
      String note = context.getSession().readFile(info.getId(), ".note.txt");
      out.println(formatter.format("status.user.note"));
      String[] lines = note.split("\n");
      for (int i = 0; i < lines.length; ++i) {
        out.println(lines[i]);
      }
    } catch (ObjectNotFoundException e) {
      // Not found, nothing to print
    } catch (AuthorizationException e) {
      // No access, nothing to print
    }

    // List memberships
    //
    out.println();
    out.println(formatter.format("status.user.memberof"));
    NameAssociation[] memberships = context.getSession().listMemberships(info.getId());
    int top = memberships.length;
    for (int idx = 0; idx < top; ++idx) {
      out.println(context.formatObjectName(memberships[idx].getName(), memberships[idx].getId()));
      /*
      if (memberships[idx].getId() == info.getId())
      {
          //The mailbox
          out.println(formatter.format("misc.mailboxtitle"));
      }
      else
      {
          out.println(memberships[idx].getName());
      } */
    }
  }

  protected void printConferenceStatus(Context context, ConferenceInfo info)
      throws KOMException, IOException, InterruptedException {
    PrintWriter out = context.getOut();
    MessageFormatter formatter = context.getMessageFormatter();
    String confType =
        (info.getPermissions() & ConferencePermissions.READ_PERMISSION) != 0
            ? formatter.format("conference.public")
            : formatter.format("conference.exclusive");
    if (info.getVisibility() != Visibilities.PUBLIC)
      confType = formatter.format("conference.protected");
    PrintUtils.printLabelled(
        out, formatter.format("status.conference.id"), LABEL_LENGTH, Long.toString(info.getId()));
    PrintUtils.printLabelled(
        out,
        formatter.format("status.conference.name"),
        LABEL_LENGTH,
        context.formatObjectName(info.getName(), info.getId()));
    PrintUtils.printLabelledIfDefined(
        out, formatter.format("status.conference.keywords"), LABEL_LENGTH, info.getKeywords());
    PrintUtils.printLabelledIfDefined(
        out, formatter.format("status.conference.email.alias"), LABEL_LENGTH, info.getEmailAlias());
    PrintUtils.printLabelled(
        out, formatter.format("status.conference.type"), LABEL_LENGTH, confType);
    if (info.getReplyConf() != -1) {
      ConferenceInfo replyConf = context.getSession().getConference(info.getReplyConf());
      PrintUtils.printLabelled(
          out,
          formatter.format("status.conference.commentconference"),
          LABEL_LENGTH,
          context.formatObjectName(replyConf.getName(), replyConf.getId()));
    }
    PrintUtils.printLabelled(
        out,
        formatter.format("status.conference.admin"),
        LABEL_LENGTH,
        context.formatObjectName(
            context.getSession().getName(info.getAdministrator()), info.getAdministrator()));
    PrintUtils.printLabelled(
        out,
        formatter.format("status.conference.messages"),
        LABEL_LENGTH,
        Integer.toString(info.getFirstMessage()) + " - " + Integer.toString(info.getLastMessage()));
    PrintUtils.printLabelledIfDefined(
        out,
        formatter.format("status.user.created"),
        LABEL_LENGTH,
        context.smartFormatDate(info.getCreated()));
    PrintUtils.printLabelledIfDefined(
        out,
        formatter.format("status.user.lasttext"),
        LABEL_LENGTH,
        context.smartFormatDate(info.getLasttext()));
    PrintUtils.printLabelled(
        out,
        formatter.format("status.conference.permissions"),
        LABEL_LENGTH,
        this.formatPermissions(info.getPermissions(), formatter));
    PrintUtils.printLabelled(
        out,
        formatter.format("status.conference.nonmember.permissions"),
        LABEL_LENGTH,
        this.formatPermissions(info.getNonmemberPermissions(), formatter));

    // TODO: fulkod
    ListMembers l = new ListMembers(context, "", this.getRequiredPermissions());
    String[] args = {info.getName().getName()};
    try {
      l.execute(context, args);
    } catch (Exception e) {
      //
    }
  }

  private String formatPermissions(int permissions, MessageFormatter formatter) {
    StringBuffer buffer = new StringBuffer(30);
    if ((permissions & ConferencePermissions.READ_PERMISSION) != 0)
      buffer.append(formatter.format("permission.read"));
    if ((permissions & ConferencePermissions.WRITE_PERMISSION) != 0) {
      if (buffer.length() > 0) buffer.append(", ");
      buffer.append(formatter.format("permission.write"));
    }
    if ((permissions & ConferencePermissions.REPLY_PERMISSION) != 0) {
      if (buffer.length() > 0) buffer.append(", ");
      buffer.append(formatter.format("permission.reply"));
    }
    return buffer.length() > 0 ? buffer.toString() : formatter.format("misc.none.plural");
  }
}
