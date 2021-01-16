/*
 * Created on Sep 18, 2004
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.frontend.text;

/** @author Pontus Rydin */
public interface EnvironmentListener {
  public void environmentChanged(String name, String value);
}
