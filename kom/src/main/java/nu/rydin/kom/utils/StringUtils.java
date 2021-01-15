/*
 * Created on Jul 8, 2004
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.utils;

/** @author <a href=mailto:pontus@rydin.nu>Pontus Rydin</a> */
public class StringUtils {
  /**
   * Returns true if the supplied string can be interpreted as an integer.
   *
   * @param s The string to test
   * @return
   */
  public static boolean isNumeric(final String s) {
    final int top = s.length();
    for (int idx = 0; idx < top; ++idx) {
      final char each = s.charAt(idx);
      if (each < '0' || each > '9') {
          return false;
      }
    }
    return true;
  }

  /**
   * Returns true if the supplie string can be parsed as a local or global message number.
   *
   * @param s The string to test
   */
  public static boolean isMessageNumber(final String s) {
    int top = s.length();
    final String maybeNumber =
        s.charAt(0) == '(' && s.charAt(top - 1) == ')' ? s.substring(1, top - 1) : s;
    top = maybeNumber.length();
    for (int idx = 0; idx < top; ++idx) {
      if (!Character.isDigit(maybeNumber.charAt(idx))) {
          return false;
      }
    }
    return true;
  }

  public static String formatElapsedTime(long time) {
    time /= 60000;
    final long hours = time / 60;
    final long minutes = time % 60;
    final StringBuffer buffer = new StringBuffer(10);
    if (hours > 0) {
      buffer.append(hours);
      buffer.append(':');
    }
    if (minutes < 10 && hours > 0) {
        buffer.append('0');
    }
    buffer.append(minutes);
    return buffer.toString();
  }
}
