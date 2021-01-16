/*
 * Created on Nov 10, 2003
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.frontend.text;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketException;
import java.util.LinkedList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** @author Pontus Rydin */
public class TelnetInputStream extends InputStream {
  private static final Logger LOG = LogManager.getLogger(TelnetInputStream.class);
  private static final short STATE_NORMAL = 0;
  private static final short STATE_IAC = 1;
  private static final short STATE_WILL = 2;
  private static final short STATE_DO = 3;
  private static final short STATE_WONT = 4;
  private static final short STATE_DONT = 5;
  private static final short STATE_SB = 6;
  private static final short STATE_DATA = 7;
  private static final short STATE_AFTER_COMMAND = 8;
  private static final short STATE_CR = 9;

  private static final byte CHAR_SE = -16;
  private static final byte CHAR_SB = -6;
  private static final byte CHAR_WILL = -5;
  private static final byte CHAR_WONT = -4;
  private static final byte CHAR_DO = -3;
  private static final byte CHAR_DONT = -2;
  private static final byte CHAR_IAC = -1;

  // ENVIRON subcommands
  //
  private static final byte CHAR_IS = 0;
  private static final byte CHAR_SEND = 1;
  private static final byte CHAR_INFO = 2;
  private static final byte CHAR_VAR = 0;
  private static final byte CHAR_VALUE = 1;
  private static final byte CHAR_ESC = 2;
  private static final byte CHAR_USERVAR = 3;

  // ENVIRON state machine
  //
  private static final int ENV_STATE_NEW = 0;
  private static final int ENV_STATE_ESC = 1;
  private static final int ENV_STATE_VAR = 2;
  private static final int ENV_STATE_VALUE = 3;

  private static final int OPT_BINARY = 0;
  private static final int OPT_ECHO = 1;
  private static final int OPT_SUPPRESS_GA = 3;

  @SuppressWarnings("unused")
  private static final int OPT_NAOCRD = 10;

  private static final int OPT_FLOWCONTROL = 33;
  private static final int OPT_NAWS = 31;
  private static final int OPT_LINEMODE = 34;
  private static final int OPT_ENVIRON = 39;

  // Other characters
  //
  private static final int LF = 10;
  private static final int CR = 13;
  private final InputStream m_input;
  private final OutputStream m_output;
  private final List<TerminalSizeListener> m_sizeListeners = new LinkedList<>();
  private final List<EnvironmentListener> m_environmentListeners = new LinkedList<>();
  private short m_state = TelnetInputStream.STATE_NORMAL;
  private int[] m_dataBuffer;
  private int m_dataState;
  private int m_dataIdx;

  public TelnetInputStream(final InputStream input, final OutputStream output) throws IOException {
    m_input = input;
    m_output = output;

    // We're willing to receive environment variables
    //
    sendOption(TelnetInputStream.CHAR_DO, TelnetInputStream.OPT_ENVIRON);

    // Please don't use linemode
    //
    sendOption(TelnetInputStream.CHAR_WONT, TelnetInputStream.OPT_LINEMODE);

    // We do use binary mode
    //
    sendOption(TelnetInputStream.CHAR_DO, TelnetInputStream.OPT_BINARY);

    // We will echo
    //
    sendOption(TelnetInputStream.CHAR_WILL, TelnetInputStream.OPT_ECHO);

    // We will suppress go ahead
    //
    sendOption(TelnetInputStream.CHAR_WILL, TelnetInputStream.OPT_SUPPRESS_GA);

    // Please use NAWS if you support it!
    //
    sendOption(TelnetInputStream.CHAR_DO, TelnetInputStream.OPT_NAWS);

    // Now would be a good time to send your environment variables
    //
    m_output.write(TelnetInputStream.CHAR_IAC);
    m_output.write(TelnetInputStream.CHAR_SB);
    m_output.write(TelnetInputStream.OPT_ENVIRON);
    m_output.write(TelnetInputStream.CHAR_SEND);
    m_output.write(TelnetInputStream.CHAR_IAC);
    m_output.write(TelnetInputStream.CHAR_SE);

    m_output.flush();
  }

