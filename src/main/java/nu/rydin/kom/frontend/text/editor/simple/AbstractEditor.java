/*
 * Created on Jun 19, 2004
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.frontend.text.editor.simple;

import java.io.IOException;
import java.io.PrintWriter;
import nu.rydin.kom.exceptions.EventDeliveredException;
import nu.rydin.kom.exceptions.KOMException;
import nu.rydin.kom.exceptions.LineEditingDoneException;
import nu.rydin.kom.exceptions.LineEditingInterruptedException;
import nu.rydin.kom.exceptions.LineOverflowException;
import nu.rydin.kom.exceptions.LineUnderflowException;
import nu.rydin.kom.exceptions.OperationInterruptedException;
import nu.rydin.kom.exceptions.OutputInterruptedException;
import nu.rydin.kom.exceptions.StopCharException;
import nu.rydin.kom.exceptions.UnexpectedException;
import nu.rydin.kom.frontend.text.Context;
import nu.rydin.kom.frontend.text.DisplayController;
import nu.rydin.kom.frontend.text.KOMWriter;
import nu.rydin.kom.frontend.text.LineEditor;
import nu.rydin.kom.frontend.text.editor.Buffer;
import nu.rydin.kom.frontend.text.editor.EditorContext;
import nu.rydin.kom.frontend.text.editor.WordWrapper;
import nu.rydin.kom.frontend.text.parser.Parser;
import nu.rydin.kom.i18n.MessageFormatter;
import nu.rydin.kom.structs.UnstoredMessage;
import nu.rydin.kom.utils.PrintUtils;

/**
 * @author Pontus Rydin
 * @author Jepson
 */
public abstract class AbstractEditor extends EditorContext {
  public static final String MESSAGE_EDITOR_STOP_CHARS = "\u000c\u001a\u0004";
  private Parser m_parser;
  private final String m_commandList;

  public AbstractEditor(String commandList, Context context)
      throws IOException, UnexpectedException {
    super(context);
    m_commandList = commandList;
  }

  public abstract UnstoredMessage edit() throws KOMException, InterruptedException, IOException;

  public void fill(String content) {
    this.getBuffer().fill(this.getWordWrapper(content));
  }

  public void fill(WordWrapper wrapper) {
    this.getBuffer().fill(wrapper);
  }

  protected abstract void refresh() throws KOMException;

  protected abstract String getAbortQuestionFormat();

  protected void handleLineEditingInterruptedException(
      EditorContext context, LineEditingInterruptedException e)
      throws InterruptedException, OperationInterruptedException, IOException {
    for (; ; ) {
      // If user has written no more than three lines, abort immediately.
      if (context.getBuffer().size() <= 3) {
        throw e;
      }

      // Otherwise, ask user if he wants to abort.
      //
      MessageFormatter formatter = context.getMessageFormatter();
      KOMWriter out = context.getOut();
      LineEditor in = context.getIn();
      out.print(formatter.format(this.getAbortQuestionFormat()));
      out.flush();
      try {
        String answer = in.readLine();
        if (answer.equals(formatter.format("misc.y"))) throw e;
        else return;
      } catch (LineEditingDoneException e2) {
        continue;
      }
    }
  }

