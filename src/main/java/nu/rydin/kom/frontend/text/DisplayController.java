/*
 * Created on Jul 8, 2004
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.frontend.text;

/** @author Pontus Rydin */
public interface DisplayController {
  public void prompt();

  public void normal();

  public void messageHeader();

  public void mailMessageHeader();

  public void messageBody();

  public void quotedMessageBody();

  public void messageFooter();

  public void messageSubject();

  public void editorLineNumber();

  public void chatMessageHeader();

  public void broadcastMessageHeader();

  public void chatMessageBody();

  public void broadcastMessageBody();

  public void input();

  public void output();

  public void header();

  public void highlight();

  public void quotedHighlight();

  public void reset();

  public void printWithAttributes(String string);
}