  protected static int complement2(final int n) {
    return n < 0 ? 256 + n : n;
  }

  public void addSizeListener(final TerminalSizeListener listener) {
    synchronized (m_sizeListeners) {
      m_sizeListeners.add(listener);
    }
  }

  public void addEnvironmentListener(final EnvironmentListener listener) {
    synchronized (m_environmentListeners) {
      m_environmentListeners.add(listener);
    }
  }

  @Override
  public int read() throws IOException {
    for (; ; ) {
      final int data = m_input.read();
      if (data == -1) {
        return data; // EOF!
      }

      // Don't echo if we're called from block-read methods, since they
      // will take care of echoing themselves.
      //
      // TODO: Something very weird is going on here!
      final boolean m_suppressProcessing = false;
      if (!m_suppressProcessing) {
        return data;
      }
      final boolean valid = stateMachine(data);
      if (valid) {
        return data;
      }
    }
  }

  @Override
  public int read(final byte[] b) throws IOException {
    return read(b, 0, b.length);
  }

  @Override
  public int read(final byte[] b, final int off, final int length) throws IOException {
    final byte[] buffer = new byte[length];
    for (; ; ) {
      int n;
      try {
        n = m_input.read(buffer);
      } catch (final SocketException e) {
        // The socket was probably disconnected. Treat as EOF.
        //
        return -1;
      }

      // End of file. Nothing more to do!
      // We treat zero bytes returned as an EOF
      // condition as well.
      //
      if (n <= 0) {
        return -1;
      }
      n = handleBuffer(buffer, off, n);

      // Anything left after trimming?
      // If not, try reading again
      //
      if (n == 0) {
        continue;
      }
      System.arraycopy(buffer, 0, b, off, n);
      return n;
    }
  }

  protected int handleBuffer(final byte[] bytes, final int offset, final int length)
      throws IOException {
    final byte[] copy = new byte[length];
    int n = 0;
    for (int idx = 0; idx < length; ++idx) {
      final byte b = bytes[offset + idx];
      final boolean valid = stateMachine(b);
      if (valid) {
        copy[n++] = b;
      }
    }
    System.arraycopy(copy, 0, bytes, offset, n);
    return n;
  }

  protected void sendCommand(final int command) throws IOException {
    m_output.write(TelnetInputStream.CHAR_IAC);
    m_output.write(command);
  }

  protected void sendOption(final int verb, final int option) throws IOException {
    sendCommand(verb);
    m_output.write(option);
  }