  protected void mainloop(boolean stopOnEmpty)
      throws InterruptedException, OperationInterruptedException, UnexpectedException, IOException {
    if (m_parser == null) m_parser = Parser.load(m_commandList, this);

    // Set up some stuff
    //
    DisplayController dc = this.getDisplayController();
    PrintWriter out = this.getOut();
    LineEditor in = this.getIn();
    Buffer buffer = this.getBuffer();
    int width = this.getTerminalSettings().getWidth() - 5;

    // Mainloop
    //
    String defaultLine = "";
    for (; ; ) {
      dc.messageHeader();
      PrintUtils.printRightJustified(out, Integer.toString(buffer.size() + 1), 4);
      out.print(':');
      dc.input();
      out.flush();
      String line = null;
      try {
        // TODO: Handle chat messages n'stuff.
        //
        int flags = LineEditor.FLAG_ECHO | LineEditor.FLAG_STOP_ON_EOL;
        if (buffer.size() > 0) flags |= LineEditor.FLAG_STOP_ON_BOL;
        try {
          line = in.readLine(defaultLine, MESSAGE_EDITOR_STOP_CHARS, width, flags);
        } catch (LineEditingDoneException e) {
          // TODO
        }

        // Check if we got a command
        //
        if (line.length() > 0 && line.charAt(0) == '!') {
          // Could be a command, but stuff starting with "!!" are
          // escaped "!".
          //
          if (line.startsWith("!!")) line = line.substring(1);
          else {
            try {
              // It's a command! How great! Go parse it!
              // But first, make sure we clear the current line
              //
              defaultLine = "";
              line = line.substring(1);
              Parser.ExecutableCommand executableCommand = null;
              executableCommand = m_parser.parseCommandLine(this, line);

              if (executableCommand == null) continue;

              // We have a command. Go run it!
              //
              executableCommand.execute(this);
            } catch (OperationInterruptedException e) {
              // ignore it, it was just the command that was interrupted,
              // (like a help listing), not the editing.
            } catch (OutputInterruptedException e) {
              // ignore it, it was just the command that was interrupted,
              // (like a help listing), not the editing.
            } catch (SaveEditorException e) {
              // We're done. Just return.
              return;
            } catch (QuitEditorException e) {
              throw new LineEditingInterruptedException(null);
            } catch (KOMException e) {
              // TODO: Is this the way we should handle this?
              //
              out.println(e.formatMessage(this));
            }

            // Don't include this in the buffer!
            //
            continue;
          }
        }

        // Stop on empty line if requested
        //
        if (stopOnEmpty && line.length() == 0) return;

        // Add line to buffer
        //
        line += '\n';
        buffer.add(line);
        defaultLine = null;
      } catch (EventDeliveredException e) {
        // TODO: Handle chat messages here!
      } catch (LineEditingInterruptedException e) {
        defaultLine = e.getPartialLine();
        if (defaultLine != null) {
          // If it's null we've gotten here by a Quit command, otherwise by ctrl-c.
          // Only add extra line in ctrl-c case. (Sorry f�r fulkoden. /Ihse)
          out.println();
        }
        handleLineEditingInterruptedException(this, e);
      } catch (LineOverflowException e) {
        // Overflow! We have to wrap the line
        //
        String original = e.getLine();
        WordWrapper wrapper = this.getWordWrapper(original, width - 1);
        line = wrapper.nextLine();
        buffer.add(line);
        defaultLine = wrapper.nextLine();
        if (defaultLine == null) defaultLine = "";

        // Erase wrapped portion, but don't erase the character around which we wrap.
        //
        int top = defaultLine.length();
        for (int idx = 1; idx < top; ++idx) out.print("\b \b");
        out.println();
      } catch (LineUnderflowException e) {
        if (buffer.size() > 0) {
          defaultLine = buffer.get(buffer.size() - 1).toString();

          // Backspacing to the previous line implicitly deletes
          // the newline. Handle that!
          //
          if (defaultLine.endsWith("\n"))
            defaultLine = defaultLine.substring(0, defaultLine.length() - 1);
          buffer.remove(buffer.size() - 1);
        }
        out.print('\r');
      } catch (StopCharException e) {
        String s = null;
        switch (e.getStopChar()) {
          case '\u0004': // Ctrl-D
          case '\u001a': // Ctrl-Z
            out.println();
            s = e.getLine();
            if (s.length() > 0) buffer.add(s);
            return;
          case '\u000c': // Ctrl-L
            try {
              s = e.getLine();
              defaultLine = s;
              out.println();
              try {
                refresh();
              } catch (OutputInterruptedException e2) {
                // We don't want people to be kicked out of
                // the editor just because they abort a listing.
                // Just ingore...
                //

              } catch (OperationInterruptedException e2) {
                // We don't want people to be kicked out of
                // the editor just because they abort a listing.
                // Just ingore...
                //

              }
            } catch (KOMException e1) {
              throw new RuntimeException(e1);
            }
            break;
        }
      }
    }
  }
}
