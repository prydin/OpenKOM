/*
 * Created on Aug 19, 2005
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.frontend.text.editor.fullscreen;

import java.io.IOException;
import java.io.PrintWriter;
import nu.rydin.kom.exceptions.AmbiguousPatternException;
import nu.rydin.kom.exceptions.EmptyMessageException;
import nu.rydin.kom.exceptions.EscapeException;
import nu.rydin.kom.exceptions.KOMException;
import nu.rydin.kom.exceptions.KOMRuntimeException;
import nu.rydin.kom.exceptions.UnexpectedException;
import nu.rydin.kom.frontend.text.Context;
import nu.rydin.kom.frontend.text.DisplayController;
import nu.rydin.kom.frontend.text.KOMWriter;
import nu.rydin.kom.frontend.text.KeystrokeTokenizer;
import nu.rydin.kom.frontend.text.KeystrokeTokenizerDefinition;
import nu.rydin.kom.frontend.text.LineEditor;
import nu.rydin.kom.frontend.text.MessageEditor;
import nu.rydin.kom.frontend.text.Shell;
import nu.rydin.kom.frontend.text.constants.Keystrokes;
import nu.rydin.kom.frontend.text.editor.Buffer;
import nu.rydin.kom.frontend.text.parser.Parser;
import nu.rydin.kom.frontend.text.terminal.TerminalController;
import nu.rydin.kom.i18n.MessageFormatter;
import nu.rydin.kom.structs.MessageLocator;
import nu.rydin.kom.structs.Name;
import nu.rydin.kom.structs.UnstoredMessage;
import nu.rydin.kom.utils.PrintUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** @author Pontus Rydin */
public class FullscreenMessageEditor extends FullscreenEditor implements MessageEditor {
  private static final Logger LOG = LogManager.getLogger(FullscreenMessageEditor.class);

  private static final int NUM_HEADER_ROWS = 3;

  private String replyHeader;

  private String recipientHeader;

  private int headerRows;

  public FullscreenMessageEditor(final Context context) throws IOException, UnexpectedException {
    super(context);
  }

  @Override
  public UnstoredMessage edit() throws KOMException, InterruptedException {
    return edit(MessageLocator.NO_MESSAGE, -1, Name.emptyName(), -1, Name.emptyName(), "", true);
  }

  @Override
  public UnstoredMessage edit(final boolean askForSubject)
      throws KOMException, InterruptedException {
    return edit(
        MessageLocator.NO_MESSAGE, -1, Name.emptyName(), -1, Name.emptyName(), "", askForSubject);
  }

  protected void printHeader() {
    final TerminalController tc = getTerminalController();
    final int w = getTerminalSettings().getWidth();
    final DisplayController dc = getDisplayController();
    final PrintWriter out = getOut();
    final MessageFormatter formatter = getMessageFormatter();
    tc.eraseScreen();
    tc.setCursor(0, 0);
    dc.messageHeader();

    if (replyHeader != null) {
      PrintUtils.printLeftJustified(out, replyHeader, w);
      out.println();
    }
    PrintUtils.printLeftJustified(out, recipientHeader, w);
    out.println();
    final String subjLine = formatter.format("simple.editor.subject");
    out.print(subjLine);
    String subject = getSubject();
    if (subject == null) {
      subject = "";
    }
    dc.messageBody();
    PrintUtils.printLeftJustified(out, subject, w - subjLine.length());
    out.println();
    dc.messageHeader();
    PrintUtils.printRepeated(out, '-', subjLine.length() + subject.length());
    out.flush();
  }

  @Override
  protected void refresh() {
    printHeader();
    getOut().println();
    getDisplayController().messageBody();
    super.refresh();
  }

  public UnstoredMessage edit(
      final MessageLocator replyTo,
      final long recipientId,
      final Name recipientName,
      final long replyToAuthor,
      final Name replyToAuthorName,
      final String oldSubject,
      final boolean askForSubject)
      throws KOMException, InterruptedException {
    final TerminalController tc = getTerminalController();
    final DisplayController dc = getDisplayController();
    final PrintWriter out = getOut();
    final LineEditor in = getIn();
    final MessageFormatter formatter = getMessageFormatter();
    try {
      tc.eraseScreen();
      tc.setCursor(0, 0);
      dc.messageHeader();

      // Handle reply
      //
      if (replyTo.isValid()) {
        if (getRecipient().getId() == recipientId) {
          // Simple case: Original text is in same conference
          //
          replyHeader =
              formatter.format(
                  "CompactMessagePrinter.reply.to.same.conference",
                  new Object[] {
                    replyTo.getLocalnum(), formatObjectName(replyToAuthorName, replyToAuthor)
                  });
        } else {
          // Complex case: Original text was in a different conference
          //
          replyHeader =
              formatter.format(
                  "CompactMessagePrinter.reply.to.different.conference",
                  new Object[] {
                    new Long(replyTo.getLocalnum()),
                    formatObjectName(recipientName, recipientId),
                    formatObjectName(replyToAuthorName, replyToAuthor)
                  });
        }
      }

      // Construct receiver
      //
      recipientHeader =
          formatter.format("simple.editor.receiver", formatObjectName(getRecipient()));
      printHeader();

      // Read subject
      //
      tc.up(1);
      dc.input();
      out.flush();
      setSubject(askForSubject ? in.readLine(oldSubject) : oldSubject);

      // Establish viewport
      //
      headerRows = FullscreenMessageEditor.NUM_HEADER_ROWS;
      if (replyTo.isValid()) {
        ++headerRows;
      }
      pushViewport(headerRows, getTerminalSettings().getHeight() - 1);
      refresh();

      // Enter the main editor loop
      //
      final boolean pageBreak = in.getPageBreak();
      in.setPageBreak(false);
      try {
        mainloop();
      } finally {
        in.setPageBreak(pageBreak);
        popViewport();
      }
      return new UnstoredMessage(getSubject(), getBuffer().toString());
    } catch (final IOException e) {
      throw new KOMRuntimeException(formatter.format("error.reading.user.input"), e);
    }
  }