  protected boolean stateMachine(final int b) throws IOException {
    // System.out.println("Char: "+ b);
    switch (m_state) {
      case TelnetInputStream.STATE_CR:
        if (b == TelnetInputStream.LF) {
          return false; // Strip latter part of CRLF
        }
        m_state = TelnetInputStream.STATE_NORMAL;
        // FALL THRU
      case TelnetInputStream.STATE_NORMAL:
        switch (b) {
          case 0:
            return false;
          case TelnetInputStream.CHAR_IAC:
            m_state = TelnetInputStream.STATE_IAC;
            return false;
          case TelnetInputStream.CR:
            m_state = TelnetInputStream.STATE_CR;
            return true;
          default:
            return true;
        }
      case TelnetInputStream.STATE_IAC:
        switch (b) {
          case TelnetInputStream.CHAR_IAC:
            // Escaped 255
            //
            m_state = TelnetInputStream.STATE_NORMAL;
            return true;
          case TelnetInputStream.CHAR_WILL:
            m_state = TelnetInputStream.STATE_WILL;
            break;
          case TelnetInputStream.CHAR_WONT:
            m_state = TelnetInputStream.STATE_WONT;
            break;
          case TelnetInputStream.CHAR_DO:
            m_state = TelnetInputStream.STATE_DO;
            break;
          case TelnetInputStream.CHAR_DONT:
            m_state = TelnetInputStream.STATE_DONT;
            break;
          case TelnetInputStream.CHAR_SB:
            m_state = TelnetInputStream.STATE_SB;
            break;
          default:
            handleCommand(b);
            m_state = TelnetInputStream.STATE_NORMAL;
            break;
        }
        break;
      case TelnetInputStream.STATE_WILL:
        handleWill(b);
        m_state = TelnetInputStream.STATE_NORMAL;
        break;
      case TelnetInputStream.STATE_WONT:
        handleWont(b);
        m_state = TelnetInputStream.STATE_NORMAL;
        break;
      case TelnetInputStream.STATE_DO:
        handleDo(b);
        m_state = TelnetInputStream.STATE_NORMAL;
        break;
      case TelnetInputStream.STATE_DONT:
        handleDont(b);
        m_state = TelnetInputStream.STATE_NORMAL;
        break;
      case TelnetInputStream.STATE_SB:
        LOG.debug("SB: " + b);
        switch (b) {
          case TelnetInputStream.OPT_NAWS:
            m_dataBuffer = new int[4];
            m_dataIdx = 0;
            m_state = TelnetInputStream.STATE_DATA;
            break;
          case TelnetInputStream.OPT_ENVIRON:
            m_dataBuffer = new int[8192];
            m_dataIdx = 0;
            m_state = TelnetInputStream.STATE_DATA;
            break;
        }
        m_dataState = b;
        break;
      case TelnetInputStream.STATE_DATA:
        {
          switch (b) {
            case TelnetInputStream.CHAR_IAC:
              m_state = TelnetInputStream.STATE_AFTER_COMMAND;
              break;
            default:
              if (m_dataIdx < m_dataBuffer.length) {
                m_dataBuffer[m_dataIdx++] = b;
              } else {
                m_state = TelnetInputStream.STATE_NORMAL; // Buffer overflow, go back to normal
              }
          }
          break;
        }
      case TelnetInputStream.STATE_AFTER_COMMAND:
        if (b != TelnetInputStream.CHAR_SE) {
          // Huh? Not end of subnegotiation?
          //
          m_state = TelnetInputStream.STATE_NORMAL;
          break;
        }
        // End of command
        //
        switch (m_dataState) {
          case TelnetInputStream.OPT_NAWS:
            handleNaws();
            m_state = TelnetInputStream.STATE_NORMAL;
            break;
          case TelnetInputStream.OPT_ENVIRON:
            handleEnviron();
            m_state = TelnetInputStream.STATE_NORMAL;
          default:
            m_state = TelnetInputStream.STATE_NORMAL;
        }
        break;
    }
    return false;
  }

  protected void handleCommand(final int ch) {
    LOG.debug("Command: " + ch);
  }

  protected void handleWill(final int ch) throws IOException {
    LOG.debug("Will: " + ch);
    switch (ch) {
      case TelnetInputStream.OPT_LINEMODE:
        sendOption(TelnetInputStream.CHAR_DONT, TelnetInputStream.OPT_LINEMODE);
        sendOption(TelnetInputStream.CHAR_WONT, TelnetInputStream.OPT_LINEMODE);
        break;
      case TelnetInputStream.OPT_FLOWCONTROL:
        sendOption(TelnetInputStream.CHAR_DONT, TelnetInputStream.OPT_FLOWCONTROL);
        sendOption(TelnetInputStream.CHAR_WONT, TelnetInputStream.OPT_FLOWCONTROL);
        break;
    }
  }

  protected void handleWont(final int ch) {
    LOG.debug("Won't: " + ch);
  }

  protected void handleDo(final int ch) throws IOException {
    LOG.debug("Do: " + ch);
    switch (ch) {
      case TelnetInputStream.OPT_ECHO:
        // Yes, we will echo
        //
        sendOption(TelnetInputStream.CHAR_WILL, TelnetInputStream.OPT_ECHO);
        break;
      case TelnetInputStream.OPT_SUPPRESS_GA:
        // Yes, we will supress GA
        //
        sendOption(TelnetInputStream.CHAR_WILL, TelnetInputStream.OPT_SUPPRESS_GA);
        break;
    }
  }

