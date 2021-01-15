/*
 * Created on Nov 4, 2003
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom;

import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** @author <a href=mailto:pontus@rydin.nu>Pontus Rydin</a> */
public class SystemSettings {
  private final Properties m_resources;

  public SystemSettings(final Map<String, String> parameters) {
    m_resources = new Properties();
    final Set<String> keys = parameters.keySet();
    for (final Iterator<String> iter = keys.iterator(); iter.hasNext(); ) {
      final String key = iter.next();
      m_resources.setProperty(key, parameters.get(key));
    }
  }

  public String getString(final String key) {
    return m_resources.getProperty(key);
  }

  public int getInt(final String key) {
    return Integer.parseInt(getString(key));
  }

  public long getLong(final String key) {
    return Long.parseLong(getString(key));
  }
}