  @Override
  public UnstoredMessage edit(
      final MessageLocator replyTo,
      final long recipientId,
      final Name recipientName,
      final long replyToAuthor,
      final Name replyToAuthorName,
      final String oldSubject)
      throws KOMException, InterruptedException {
    return edit(
        replyTo, recipientId, recipientName, replyToAuthor, replyToAuthorName, oldSubject, true);
  }

  @Override
  protected KeystrokeTokenizer getKeystrokeTokenizer() {
    // Get a copy of the keystroke tokenizer definition
    //
    final KeystrokeTokenizerDefinition kstd =
        getTerminalController().getKeystrokeTokenizer().getDefinition().deepCopy();

    // Add keystrokes specific to us
    //
    try {
      kstd.addPattern(
          "\u000b\u000b",
          Keystrokes.TOKEN_COMMAND | Keystrokes.TOKEN_MOFIDIER_BREAK); // Ctrl-K Ctrl-K
      kstd.addPattern(
          "\u000bK", Keystrokes.TOKEN_COMMAND | Keystrokes.TOKEN_MOFIDIER_BREAK); // Ctrl-K K
      kstd.addPattern(
          "\u000bk", Keystrokes.TOKEN_COMMAND | Keystrokes.TOKEN_MOFIDIER_BREAK); // Ctrl-K k
      kstd.addPattern(
          "\u000b\u0011",
          Keystrokes.TOKEN_QUOTE | Keystrokes.TOKEN_MOFIDIER_BREAK); // Ctrl-K Ctrl-Q
      kstd.addPattern(
          "\u000bQ", Keystrokes.TOKEN_QUOTE | Keystrokes.TOKEN_MOFIDIER_BREAK); // Ctrl-K Q
      kstd.addPattern(
          "\u000bq", Keystrokes.TOKEN_QUOTE | Keystrokes.TOKEN_MOFIDIER_BREAK); // Ctrl-K q
      return kstd.createKeystrokeTokenizer();
    } catch (final AmbiguousPatternException e) {
      LOG.error("Ambigous keystroke pattern", e);
      throw new RuntimeException(e);
    }
  }

  @Override
  protected void unknownToken(final KeystrokeTokenizer.Token token) throws EscapeException {
    final LineEditor in = getIn();
    try {

      switch (token.getKind() & ~Keystrokes.TOKEN_MOFIDIER_BREAK) {
        case Keystrokes.TOKEN_QUOTE:
          quote();
          break;
        case Keystrokes.TOKEN_COMMAND:
          in.popTokenizer();
          try {
            final TerminalController tc = getTerminalController();
            tc.eraseScreen();
            tc.setCursor(0, 0);
            final Shell i = new Shell(Parser.load("fullscreeneditorcommands.xml", this));
            i.run(this, "editor");
            refresh();
          } finally {
            in.pushTokenizer(getKeystrokeTokenizer());
          }
          break;
        default:
          super.unknownToken(token);
      }
    } catch (final IOException e) {
      throw new RuntimeException(e);
    } catch (final KOMException e) {
      throw new RuntimeException(e);
    }
  }

  protected void addQuote(final String line) {
    final Buffer buffer = getBuffer();
    buffer.set(m_cy + m_viewportStart, line + '\n');
    // buffer.setNewline(m_cy + m_viewportStart, true);
    insertLine("");
    refreshCurrentLine();
    moveDown();
    m_tc.eraseToEndOfLine();
  }

  public void quote() throws KOMException, IOException {
    final KOMWriter out = getOut();
    final MessageLocator replyTo = getReplyTo();
    if (!replyTo.isValid()) {
      return;
    }
    try {
      final int middle = (getTerminalSettings().getHeight() - headerRows) / 2;
      pushViewport(0, middle);
      final QuoteEditor quoter = new QuoteEditor(this, replyTo, this);
      revealCursor(false);
      refreshViewport();
      m_tc.setCursor(middle, 0);
      m_tc.reverseVideo();
      m_tc.messageHeader();
      final String divider = "--" + getMessageFormatter().format("fullscreen.editor.quote.help");
      out.print(divider);
      PrintUtils.printRepeated(out, '-', getTerminalSettings().getWidth() - divider.length() - 1);
      m_tc.reset();
      m_tc.messageBody();
      quoter.pushViewport(middle + 5, getTerminalSettings().getHeight() - 1);
      quoter.edit();
      refresh();
    } catch (final InterruptedException e) {
      // Just return
    } catch (final EmptyMessageException e) {
      // Can't quote an emty message
    } finally {
      popViewport();
      refresh();
    }
  }
}
