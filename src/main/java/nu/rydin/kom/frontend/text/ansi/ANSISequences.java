/*
 * Created on Jun 6, 2004
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.frontend.text.ansi;

import java.text.MessageFormat;

/**
 * @author Pontus Rydin
 * @author Jepson
 */
public class ANSISequences {
  public static final String CLEAR_DISPLAY = "\u001b[H\u001b[J";
  public static final String SET_GRAPHIC_RENDITION = "\u001b[#";

  public static final String SET_CURSOR_POSITION = "\u001b[{0};{1}H";
  public static final String MOVE_CURSOR_UP = "\u001b[{0}A";
  public static final String MOVE_CURSOR_DOWN = "\u001b[{0}B";
  public static final String MOVE_CURSOR_RIGHT = "\u001b[{0}C";
  public static final String MOVE_CURSOR_RIGHT_ONE = "\u001b[C";
  public static final String MOVE_CURSOR_LEFT = "\u001b[{0}D";
  public static final String MOVE_CURSOR_LEFT_ONE = "\u001b[D";
  public static final String SAVE_CURSOR_POSITION = "\u001b[s";
  public static final String RECALL_CURSOR_POSITION = "\u001b[u";
  public static final String ERASE_CURRENT_LINE = "\u001b[K";

  // BLACK = Black text on white background, all others on black background.
  public static final String BLACK = "\u001b[47;30m";
  public static final String RED = "\u001b[40;31m";
  public static final String GREEN = "\u001b[40;32m";
  public static final String YELLOW = "\u001b[40;33m";
  public static final String BLUE = "\u001b[40;34m";
  public static final String MAGENTA = "\u001b[40;35m";
  public static final String CYAN = "\u001b[40;36m";
  public static final String WHITE = "\u001b[40;37m";

  public static final String BRIGHT = "\u001b[1m";
  public static final String RESET_ATTRIBUTES = "\u001b[0m";

  public static String moveCursorLeft(int moves) {
    return MessageFormat.format(MOVE_CURSOR_LEFT, new Object[] {new Integer(moves)});
  }

  public static String moveCursorRight(int moves) {
    return MessageFormat.format(MOVE_CURSOR_RIGHT, new Object[] {new Integer(moves)});
  }
}
