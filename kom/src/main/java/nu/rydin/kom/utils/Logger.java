/*
 * Created on Jul 21, 2004
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.utils;

/** @author Henrik Schr�der */
public class Logger extends Object {
  static {
    try {
      Logger.initialize();
    } catch (final Exception e) {
      System.err.println("Exception when initializing logging...");
      e.printStackTrace();
    }
  }

  public static void initialize() throws Exception {
    // Never mind, we're just relying on log4j's ability to autoload log4j.properties.

    // TODO: Maybe we should add the ability to read from a specified XML config file
    // DOMConfigurator.configureAndWatch("D:/eclipse/workspace/kom/java/log.xml");
  }

  public static boolean isDebugEnabled(final Object invoker) {
    return Logger.getInstance(invoker).isDebugEnabled();
  }

  public static void debug(final Object invoker) {
    Logger.getInstance(invoker).debug(null);
  }

  public static void debug(final Object invoker, final Object message) {
    Logger.getInstance(invoker).debug(message);
  }

  public static void debug(final Object invoker, final Throwable throwable) {
    Logger.getInstance(invoker).debug(null, throwable);
  }

  public static void debug(final Object invoker, final Object message, final Throwable throwable) {
    Logger.getInstance(invoker).debug(message, throwable);
  }

  public static void info(final Object invoker) {
    Logger.getInstance(invoker).info(null);
  }

  public static void info(final Object invoker, final Object message) {
    Logger.getInstance(invoker).info(message);
  }

  public static void info(final Object invoker, final Throwable throwable) {
    Logger.getInstance(invoker).info(null, throwable);
  }

  public static void info(final Object invoker, final Object message, final Throwable throwable) {
    Logger.getInstance(invoker).info(message, throwable);
  }

  public static void warn(final Object invoker) {
    Logger.getInstance(invoker).warn(null);
  }

  public static void warn(final Object invoker, final Object message) {
    Logger.getInstance(invoker).warn(message);
  }

  public static void warn(final Object invoker, final Throwable throwable) {
    Logger.getInstance(invoker).warn(null, throwable);
  }

  public static void warn(final Object invoker, final Object message, final Throwable throwable) {
    Logger.getInstance(invoker).warn(message, throwable);
  }

  public static void error(final Object invoker) {
    Logger.getInstance(invoker).error(null);
  }

  public static void error(final Object invoker, final Object message) {
    Logger.getInstance(invoker).error(message);
  }

  public static void error(final Object invoker, final Throwable throwable) {
    Logger.getInstance(invoker).error(null, throwable);
  }

  public static void error(final Object invoker, final Object message, final Throwable throwable) {
    Logger.getInstance(invoker).error(message, throwable);
  }

  public static void fatal(final Object invoker) {
    Logger.getInstance(invoker).fatal(null);
  }

  public static void fatal(final Object invoker, final Object message) {
    Logger.getInstance(invoker).fatal(message);
  }

  public static void fatal(final Object invoker, final Throwable throwable) {
    Logger.getInstance(invoker).fatal(null, throwable);
  }

  public static void fatal(final Object invoker, final Object message, final Throwable throwable) {
    Logger.getInstance(invoker).fatal(message, throwable);
  }

  private static org.apache.log4j.Logger getInstance(final Object invoker) {
    final Class invokerClass = (invoker instanceof Class) ? (Class) invoker : invoker.getClass();
    return (org.apache.log4j.Logger) org.apache.log4j.Logger.getInstance(invokerClass.getName());
  }
}
