/*
 * Created on Oct 5, 2003
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.backend;

import java.util.StringTokenizer;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** @author Pontus Rydin */
public class NameUtils {
  private static final Pattern NamePattern;

  private static final Pattern UserNamePattern;

  private static final Pattern ConferenceNamePattern;

  static {
    try {
      NamePattern = Pattern.compile("^[^*]+$");
      UserNamePattern = Pattern.compile("^[^*]+$");
      ConferenceNamePattern = Pattern.compile("^[^*]+$");
    } catch (final PatternSyntaxException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  public static String normalizeNameKeepParanteses(final String name) {
    final int top = name.length();
    final StringBuilder buffer = new StringBuilder(top);
    for (final StringTokenizer st = new StringTokenizer(name, " "); st.hasMoreTokens(); ) {
      buffer.append(st.nextToken().toUpperCase());
      if (st.hasMoreTokens()) {
        buffer.append(' ');
      }
    }
    return buffer.toString();
  }

  /**
   * Removes non-significant parts of a name. All within parenthesis is ignored. No parenthesis
   * stacking. All parenthesis signs are ignored. All whitespace is compressed to single space.
   *
   * <p>Example: "(The) KOM System (general discussions)" becomes "KOM SYSTEM". "Foo ((((((((is a)
   * ))) bar" becomes "FOO ))) BAR"
   *
   * @param name The name to normalize
   * @return The normalized name
   */
  public static String normalizeName(final String name) {
    final int top = name.length();
    final StringBuilder buffer = new StringBuilder(top);
    boolean lastInsertedWasSpace = true;
    boolean inIgnoreMode = false;
    for (int idx = 0; idx < top; ++idx) {
      final char ch = name.charAt(idx);
      if (inIgnoreMode) {
        if (ch == ')') {
          inIgnoreMode = false;
        }
      } else {
        if (ch == '(') {
          inIgnoreMode = true;
        } else if (ch == ')') {
          // Skip...
        } else if (ch == ' ') {
          if (!lastInsertedWasSpace) {
            buffer.append(' ');
            lastInsertedWasSpace = true;
          }
        } else {
          buffer.append(Character.toUpperCase(ch));
          lastInsertedWasSpace = false;
        }
      }
    }
    return buffer.toString();
  }

  public static boolean isValidName(final String name) {
    return NamePattern.matcher(name).matches();
  }

  public static boolean isValidUserName(final String name) {
    return UserNamePattern.matcher(name).matches();
  }

  public static boolean isValidConferenceName(final String name) {
    return ConferenceNamePattern.matcher(name).matches();
  }

  public static String[] splitName(final String name) {
    final StringTokenizer st = new StringTokenizer(normalizeName(name));
    final int top = st.countTokens();
    final String[] answer = new String[top];
    for (int idx = 0; idx < top; ++idx) {
      answer[idx] = st.nextToken();
    }
    return answer;
  }

  public static String[] splitNameKeepParenteses(final String name) {
    final StringTokenizer st = new StringTokenizer(name);
    final int top = st.countTokens();
    final String[] answer = new String[top];
    for (int idx = 0; idx < top; ++idx) {
      answer[idx] = st.nextToken().toUpperCase();
    }
    return answer;
  }

  public static String assembleName(final String[] names) {
    final StringBuilder sb = new StringBuilder(200);
    final int top = names.length;
    for (int idx = 0; idx < top; ++idx) {
      sb.append(names[idx]);
      if (idx < top - 1) {
        sb.append(' ');
      }
    }
    return sb.toString();
  }

  public static boolean match(final String pattern, final String candidate, final boolean allowPrefixes) {
    if (pattern == null || "%".equals(pattern) || "".equals(pattern)) {
      return true;
    }
    final String[] patternParts = splitName(normalizeName(pattern));
    final String[] candidateParts = splitName(normalizeName(candidate));
    final int pTop = patternParts.length;
    final int cTop = candidateParts.length;

    // Pattern has more parts than candidate? Not a match!
    //
    if (pTop > cTop) {
      return false;
    }

    int prefixSize = 0;
    if (allowPrefixes) {
      for (; prefixSize < cTop; prefixSize++) {
        if (candidateParts[prefixSize].startsWith(patternParts[0])) {
          break;
        }
      }
    }
    if (prefixSize + pTop > cTop) {
      // We did get a match at our first part of the pattern, but too
      // "late", so
      // we can't possible match all parts of the pattern in the rest of
      // the candidate.
      return false;
    }

    // Check if every token in "pattern" is a substring of every token in
    // "candidate"
    //
    for (int idx = 0; idx < pTop; ++idx) {
      if (!candidateParts[idx + prefixSize].startsWith(patternParts[idx])) {
        return false;
      }
    }

    // If we made it this far, everything matched!
    //
    return true;
  }

  public static String stripSuffix(final String name) {
    final int p = name.indexOf('/');
    return p == -1 ? name : name.substring(0, p > 0 ? p - 1 : 0).trim();
  }

  public static String addSuffix(String name, final String suffix) {
    name = stripSuffix(name);
    return suffix.length() != 0 ? name + " /" + suffix : name;
  }
}
