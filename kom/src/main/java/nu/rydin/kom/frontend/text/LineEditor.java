/*
 * Created on Nov 10, 2003
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.frontend.text;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Stack;
import nu.rydin.kom.backend.EventSource;
import nu.rydin.kom.backend.ServerSession;
import nu.rydin.kom.constants.UserFlags;
import nu.rydin.kom.events.Event;
import nu.rydin.kom.events.EventTarget;
import nu.rydin.kom.events.SessionShutdownEvent;
import nu.rydin.kom.exceptions.AmbiguousPatternException;
import nu.rydin.kom.exceptions.EventDeliveredException;
import nu.rydin.kom.exceptions.ImmediateShutdownException;
import nu.rydin.kom.exceptions.LineEditingDoneException;
import nu.rydin.kom.exceptions.LineEditingInterruptedException;
import nu.rydin.kom.exceptions.LineOverflowException;
import nu.rydin.kom.exceptions.LineUnderflowException;
import nu.rydin.kom.exceptions.OperationInterruptedException;
import nu.rydin.kom.exceptions.OutputInterruptedException;
import nu.rydin.kom.exceptions.StopCharException;
import nu.rydin.kom.frontend.text.constants.Keystrokes;
import nu.rydin.kom.i18n.MessageFormatter;
import nu.rydin.kom.utils.PrintUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author Pontus Rydin
 * @author Jepson
 */
public class LineEditor implements NewlineListener {
  public static final int TABSIZE = 8;
  public static final int FLAG_STOP_ON_EVENT = 0x01;
  public static final int FLAG_ECHO = 0x02;
  public static final int FLAG_STOP_ON_BOL = 0x04;
  public static final int FLAG_STOP_ON_EOL = 0x08;
  public static final int FLAG_STOP_ONLY_WHEN_EMPTY = 0x10;
  public static final int FLAG_RECORD_HISTORY = 0x20;
  public static final int FLAG_ALLOW_HISTORY = 0x40;
  public static final int FLAG_DONT_REFRESH = 0x80;
  public static final int FLAG_TREAT_SPACE_AS_NEWLINE = 0x100;
  private static final Logger LOG = LogManager.getLogger(LineEditor.class);
  private static final char BELL = 7;
  private static final char BS = 8;
  private static final char TAB = 9;
  private static final char SPACE = 32;

  /**
   * Maximum number of keystrokes on one line before session shutdown. For protection against
   * DOS-attacks.
   */
  private static final int SHUTDOWN_LIMIT = 10000;

  private static final KeystrokeTokenizerDefinition s_tokenizerDef;