  protected void handleDont(final int ch) throws IOException {
    LOG.debug("Don't: " + ch);
    switch (ch) {
      case TelnetInputStream.OPT_ECHO:
        // I'm sorry, but we will echo
        //
        sendOption(TelnetInputStream.CHAR_WILL, TelnetInputStream.OPT_ECHO);
        break;
      case TelnetInputStream.OPT_SUPPRESS_GA:
        // I'm sorry, but we will supress GA.
        //
        sendOption(TelnetInputStream.CHAR_WILL, TelnetInputStream.OPT_SUPPRESS_GA);
        break;
    }
  }

  protected void handleNaws() {
    final int width =
        (TelnetInputStream.complement2(m_dataBuffer[0]) << 8)
            + TelnetInputStream.complement2(m_dataBuffer[1]);
    final int height =
        (TelnetInputStream.complement2(m_dataBuffer[2]) << 8)
            + TelnetInputStream.complement2(m_dataBuffer[3]);
    LOG.debug("NAWS: " + width + "*" + height);
    synchronized (m_sizeListeners) {
      for (final TerminalSizeListener terminalSizeListener : m_sizeListeners) {
        terminalSizeListener.terminalSizeChanged(width, height);
      }
    }
  }

  protected void handleEnviron() {
    // First byte is "IS" or "INFO". We treat them both the same. Ignore anything else
    //
    final int subCommand = m_dataBuffer[0];
    if (subCommand != TelnetInputStream.CHAR_IS && subCommand != TelnetInputStream.CHAR_INFO) {
      return;
    }

    // Now parse variable data
    //
    int state = TelnetInputStream.ENV_STATE_NEW;
    final int top = m_dataIdx;
    StringBuffer buffer = null;
    String name = "";
    String value;
    for (int idx = 1; idx < top; ++idx) {
      final char ch = (char) m_dataBuffer[idx];
      switch (state) {
        case TelnetInputStream.ENV_STATE_NEW:
          switch (ch) {
            case TelnetInputStream.CHAR_USERVAR:
            case TelnetInputStream.CHAR_VAR:
              // Start of variable
              //
              state = TelnetInputStream.ENV_STATE_VAR;
              buffer = new StringBuffer();
              break;
            default:
              // Unknown character. Go back to start state
              //
              state = TelnetInputStream.ENV_STATE_NEW;
          }
          break;
        case TelnetInputStream.ENV_STATE_VALUE:
        case TelnetInputStream.ENV_STATE_VAR:
          switch (ch) {
            case TelnetInputStream.CHAR_ESC:
              state = TelnetInputStream.ENV_STATE_ESC;
              break;
            case TelnetInputStream.CHAR_VALUE:
              name = buffer.toString();
              buffer = new StringBuffer();
              state = TelnetInputStream.ENV_STATE_VALUE;
              break;
            case TelnetInputStream.CHAR_USERVAR:
            case TelnetInputStream.CHAR_VAR:
              if (state == TelnetInputStream.ENV_STATE_VALUE) {
                value = buffer.toString();
                state = TelnetInputStream.ENV_STATE_VAR;
              } else {
                name = buffer.toString();
                value = "";
              }
              buffer = new StringBuffer();
              handleEnvironmentVariable(name, value);
              break;
            default:
              buffer.append(ch);
              break;
          }
          break;
        case TelnetInputStream.ENV_STATE_ESC:
          buffer.append(ch);
          break;
      }
    }
    // Handle dangling variable
    //
    if (name.length() > 0) {
      handleEnvironmentVariable(name, buffer.toString());
    }
  }

  protected void handleEnvironmentVariable(final String name, final String value) {
    LOG.debug("ENVIRON: var=" + name + " value=" + value);
    synchronized (m_environmentListeners) {
      for (final EnvironmentListener environmentListener : m_environmentListeners) {
        environmentListener.environmentChanged(name, value);
      }
    }
  }
}
