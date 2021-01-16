package nu.rydin.kom.modules.email;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Properties;
import java.util.UUID;
import javax.mail.Address;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Header;
import javax.mail.Message;
import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.internet.InternetAddress;
import nu.rydin.kom.backend.ServerSession;
import nu.rydin.kom.backend.ServerSessionFactory;
import nu.rydin.kom.backend.ServerSessionFactoryImpl;
import nu.rydin.kom.constants.ClientTypes;
import nu.rydin.kom.exceptions.AlreadyLoggedInException;
import nu.rydin.kom.exceptions.AuthenticationException;
import nu.rydin.kom.exceptions.AuthorizationException;
import nu.rydin.kom.exceptions.EmailRecipientNotRecognizedException;
import nu.rydin.kom.exceptions.EmailSenderNotRecognizedException;
import nu.rydin.kom.exceptions.LoginProhibitedException;
import nu.rydin.kom.exceptions.ObjectNotFoundException;
import nu.rydin.kom.exceptions.UnexpectedException;
import nu.rydin.kom.structs.MessageOccurrence;
import nu.rydin.kom.structs.UnstoredMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class POP3Poller extends Thread {
  private static final Logger LOG = LogManager.getLogger(POP3Poller.class);
  private final String host;

  private final int port;

  private final String user;

  private final String password;

  private final String postmaster;

  private final String postmasterPassword;

  private final String deadLetterArea;

  private final int pollDelay;

  private final long systemMessageConf;

  public POP3Poller(
      final String host,
      final int port,
      final String user,
      final String password,
      final String postmaster,
      final String postmasterPassword,
      final int pollDelay,
      final String deadLetterArea,
      final long systemMessageConf) {
    this.host = host;
    this.port = port;
    this.user = user;
    this.password = password;
    this.postmaster = postmaster;
    this.postmasterPassword = postmasterPassword;
    this.pollDelay = pollDelay;
    this.deadLetterArea = deadLetterArea;
    this.systemMessageConf = systemMessageConf;
    setName("Postmaster");
  }

  @Override
  public void run() {
    final Properties props = System.getProperties();

    // Get a Session object
    //
    final Session session = Session.getInstance(props, null);
    session.setDebug(false);

    // Get an OpenKOM session factory
    //
    final ServerSessionFactory ssf;
    ssf = ServerSessionFactoryImpl.getInstance();
    POP3Poller.LOG.info("Polling mailbox " + user + " at host " + host);
    try {
      for (; ; ) {
        boolean commit = false;
        try {
          final Store store = session.getStore("pop3");
          store.connect(host, port, user, password);
          final Folder folder = store.getFolder("INBOX");
          folder.open(Folder.READ_WRITE);
          try {
            final Message[] messages = folder.getMessages();
            final int top = messages.length;
            if (top > 0) {
              ServerSession ss = null;
              try {
                for (final Message each : messages) {
                  final Address[] fromAddresses = each.getFrom();
                  if (fromAddresses == null || fromAddresses.length == 0) {
                    handleDeadLetter(each, "Null sender. Can't handle message", ss);
                    continue;
                  }
                  final Address fromAddress = fromAddresses[0];
                  if (fromAddress == null) {
                    handleDeadLetter(each, "Null sender. Can't handle message", ss);
                    continue;
                  }
                  if (!fromAddress.getType().equals("rfc822")) {
                    handleDeadLetter(
                        each, "Can't handle address of type " + fromAddress.getType(), ss);
                    continue;
                  }

                  final String from = ((InternetAddress) fromAddress).getAddress();

                  // Strip the domain off the receiver
                  //
                  final Address[] recipients = each.getRecipients(RecipientType.TO);
                  if (recipients == null) {
                    handleDeadLetter(
                        each,
                        "Null recipients. Can't handle message from "
                            + Arrays.toString(each.getFrom()),
                        ss);
                    continue;
                  }
                  for (final Address recipient : recipients) {
                    final String to = ((InternetAddress) recipient).getAddress();
                    POP3Poller.LOG.info("Processing email to " + to);

                    // Looking good! Let's send it!
                    //
                    try {
                      // Create server session of we don't already have one
                      //
                      if (ss == null) {
                        ss = ssf.login(postmaster, postmasterPassword, ClientTypes.SOAP, true);
                      }
                      String subject = each.getSubject();
                      if (subject == null) {
                        subject = "";
                      }
                      final MessageOccurrence occ =
                          ss.postIncomingEmail(
                              from,
                              to.substring(0, to.indexOf("@")),
                              each.getSentDate(),
                              each.getReceivedDate(),
                              subject,
                              getContent(each));
                      POP3Poller.LOG.info(
                          "Email from "
                              + from
                              + " accepted and stored as ("
                              + occ.getGlobalId()
                              + ")");
                      each.setFlag(Flags.Flag.DELETED, true);
                    } catch (final EmailRecipientNotRecognizedException e) {
                      handleDeadLetter(
                          each, "Recipient " + to + " not recoginized. Message skipped!", ss);

                      // TODO: Maybe send something back?
                    } catch (final EmailSenderNotRecognizedException e) {
                      handleDeadLetter(
                          each, "Sender " + from + " not recoginized. Message skipped!", ss);
                    } catch (final AuthorizationException e) {
                      handleDeadLetter(
                          each,
                          "Not authorized to store message. Check privileges of postmaster or that sender is member of destination conference!",
                          ss);
                    } catch (final UnexpectedException e) {
                      POP3Poller.LOG.error("Internal error when processing email", e);
                      handleDeadLetter(each, "Internal error, check logs!", ss);
                    }
                  }
                }
              } finally {
                if (ss != null) {
                  ss.close();
                }
              }
            }
            // We made it through, so we can commit changes!
            //
            commit = true;
          } catch (final LoginProhibitedException e) {
            POP3Poller.LOG.error("Login prohibited. Trying again...");
          } catch (final AuthenticationException e) {
            POP3Poller.LOG.error("Cannot log in postmaster. Wrong user/password. Trying again...");
          } catch (final AlreadyLoggedInException e) {
            POP3Poller.LOG.error("Congratulations! This can't happen!");
          } finally {
            folder.close(commit);
            store.close();
          }
        } catch (final MessagingException | IOException e) {
          POP3Poller.LOG.error("Error fetching email", e);
        } catch (final Exception e) {
          POP3Poller.LOG.error("Uncaught exception", e);
        }
        Thread.sleep(pollDelay);
      }
    } catch (final InterruptedException e) {
      POP3Poller.LOG.info("Shutting down");
    }
  }

  protected void handleDeadLetter(
      final Message message, final String reason, final ServerSession ss)
      throws ObjectNotFoundException, AuthorizationException, UnexpectedException, IOException,
          MessagingException {
    // Log warning
    //
    POP3Poller.LOG.warn(reason);

    // Dump message to dead letter area
    //
    final InputStream is = message.getInputStream();
    final PrintStream os;
    File file;
    for (; ; ) {
      final String filename = "dead_letter_" + UUID.randomUUID();
      file = new File(deadLetterArea, filename);

      // Check for the very unlikely condition that we already have
      // a file with the same name
      //
      if (!file.exists()) {
        // Didn't exist. We're good!
        //
        os = new PrintStream(new FileOutputStream(file));
        break;
      }
    }

    // Copy entire email to dead letter file
    //
    // First, dump all headers.
    // TODO: There HAS to be a way to stream out the entire raw message using JavaMail!!!
    //
    for (final Enumeration<Header> en = message.getAllHeaders(); en.hasMoreElements(); ) {
      final Header header = en.nextElement();
      os.print(header.getName());
      os.print(": ");
      os.println(header.getValue());
    }
    os.println();
    final byte[] buffer = new byte[10000];
    int n;
    while ((n = is.read(buffer)) > 0) {
      os.write(buffer, 0, n);
    }
    os.close();

    // Notify operators by posting a message in the system message
    // conference
    //
    final StringBuilder msg = new StringBuilder(5000);
    msg.append("Undeliverable incoming email message.\n\n");
    msg.append("Reason: ");
    msg.append(reason);
    msg.append("\n\nFor the entire message, please refer to local file ");
    msg.append(file.getAbsolutePath());
    msg.append(". \n\nHeaders follow:\n\n");
    for (final Enumeration en = message.getAllHeaders(); en.hasMoreElements(); ) {
      final Header header = (Header) en.nextElement();
      msg.append(header.getName());
      msg.append(": ");
      msg.append(header.getValue());
      msg.append('\n');
    }
    final UnstoredMessage notification = new UnstoredMessage("Undeliverable email", msg.toString());
    ss.storeMessage(systemMessageConf, notification);

    // Getting here means all is well, so we can delete the message!
    //
    message.setFlag(Flags.Flag.DELETED, true);
  }

  protected String getContent(final Part message) throws MessagingException, IOException {
    // Act according to mime type
    //
    if (message.isMimeType("text/plain")) {
      return (String) message.getContent();
    } else if (message.isMimeType("multipart/*")) {
      final Multipart mp = (Multipart) message.getContent();
      final int count = mp.getCount();
      for (int i = 0; i < count; i++) {
        final String content = getContent(mp.getBodyPart(i));
        if (content != null) {
          return content;
        }
      }

      // Getting here means we didn't find anything we understand
      //
      return "<Warning: Empty message or no plain-text part>";
    } else if (message.isMimeType("message/rfc822")) {
      // Nested message
      //
      return getContent((Part) message.getContent());
    } else {
      // No comprende...
      //
      return "<Warning: Didn't find any part of the message I could understand (probably all HTML without plain-text part)>";
    }
  }
}