  static {
    try {
      s_tokenizerDef =
          new KeystrokeTokenizerDefinition(
              new String[] {
                "\n", // Newline
                "\r", // CR
                "\u0008", // BS
                "\u007f", // DEL
                "\u0001", // Ctrl-A
                "\u0005", // Ctrl-E
                "\u0015", // Ctrl-U
                "\u0018", // Ctrl-X
                "\u0017", // Ctrl-W
                "\u001b[A", // <esc> [ A
                "\u001b[B", // <esc> [ B
                "\u001b[C", // <esc> [ C
                "\u0006", // Ctrl-F
                "\u001b[D", // <esc> [ D
                "\u0002", // Ctrl-B
                "\u0003", // Ctrl-C
                "\u0010", // Ctrl-P
                "\u000e"
              }, // Ctrl-N
              new int[] {
                Keystrokes.TOKEN_CR, // Newline
                Keystrokes.TOKEN_CR, // CR
                Keystrokes.TOKEN_BS, // BS
                Keystrokes.TOKEN_BS, // DEL
                Keystrokes.TOKEN_BOL, // Ctrl-A
                Keystrokes.TOKEN_EOL, // Ctrl-E
                Keystrokes.TOKEN_CLEAR_LINE, // Ctrl-U
                Keystrokes.TOKEN_CLEAR_LINE, // Ctrl-X
                Keystrokes.TOKEN_DELETE_WORD, // Ctrl-W
                Keystrokes.TOKEN_UP, // <esc> [ A
                Keystrokes.TOKEN_DOWN, // <esc> [ B
                Keystrokes.TOKEN_RIGHT, // <esc> [ C
                Keystrokes.TOKEN_RIGHT, // Ctrl-F
                Keystrokes.TOKEN_LEFT, // <esc> [ D
                Keystrokes.TOKEN_LEFT, // Ctrl-B
                Keystrokes.TOKEN_ABORT, // Ctrl-C
                Keystrokes.TOKEN_PREV, // Ctrl-P
                Keystrokes.TOKEN_NEXT
              }); // Ctrl-N
    } catch (final AmbiguousPatternException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private final Stack<KeystrokeTokenizer> m_tokenizerStack = new Stack<>();

  private final ReaderProxy m_in;

  private final InputStream m_inStream;

  private final KOMWriter m_out;

  private final EventTarget m_target;
  private final MessageFormatter m_formatter;
  private final String m_morePrompt;
  /**
   * Incoming events. Low priority events that can't be handled immediately are moved to the low
   * priority queue.
   */
  private final LinkedList<Event> m_eventQueue = new LinkedList<>();

  private final TerminalSettingsProvider m_tsProvider;
  /** Command history */
  private final ArrayList<String> m_history = new ArrayList<>();

  private ServerSession m_session;
  private boolean m_dontCount = false;
  private int m_lineCount = 0;
  private boolean m_bypass = false;
  private long m_lastKeystrokeTime = System.currentTimeMillis();
  /** Thread polling for system events, such as new messages and chat messages. */
  private EventPoller m_eventPoller;
  /** Thread polling for keystrokes. */
  private KeystrokePoller m_keystrokePoller;

  private KeystrokeListener m_keystrokeListener;

  public LineEditor(
      final InputStream in,
      final KOMWriter out,
      final EventTarget target,
      final TerminalSettingsProvider tsProvider,
      final ServerSession session,
      final MessageFormatter formatter,
      final String charset)
      throws UnsupportedEncodingException {
    m_in = new ReaderProxy(new InputStreamReader(in, charset));
    m_inStream = in;
    m_out = out;
    m_target = target;
    m_tsProvider = tsProvider;
    m_formatter = formatter;

    // Create "more" prompt
    //
    m_morePrompt = m_formatter.format("misc.more");

    // Create tokenizer
    //
    m_tokenizerStack.push(LineEditor.s_tokenizerDef.createKeystrokeTokenizer());

    // Start pollers
    //
    if (session != null) {
      setSession(session);
    }
  }

  /** Starts the keystroke poller. No input can be read until this method is called. */
  public void start() {
    m_keystrokePoller = new KeystrokePoller();
    m_keystrokePoller.start();
  }

  public void pushTokenizer(final KeystrokeTokenizer tokenizer) {
    m_tokenizerStack.push(tokenizer);
  }

  public void popTokenizer() {
    m_tokenizerStack.pop();
  }

  public void setSession(final ServerSession session) {
    if (m_session != null) {
      throw new IllegalStateException("Already have a session!");
    }
    m_session = session;
    m_keystrokePoller.setSession(session);
    m_eventPoller = new EventPoller();
    m_eventPoller.setSession(session);

    // Set priority somewhere between the normal and the maximum
    //
    m_eventPoller.setPriority((Thread.NORM_PRIORITY + Thread.MAX_PRIORITY) / 2);
    m_eventPoller.start();
  }

  public boolean getPageBreak() {
    return !m_bypass;
  }

  public void setPageBreak(final boolean flag) {
    m_bypass = !flag;
  }

  public void setKeystrokeListener(final KeystrokeListener listener) {
    m_keystrokeListener = listener;
  }

  public String readLineStopOnEvent()
      throws IOException, InterruptedException, OperationInterruptedException,
          EventDeliveredException, LineEditingDoneException {
    try {
      return innerReadLine(
          null,
          null,
          0,
          LineEditor.FLAG_STOP_ON_EVENT
              | LineEditor.FLAG_ECHO
              | LineEditor.FLAG_STOP_ONLY_WHEN_EMPTY);
    } catch (final LineOverflowException | LineUnderflowException | StopCharException e) {
      throw new RuntimeException("This should not happen!", e);
    }
  }

  public String readLineStopOnEvent(final String defaultString)
      throws IOException, InterruptedException, OperationInterruptedException,
          EventDeliveredException, LineEditingDoneException {
    try {
      return innerReadLine(
          defaultString,
          null,
          0,
          LineEditor.FLAG_STOP_ON_EVENT
              | LineEditor.FLAG_ECHO
              | LineEditor.FLAG_STOP_ONLY_WHEN_EMPTY);
    } catch (final LineOverflowException | LineUnderflowException | StopCharException e) {
      throw new RuntimeException("This should not happen!", e);
    }
  }

  public void setCharset(final String charset) throws UnsupportedEncodingException {
    m_in.setReader(new InputStreamReader(m_inStream, charset));
  }

  public String readPassword()
      throws IOException, InterruptedException, OperationInterruptedException,
          LineEditingDoneException {
    try {
      return innerReadLine(null, null, 0, 0);
    } catch (final EventDeliveredException
        | LineOverflowException
        | LineUnderflowException
        | StopCharException e) {
      // HUH??!?!? We asked NOT TO have events interrupt us, but
      // still here we are. Something is broken!
      //
      throw new RuntimeException("This should not happen!", e);
    }
  }

  public String readLine()
      throws IOException, InterruptedException, OperationInterruptedException,
          LineEditingDoneException {
    return readLine(null);
  }

  public String readLine(final String defaultString)
      throws IOException, InterruptedException, OperationInterruptedException,
          LineEditingDoneException {
    try {
      return innerReadLine(defaultString, null, 0, LineEditor.FLAG_ECHO);
    } catch (final EventDeliveredException
        | LineOverflowException
        | LineUnderflowException
        | StopCharException e) {
      // HUH??!?!? We asked NOT TO have events interrupt us, but
      // still here we are. Something is broken!
      //
      throw new RuntimeException("This should not happen!", e);
    }
  }

  public String readLine(
      final String defaultString, final String stopChars, final int length, final int flags)
      throws LineOverflowException, StopCharException, LineUnderflowException, IOException,
          InterruptedException, OperationInterruptedException, EventDeliveredException,
          LineEditingDoneException {
    return innerReadLine(defaultString, stopChars, length, flags);
  }

  public int getChoice(
      final String prompt,
      final String[] choices,
      final int defaultChoice,
      final String errorString)
      throws IOException, InterruptedException, OperationInterruptedException,
          LineEditingDoneException {
    final String defaultString = defaultChoice != -1 ? choices[defaultChoice] : null;
    final int top = choices.length;
    for (; ; ) {
      m_out.print(prompt);
      m_out.flush();
      final String tmp = readLine(defaultString).toUpperCase();
      if (tmp.length() > 0) {
        for (int idx = 0; idx < top; ++idx) {
          if (choices[idx].toUpperCase().startsWith(tmp)) {
            return idx;
          }
        }
      }

      // Invalid response
      //
      m_out.println();
      m_out.println(errorString);
      m_out.println();
    }
  }

  public boolean getYesNo(final String prompt, final char[] yesChars, final char[] noChars)
      throws IOException, InterruptedException {
    boolean result = false;

    // Print prompt
    m_out.print(prompt);
    m_out.flush();

    // Concatenate yesChars and noChars
    final char[] tmp = new char[yesChars.length + noChars.length];
    System.arraycopy(yesChars, 0, tmp, 0, yesChars.length);
    System.arraycopy(noChars, 0, tmp, yesChars.length, noChars.length);

    final char ch = waitForCharacter(tmp);
    for (final char yesChar : yesChars) {
      if (yesChar == ch) {
        // We got a yes character!
        result = true;
        break;
      }
    }
    // No need to check for a no character since waitForChar won't return
    // on any other characters but the specified ones.

    // Erase the prompt
    //
    m_out.print('\r');
    PrintUtils.printRepeated(m_out, ' ', prompt.length());
    m_out.print('\r');
    m_out.flush();
    return result;
  }

  /**
   * Displays a prompt and waits for user input. The user may choose to abort (noChars), continue
   * another page (yesChars) or continue all (goAheadChars).
   *
   * @param prompt
   * @return true = continue, false = abort
   * @throws IOException
   * @throws InterruptedException
   */
  public boolean getMore(final String prompt) throws IOException, InterruptedException {
    boolean result = false;

    // Print prompt
    m_out.print(prompt);
    m_out.flush();

    String yes = m_formatter.format("misc.more.yeschars");
    String no = m_formatter.format("misc.more.nochars");
    final String goAhead = m_formatter.format("misc.more.goaheadchars");

    // This is a quick fix, since the only alternative is completely rewriting
    // getMore() and a few other methods. The current architecture was never designed
    // for changing core constants (such as misc.more.yeschars) runtime.
    //
    boolean addSpaceAsYesChar = false;
    try {
      if ((m_session.getUser(m_session.getLoggedInUserId()).getFlags1()
              & UserFlags.TREAT_SPACE_AS_NEWLINE)
          != 0) {
        addSpaceAsYesChar = true;
      }
    } catch (final Exception e) {
      // Quietly throw the exception away, there's nothing we can do anyway.
    }

    if (addSpaceAsYesChar) {
      // Space might be a no character by default, if so we need to drop it from that
      // string as well as adding it to the yes characters.
      //
      if (!yes.contains(" ")) {
        yes += " ";
      }

      final int idx = no.indexOf(' ');
      if (-1 != idx) {
        final StringBuilder tmpNo = new StringBuilder(no);
        no = tmpNo.deleteCharAt(idx).toString();
      }
    }

    final String all = yes + no + goAhead;

    final char answer = waitForCharacter(all.toCharArray());
    if (yes.indexOf(answer) != -1) {
      result = true;
    } else if (goAhead.indexOf(answer) != -1) {
      result = true;
      // Disable page break for now. It will be enabled before the next command is executed
      // (see nu.rydin.kom.frontend.text.parser.Parser.ExecutableCommand.execute(Context))
      setPageBreak(false);
    }

    // No need to check for a no character since waitForChar won't
    // return
    // on any other characters but the specified ones.

    // Erase the prompt
    //
    m_out.print('\r');
    PrintUtils.printRepeated(m_out, ' ', prompt.length());
    m_out.print('\r');
    m_out.flush();
    return result;
  }

  /**
   * Waits for the user to input any of the given characters and then returns it, or throws
   * InputInterruptedException on ctrl-c.
   */
  public char waitForCharacter(final char[] allowedCharacters)
      throws IOException, InterruptedException {
    // Loop until user inputs an expected character or input is interrupted
    //
    while (true) {
      try {
        final char ch = innerReadCharacter(0);
        for (final char allowedCharacter : allowedCharacters) {
          if (ch == allowedCharacter) {
            return ch;
          }
        }
      } catch (final EventDeliveredException e) {
        // HUH??!?!? We asked NOT TO have events interrupt us, but
        // still here we are. Something is broken!
        //
        throw new RuntimeException("This should not happen!", e);
      }
    }
  }

  /**
   * Returns the next character from the input buffer or waits for a keystroke.
   *
   * @param flags The flags
   */
  public char readCharacter(final int flags)
      throws IOException, InterruptedException, EventDeliveredException {
    return innerReadCharacter(flags);
  }

  /**
   * Reads a single character from the user without echoing back. Throws InputInterruptedException
   * on ctrl-c.
   *
   * @param flags If FLAG_STOP_ON_EVENT is true, the method will throw event exception on event.
   * @return The character read from user.
   */
  protected char innerReadCharacter(final int flags)
      throws IOException, InterruptedException, EventDeliveredException {
    while (true) {
      // Read next event from queue
      //
      final Event ev = getNextEvent();

      // Not a keystroke? Handle event
      //
      if (!(ev instanceof KeystrokeEvent)) {
        // IOException while reading user input? Pass it on!
        //
        if (ev instanceof IOExceptionEvent) {
          throw ((IOExceptionEvent) ev).getException();
        }

        // Session shutdown? Get us out of here immediately!
        //
        if (ev instanceof SessionShutdownEvent) {
          throw new InterruptedException();
        }

        // Dispatch event
        //
        ev.dispatch(m_target);
        if ((flags & LineEditor.FLAG_STOP_ON_EVENT) != 0) {
          throw new EventDeliveredException(ev, "", -1);
        }

        // This is not the event we're looking for. Move along.
        //
        continue;
      }

      // Getting here means we have a Keystroke event, let's handle it!
      //
      m_lineCount = 0;
      final char ch = ((KeystrokeEvent) ev).getChar();
      m_lastKeystrokeTime = System.currentTimeMillis();
      if (m_keystrokeListener != null) {
        m_keystrokeListener.keystroke(ch);
      }
      return ch;
    }
  }

  protected String innerReadLine(
      final String defaultString, final String stopChars, final int maxLength, final int flags)
      throws EventDeliveredException, StopCharException, LineOverflowException,
          LineUnderflowException, IOException, OperationInterruptedException, InterruptedException,
          LineEditingDoneException {
    return editLine(defaultString, stopChars, maxLength, -1, flags);
  }

  public String editLine(
      final String defaultString, final int maxLength, final int pos, final int flags)
      throws EventDeliveredException, StopCharException, LineOverflowException,
          LineUnderflowException, IOException, OperationInterruptedException, InterruptedException,
          LineEditingDoneException {
    return editLine(defaultString, "", maxLength, pos, flags);
  }

  public KeystrokeTokenizer.Token readToken(final int flags)
      throws EventDeliveredException, InterruptedException, IOException {
    final KeystrokeTokenizer tokenizer = m_tokenizerStack.peek();
    KeystrokeTokenizer.Token token = null;
    while (token == null) {
      char ch;
      for (; ; ) {
        try {
          ch = innerReadCharacter(flags);
          break;
        } catch (final EventDeliveredException e) {
          if ((flags & LineEditor.FLAG_STOP_ONLY_WHEN_EMPTY) == 0) {
            throw e;
          }
        }
      }
      token = tokenizer.feedCharacter(ch);
    }
    return token;
  }

  public String editLine(
      String defaultString,
      final String stopChars,
      final int maxLength,
      final int pos,
      final int flags)
      throws EventDeliveredException, StopCharException, LineOverflowException,
          LineUnderflowException, IOException, OperationInterruptedException, InterruptedException,
          LineEditingDoneException {
    int historyPos = m_history.size();
    final StringBuffer buffer = new StringBuffer(500);
    int cursorpos = 0;
    boolean newLine = false;

    // Did we get a default string?
    //
    if (defaultString != null) {
      // Strip newlines
      //

      if (defaultString.endsWith("\n")) {
        defaultString = defaultString.substring(0, defaultString.length() - 1);
        newLine = true;
      }
      buffer.append(defaultString);
      m_out.print(defaultString);
      cursorpos = defaultString.length();
      if (pos != -1) {
        for (; cursorpos > pos; --cursorpos) {
          m_out.print(LineEditor.BS);
        }
      }
      m_out.flush();
    }

    int count = 0;
    boolean editing = true;
    while (editing) {
      // Getting here means that we have a KeyStrokeEvent. Handle it!
      //
      KeystrokeTokenizer.Token token = null;
      char ch = 0;
      while (token == null) {
        for (; ; ) {
          try {
            ch = innerReadCharacter(flags);
            if (++count > LineEditor.SHUTDOWN_LIMIT) {
              m_out.println();
              m_out.println("Suspected DOS-attack!!! Shutting down!");
              throw new InterruptedException();
            }
            break;
          } catch (final EventDeliveredException e) {
            if ((flags & LineEditor.FLAG_STOP_ONLY_WHEN_EMPTY) == 0) {
              throw e;
            } else {
              if (buffer.length() == 0) {
                throw e;
              }

              // Otherwise, skip and hope it's queued
              //
            }
          }
        }
        token = m_tokenizerStack.peek().feedCharacter(ch);
      }
      int kind = token.getKind();
      final boolean exit = (kind & Keystrokes.TOKEN_MOFIDIER_BREAK) != 0;
      kind &= ~Keystrokes.TOKEN_MOFIDIER_BREAK; // Strip break bit

      // Break?
      //
      if (exit) {
        final String line = buffer.toString();
        throw new LineEditingDoneException(token, newLine ? line + '\n' : line, cursorpos);
      }
      switch (kind) {
        case Keystrokes.TOKEN_ABORT:
          throw new LineEditingInterruptedException(buffer.toString());
        case Keystrokes.TOKEN_PREV:
        case Keystrokes.TOKEN_UP:
          if (historyPos > 0 && (flags & LineEditor.FLAG_ALLOW_HISTORY) != 0) {
            deleteLine(buffer, cursorpos);
            final String command = m_history.get(--historyPos);
            buffer.append(command);
            m_out.print(command);
            cursorpos = command.length();
          } else {
            m_out.write(LineEditor.BELL);
          }
          break;
        case Keystrokes.TOKEN_NEXT:
        case Keystrokes.TOKEN_DOWN:
          if (historyPos < m_history.size() && (flags & LineEditor.FLAG_ALLOW_HISTORY) != 0) {
            deleteLine(buffer, cursorpos);
            cursorpos = 0;
            historyPos++;
            if (historyPos < m_history.size()) {
              final String command = m_history.get(historyPos);
              buffer.append(command);
              m_out.print(command);
              cursorpos = command.length();
            }
          } else {
            m_out.write(LineEditor.BELL);
          }
          break;
        case Keystrokes.TOKEN_RIGHT:
          if (cursorpos == buffer.length() || (flags & LineEditor.FLAG_ECHO) == 0) {
            // If at end of buffer or no echo, ignore arrow and beep.
            m_out.write(LineEditor.BELL);
          } else {
            m_out.write(buffer.charAt(cursorpos));
            cursorpos++;
          }
          break;
        case Keystrokes.TOKEN_LEFT:
          if (cursorpos == 0 || (flags & LineEditor.FLAG_ECHO) == 0) {
            // If at end of buffer or no echo, ignore arrow and beep.
            //
            if ((flags & LineEditor.FLAG_STOP_ON_BOL) != 0) {
              // FIXME Disabled this, breaks lots of stuff. :-)
              // throw new LineUnderflowException();
            }
            m_out.write(LineEditor.BELL);
          } else {
            cursorpos--;
            m_out.write(LineEditor.BS);
          }
          break;
        case Keystrokes.TOKEN_CR:
          editing = false;
          m_out.write('\r');
          m_out.write('\n');
          break;
        case Keystrokes.TOKEN_BS:
          if (cursorpos == 0) {
            if ((flags & LineEditor.FLAG_STOP_ON_BOL) != 0) {
              throw new LineUnderflowException(buffer.toString(), cursorpos);
            }
            m_out.write(LineEditor.BELL);
          } else {
            buffer.deleteCharAt(--cursorpos);
            m_out.write(LineEditor.BS);
            m_out.write(LineEditor.SPACE);
            m_out.write(LineEditor.BS);
            if (cursorpos < buffer.length()) {
              m_out.write(buffer.substring(cursorpos));
              m_out.write(' ');
              PrintUtils.printRepeated(m_out, LineEditor.BS, buffer.length() - cursorpos + 1);
            }
          }
          break;
        case Keystrokes.TOKEN_BOL:
          if (((flags & LineEditor.FLAG_ECHO) != 0) && cursorpos > 0) {
            // Advance cursor to beginning of line
            PrintUtils.printRepeated(m_out, LineEditor.BS, cursorpos);
            cursorpos = 0;
          }
          break;
        case Keystrokes.TOKEN_EOL:
          if (((flags & LineEditor.FLAG_ECHO) != 0) && cursorpos < buffer.length()) {
            // Advance cursor to end of line
            //
            m_out.write(buffer.substring(cursorpos));
            cursorpos = buffer.length();
          }
          break;
        case Keystrokes.TOKEN_CLEAR_LINE:
          {
            deleteLine(buffer, cursorpos);
            cursorpos = 0;
            break;
          }
        case Keystrokes.TOKEN_DELETE_WORD:
          {
            // Move i to beginning of current word.
            int i = cursorpos;
            for (; i > 0 && Character.isSpaceChar(buffer.charAt(i - 1)); --i) {}
            for (; i > 0 && !Character.isSpaceChar(buffer.charAt(i - 1)); --i) {}

            if (i != cursorpos) {
              // Delete characters in buffer from i to cursorpos.
              buffer.delete(i, cursorpos);

              // Move back cursor to i.
              PrintUtils.printRepeated(m_out, LineEditor.BS, cursorpos - i);

              // Print rest of buffer from i.
              m_out.write(buffer.substring(i));

              // Pad with spaces.
              PrintUtils.printRepeated(m_out, LineEditor.SPACE, cursorpos - i);

              // Move back cursor to i.
              PrintUtils.printRepeated(m_out, LineEditor.BS, buffer.length() + (cursorpos - i) - i);

              // Set new cursorpos
              cursorpos = i;
            }
            break;
          }
        case Keystrokes.TOKEN_SKIP:
          break;
        case KeystrokeTokenizerDefinition.LITERAL:
          if (stopChars != null && stopChars.indexOf(ch) != -1) {
            throw new StopCharException(buffer.toString(), cursorpos, ch);
          }

          // Are we exceeding max length?
          //
          if (maxLength != 0 && buffer.length() >= maxLength - 1) {
            // Break on overflow?
            //
            if ((flags & LineEditor.FLAG_STOP_ON_EOL) != 0) {
              buffer.insert(cursorpos++, ch);
              throw new LineOverflowException(buffer.toString(), cursorpos);
            }
            // Don't break. Just make noise and ignore keystroke
            //
            m_out.write(LineEditor.BELL);
            m_out.flush();
            break;
          }

          if (((flags & LineEditor.FLAG_TREAT_SPACE_AS_NEWLINE) != 0)
              && (0 == cursorpos)
              && LineEditor.SPACE == ch) {
            m_out.write("\r\n");
            return "\n"; // execute default command
          }

          // Skip non printable characters
          //
          if (ch < 32 && ch != LineEditor.TAB) {
            continue;
          }

          // TAB needs special treatment. Insert enough spaces to get to
          // the next position that's a multiple of TABSIZE
          //
          if (ch == LineEditor.TAB) {
            final int spaces = LineEditor.TABSIZE - (cursorpos % LineEditor.TABSIZE);
            for (int idx = 0; idx < spaces; ++idx) {
              buffer.insert(cursorpos++, ' ');
              m_out.write(' ');
            }
          } else {
            buffer.insert(cursorpos++, ch);
          }

          if ((flags & LineEditor.FLAG_ECHO) != 0) {
            if (ch != LineEditor.TAB) {
              m_out.write(ch);
            }
            if (cursorpos < buffer.length()) {
              m_out.write(buffer.substring(cursorpos));
              m_out.write(LineEditor.SPACE);
              PrintUtils.printRepeated(m_out, LineEditor.BS, buffer.length() - cursorpos + 1);
            }
          } else {
            m_out.write('*');
          }
          break;
      }
      m_out.flush();
    }
    // We got a string! Should we record it in command history?
    //
    final String answer = buffer.toString();

    // Don't store if not asked to or if previous command
    // was exactly the same.
    //
    if ((flags & LineEditor.FLAG_RECORD_HISTORY) != 0
        && buffer.length() > 0
        && (m_history.size() == 0 || !answer.equals(m_history.get(m_history.size() - 1)))) {
      m_history.add(answer);
    }
    return newLine ? answer + '\n' : answer;
  }

  private void deleteLine(final StringBuffer buffer, final int cursorpos) {
    final int top = buffer.length();

    // Advance cursor to end of line
    //
    int n = top - cursorpos;
    while (n-- > 0) {
      m_out.write(LineEditor.SPACE);
    }

    // Delete to beginning of line
    //
    n = top;
    while (n-- > 0) {
      m_out.write(LineEditor.BS);
      m_out.write(LineEditor.SPACE);
      m_out.write(LineEditor.BS);
    }
    buffer.delete(0, buffer.length());
  }

  public void shutdown() {
    if (m_eventPoller != null) {
      m_eventPoller.interrupt();
    }
    if (m_keystrokePoller != null) {
      m_keystrokePoller.interrupt();
    }
    m_eventPoller = null;
    m_keystrokePoller = null;
  }

  protected synchronized void handleEvent(final Event e) {
    m_eventQueue.addLast(e);
    notify();
  }

  protected synchronized Event getNextEvent() throws InterruptedException {
    while (m_eventQueue.isEmpty()) {
      wait();
    }
    return m_eventQueue.removeFirst();
  }

  public long getLastKeystrokeTime() {
    return m_lastKeystrokeTime;
  }

  public void resetLineCount() {
    m_lineCount = 0;
  }

  // Implementation of NewlineListener
  //
  @Override
  public void onNewline() {
    try {
      if (m_dontCount || m_bypass) {
        return;
      }
      if (++m_lineCount >= m_tsProvider.getTerminalSettings().getHeight() - 1) {
        m_dontCount = true;
        if (!getMore(m_morePrompt)) {
          throw new OutputInterruptedException();
        }
        m_lineCount = 0;
      }
    } catch (final InterruptedException e) {
      // We can't throw an InterruptedException here, since it would have to be
      // passed through the Writer interface.
      //
      // Don't page-break after this, since it could interfere with a session
      // trying to wind down it's operations.
      //
      m_bypass = true;
      throw new ImmediateShutdownException();
    } catch (final IOException e) {
      throw new RuntimeException(e);
    } finally {
      m_dontCount = false;
    }
  }

  /**
   * Proxy to a reader. This allows us to replace the underlying reader in order change the
   * character set mapping.
   *
   * @author Pontus Rydin
   */
  private static class ReaderProxy {
    private Reader m_reader;

    public ReaderProxy(final Reader reader) {
      m_reader = reader;
    }

    public void setReader(final Reader reader) {
      m_reader = reader;
    }

    public void close() throws IOException {
      m_reader.close();
    }

    @Override
    public boolean equals(final Object obj) {
      return m_reader.equals(obj);
    }

    @Override
    public int hashCode() {
      return m_reader.hashCode();
    }

    public void mark(final int readAheadLimit) throws IOException {
      m_reader.mark(readAheadLimit);
    }

    public boolean markSupported() {
      return m_reader.markSupported();
    }

    public int read() throws IOException {
      return m_reader.read();
    }

    public int read(final char[] cbuf) throws IOException {
      return m_reader.read(cbuf);
    }

    public int read(final char[] cbuf, final int off, final int len) throws IOException {
      return m_reader.read(cbuf, off, len);
    }

    public boolean ready() throws IOException {
      return m_reader.ready();
    }

    public void reset() throws IOException {
      m_reader.reset();
    }

    public long skip(final long n) throws IOException {
      return m_reader.skip(n);
    }

    @Override
    public String toString() {
      return m_reader.toString();
    }
  }

  private static class IOExceptionEvent extends Event {
    private final IOException m_exception;

    public IOExceptionEvent(final IOException exception) {
      m_exception = exception;
    }

    public IOException getException() {
      return m_exception;
    }
  }

  private abstract static class LineEditorHelper extends Thread {
    String m_threadName;

    public LineEditorHelper(final String threadName) {
      m_threadName = threadName;
      setThreadName("not logged in");
    }

    public void setThreadName(final String userName) {
      setName(m_threadName + " (" + userName + ")");
    }

    public void setSession(final ServerSession session) {
      setThreadName(session.getLoggedInUser().getUserid());
    }
  }

  private class EventPoller extends LineEditorHelper {
    private final int POLL_INTERVAL = ClientSettings.getEventPollInterval();

    public EventPoller() {
      super("EventPoller");
    }

    @Override
    public void run() {
      try {
        final EventSource es = m_session.getEventSource();
        for (; ; ) {
          final Event e = es.pollEvent(POLL_INTERVAL);
          if (e != null) {
            handleEvent(e);
          }
        }
      } catch (final InterruptedException e) {
        // Exiting gracefully...
        //
        LOG.debug("Exiting event poller");
      }
    }
  }

  private class KeystrokePoller extends LineEditorHelper {
    public KeystrokePoller() {
      super("KeystrokePoller");
    }

    @Override
    public void run() {
      for (; ; ) {
        try {
          final int ch = m_in.read();
          if (ch == -1) {
            // EOF on input. Kill session (if still alive)
            //
            handleEvent(new SessionShutdownEvent());
            break;
          }
          handleEvent(new KeystrokeEvent((char) ch));
        } catch (final IOException e) {
          handleEvent(new IOExceptionEvent(e));
          break;
        }
      }
      LOG.debug("Exiting keystroke poller");
    }
  }
}
