/*
 * Created on Apr 12, 2005
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.sklaff;

/** @author Pontus Rydin */
public class CharConvert {
  public static char convert(char ch) {
    if (ch == '{') return '�';
    if (ch == '}') return '�';
    if (ch == '|') return '�';
    if (ch == '[') return '�';
    if (ch == ']') return '�';
    if (ch == '\\') return '�';
    return ch;
  }

  public static String convert(String s) {
    if (s == null) return null;
    StringBuffer sb = new StringBuffer(s.length());
    int top = s.length();
    for (int idx = 0; idx < top; ++idx) sb.append(convert(s.charAt(idx)));
    return sb.toString();
  }
}
