/*
 * Created on Oct 5, 2003
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.frontend.text;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Date;
import nu.rydin.kom.backend.ServerSession;
import nu.rydin.kom.exceptions.DuplicateNameException;
import nu.rydin.kom.exceptions.InvalidNameException;
import nu.rydin.kom.exceptions.KOMException;
import nu.rydin.kom.exceptions.ObjectNotFoundException;
import nu.rydin.kom.exceptions.UnexpectedException;
import nu.rydin.kom.frontend.text.editor.WordWrapper;
import nu.rydin.kom.frontend.text.parser.Parser;
import nu.rydin.kom.frontend.text.terminal.TerminalController;
import nu.rydin.kom.i18n.MessageFormatter;
import nu.rydin.kom.structs.Name;
import nu.rydin.kom.structs.NameAssociation;
import nu.rydin.kom.structs.UserInfo;

/** @author Pontus Rydin */
public interface Context extends TerminalSettingsProvider {
  /** Returns the <tt>Reader</tt> handling user input */
  public LineEditor getIn();

  /** Returns the <tt>PrintWriter</tt> used to write messages to the user. */
  public KOMWriter getOut();

  /** Returns the defaul message formatter */
  public MessageFormatter getMessageFormatter();

  /** Returns the <tt>MessagePrinter</tt> associated with this session */
  public MessagePrinter getMessagePrinter();

  /** Returns the <tt>MessageEditor</tt> configured for this session */
  public MessageEditor getMessageEditor() throws UnexpectedException;

  /** Prints debug info */
  public void printDebugInfo();

  /**
   * Returns the backend session
   *
   * @return
   */
  public ServerSession getSession();

  /** Prints information about the current conference */
  public void printCurrentConference() throws ObjectNotFoundException, UnexpectedException;

  /** Returns the id of the logged in user */
  public long getLoggedInUserId();

  /**
   * Return cached user information. NOTE: Although an effort is made to keep this info up to date,
   * it is not 100% reliable. Don't use for operations where slightly stale data could cause
   * problems.
   *
   * @throws UnexpectedException
   */
  public UserInfo getCachedUserInfo() throws UnexpectedException;

  /** Invalidates user info cache. */
  public void clearUserInfoCache();

  /**
   * Returns true if the specified flag in the specified flag word i set
   *
   * @param flagWord The flag word index
   * @param mask The mask to check
   * @return
   */
  boolean isFlagSet(int flagWord, long mask) throws ObjectNotFoundException, UnexpectedException;

  /** Returns localized names of user flags. */
  public String[] getFlagLabels(String flagTable);

  /**
   * Returns the WordWrapper to use when formatting messages
   *
   * @param content The text to wrap
   */
  public WordWrapper getWordWrapper(String content);

  /**
   * Returns the WordWrapper to use when formatting messages
   *
   * @param content The text to wrap
   * @param length Maximum line length
   */
  public WordWrapper getWordWrapper(String content, int length);

  /** Returns terminal information */
  public TerminalSettings getTerminalSettings();

  /**
   * Manually sets the width of the terminal.
   *
   * @param width
   */
  public void setTerminalWidth(int width);

  /**
   * Manually sets the height of the terminal.
   *
   * @param height
   */
  public void setTerminalHeight(int height);

  /**
   * Decide whether the Context should listen to terminal size changes or not.
   *
   * @param value
   */
  public void setListenToTerminalSize(boolean value);

  /**
   * Formats a conference according to current logged in user
   *
   * @param id
   * @param name
   * @return
   */
  // public String formatConferenceName(String name, long id);

  /**
   * Formats a user according to current logged in user
   *
   * @param id
   * @param name
   * @return
   */
  // public String formatUserName(String name, long id);

  /**
   * Formats an object name according to user settings
   *
   * @param name Object name
   * @param id Object id
   */
  public String formatObjectName(Name name, long id);

  /**
   * Formats an object name according to user settings
   *
   * @param name Object name
   * @param id Object id
   */
  // public String formatObjectName(String name, long id);

  /** Formats an object name according to user settings */
  public String formatObjectName(NameAssociation object);

  /**
   * Formats a timestamp in a space efficient way.
   *
   * @param date The date to format
   * @return A formatted date
   */
  public String smartFormatDate(Date date) throws UnexpectedException;

  /** Returns a <tt>DisplayController</tt> according to the user preferences */
  public DisplayController getDisplayController();

  /**
   * Runs script file or OpenKOM commands.
   *
   * @param script The contents of the script file.
   * @throws IOException
   * @throws InterruptedException
   * @throws KOMException
   */
  public void executeScript(String script) throws IOException, InterruptedException, KOMException;

  /**
   * Runs script file or OpenKOM commands.
   *
   * @param rdr A BufferedReader reading the commands.
   * @throws IOException
   * @throws InterruptedException
   * @throws KOMException
   */
  public void executeScript(BufferedReader rdr)
      throws IOException, InterruptedException, KOMException;

  /**
   * Return the Parser associated with this Context.
   *
   * @return the parser.
   */
  public Parser getParser();

  /**
   * Performs a sanity check on a name
   *
   * @param name
   * @throws DuplicateNameException
   */
  public void checkName(String name)
      throws DuplicateNameException, InvalidNameException, UnexpectedException;

  /** Returns the terminal controller associated with the users terminal. */
  public TerminalController getTerminalController();
}
