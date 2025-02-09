/*
 * Created on Jun 17, 2004
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.frontend.text.editor;

import java.util.StringTokenizer;

/** @author Pontus Rydin */
public class StandardWordWrapper implements WordWrapper {
  private final StringTokenizer m_paraTokenizer;
  private final int m_width;
  private int m_offset;
  private String m_paragraph;
  private int m_start = 0;
  private boolean m_inParagraph = false;
  private Character breakCharacter = null;

  public static class Factory implements WordWrapperFactory {
    public WordWrapper create(String text, int width) {
      return new StandardWordWrapper(text, width);
    }

    public WordWrapper create(String text, int width, int offset) {
      return new StandardWordWrapper(text, width, offset);
    }
  }

  protected StandardWordWrapper(String content, int width) {
    this(content, width, 0);
  }

  protected StandardWordWrapper(String content, int width, int offset) {
    m_paraTokenizer = new StringTokenizer(content, "\n", true);

    // Negative widths make no sense, set to 1 in that case.
    //
    if (width > 0) m_width = width - 1;
    else m_width = 1;

    // An offset larger than width means that the previous line
    // might be force-wrapped. We don't care, we just observe that
    // the remainder of offset/width will always be smaller than width
    // and always the number we want.
    //
    m_offset = offset % m_width;
  }

  public String nextLine() {
    for (; ; ) {
      String answer = this.innerNextLine();
      if (answer == null) {
        return null;
      }

      // Skip "implicit" newline at end of paragraph
      //
      if (answer.length() == 0) {
        if (m_inParagraph) {
          // Skip to next paragraph
          //
          m_inParagraph = false;
          continue;
        }
      } else {
        m_inParagraph = true;
      }
      return answer;
    }
  }

  protected String innerNextLine() {
    breakCharacter = null;
    if (m_paragraph == null) {
      if (!m_paraTokenizer.hasMoreTokens()) return null;
      m_paragraph = m_paraTokenizer.nextToken();
      int top = m_paragraph.length();
      if (m_paragraph.endsWith("\n")) --top;
      m_paragraph = m_paragraph.substring(0, top);
      m_start = 0;
    }
    int top = m_paragraph.length();

    // Does the rest of the line fit within the limits?
    //
    if (m_offset > 0) // Explicitly handle remaining offset
    {
      if ((top - m_start) + m_offset <= m_width) {
        String answer = m_paragraph.substring(m_start);
        m_paragraph = null;
        m_offset = 0; // The line fit with offset - we're done with it.
        return answer;
      }
    } else {
      if (top - m_start <= m_width) {
        String answer = m_paragraph.substring(m_start);
        m_paragraph = null;
        return answer;
      }
    }

    // Paragraph too long. Split it.
    // Find a good point to split it.
    //
    if (m_offset > 0) // Explicitly handle remaining offset
    {
      top = m_start + m_width - m_offset;
      m_offset = 0; // The line will fit with offset - we're done with it.
    } else {
      top = m_start + m_width;
    }

    int p = top; // By default: Break at end
    boolean advance = false;
    for (int idx = top; idx >= m_start; --idx) {
      char ch = m_paragraph.charAt(idx);
      if (ch == ' ' || ch == '-') {
        p = idx + 1;
        breakCharacter = ch;
        break;
      }
    }

    // Cut string and advance pointer
    //
    String answer = m_paragraph.substring(m_start, p);

    // If the above loop defaulted, we might be breaking in the middle of a
    // word, in which case we should not advance the pointer, since there is
    // no space to strip away.
    //
    m_start = advance ? p + 1 : p;

    if (m_start >= m_paragraph.length()) m_paragraph = null;
    return answer;
  }

  public Character getBreakCharacter() {
    return breakCharacter;
  }
}
