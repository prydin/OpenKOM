/*
 * Created on Aug 24, 2004
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.utils;

import java.io.PrintWriter;
import java.util.ArrayList;

/** @author Pontus Rydin */
public class HeaderPrinter {
  private final ArrayList<Entry> m_headers = new ArrayList<Entry>();

  public void addHeader(final String label, final int width, final boolean rightJustify) {
    m_headers.add(
        rightJustify
            ? new RightJustifiedEntry(label, width)
            : new LeftJustifiedEntry(label, width));
  }

  public void addSpace(final int width) {
    m_headers.add(new SpaceEntry(width));
  }

  public void printOn(final PrintWriter out) {
    final int top = m_headers.size();
    for (int idx = 0; idx < top; ++idx) {
        m_headers.get(idx).printLabel(out);
    }
    out.println();
    for (int idx = 0; idx < top; ++idx) {
        m_headers.get(idx).printUnderline(out);
    }
    out.println();
  }

  private abstract static class Entry {
    protected final String m_label;

    protected final int m_width;

    public Entry(final String label, final int width) {
      m_label = label;
      m_width = width;
    }

    public abstract void printLabel(PrintWriter out);

    public abstract void printUnderline(PrintWriter out);
  }

  private static class LeftJustifiedEntry extends Entry {
    public LeftJustifiedEntry(final String label, final int width) {
      super(label, width);
    }

    @Override
    public void printLabel(final PrintWriter out) {
      PrintUtils.printLeftJustified(out, m_label, m_width);
    }

    @Override
    public void printUnderline(final PrintWriter out) {
      PrintUtils.printRepeated(out, '-', m_label.length());
      PrintUtils.printRepeated(out, ' ', m_width - m_label.length());
    }
  }

  private static class RightJustifiedEntry extends Entry {
    public RightJustifiedEntry(final String label, final int width) {
      super(label, width);
    }

    @Override
    public void printLabel(final PrintWriter out) {
      PrintUtils.printRightJustified(out, m_label, m_width);
    }

    @Override
    public void printUnderline(final PrintWriter out) {
      PrintUtils.printRepeated(out, ' ', m_width - m_label.length());
      PrintUtils.printRepeated(out, '-', m_label.length());
    }
  }

  private static class SpaceEntry extends Entry {
    public SpaceEntry(final int width) {
      super("", width);
    }

    @Override
    public void printLabel(final PrintWriter out) {
      PrintUtils.printRepeated(out, ' ', m_width);
    }

    @Override
    public void printUnderline(final PrintWriter out) {
      PrintUtils.printRepeated(out, ' ', m_width);
    }
  }
}
