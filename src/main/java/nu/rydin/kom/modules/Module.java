/*
 * Created on Sep 17, 2004
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.modules;

import java.io.IOException;
import java.util.Map;
import nu.rydin.kom.exceptions.ModuleException;

/** @author Pontus Rydin */
public interface Module {
  /**
   * Starts the module and any background threads associated with it
   *
   * @param parameters Module specific parameters
   */
  void start(Map<String, String> parameters) throws ModuleException, IOException;

  /** Stops the module and any background threads associated with it */
  void stop();

  /**
   * Waits for a module to stop.
   *
   * @throws InterruptedException
   */
  void join() throws InterruptedException;
}
