/*
 * Created on Jun 17, 2004
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.frontend.text.editor;

/** @author Pontus Rydin */
public interface WordWrapperFactory {
  public WordWrapper create(String text, int width);

  public WordWrapper create(String text, int width, int offset);
}
