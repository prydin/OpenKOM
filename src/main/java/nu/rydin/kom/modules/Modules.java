/*
 * Created on Oct 11, 2004
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.modules;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import nu.rydin.kom.exceptions.NoSuchModuleException;

/** @author Pontus Rydin */
public class Modules {
  private static final Map<String, Module> s_modules =
      Collections.synchronizedMap(new HashMap<String, Module>());

  public static void registerModule(final String name, final Module module) {
    Modules.s_modules.put(name, module);
  }

  public static void unregisterModule(final String name) {
    Modules.s_modules.remove(name);
  }

  public static <T extends Module> T getModule(final String name, final Class<T> clazz)
      throws NoSuchModuleException {
    final T module = (T) Modules.s_modules.get(name);
    if (module == null) {
      throw new NoSuchModuleException(name);
    }
    return module;
  }

  public static Set<String> listModuleNames() {
    return Modules.s_modules.keySet();
  }
}
