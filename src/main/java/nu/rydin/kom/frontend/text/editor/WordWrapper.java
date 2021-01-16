/*
 * Created on Jun 16, 2004
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.frontend.text.editor;

/** @author Pontus Rydin */
public interface WordWrapper {
  public Character getBreakCharacter();

  public String nextLine();
}
