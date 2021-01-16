package nu.rydin.kom.modules;

import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ConfigurationHelper {
  private static final Logger LOG = LogManager.getLogger(ConfigurationHelper.class);

  public static int getInt(
      final Map<String, String> properties, final String name, final int defaultValue) {
    try {
      return Integer.parseInt(properties.get(name));
    } catch (final NumberFormatException e) {
      LOG.warn("Invalid or missing value for '" + name + "'. Using default value: " + defaultValue);
      return defaultValue;
    }
  }

  public static long getLong(
      final Map<String, String> properties, final String name, final long defaultValue) {
    try {
      return Long.parseLong(properties.get(name));
    } catch (final NumberFormatException e) {
      LOG.warn("Invalid or missing value for '" + name + "'. Using default value: " + defaultValue);
      return defaultValue;
    }
  }
}
