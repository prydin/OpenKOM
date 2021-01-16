/*
 * Created on Oct 5, 2003
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.utils;

import java.io.PrintWriter;

/** @author Pontus Rydin */
public class PrintUtils {
  public static void printColumn(final PrintWriter out, final String message, final int colLength) {
    out.print(message);
	  PrintUtils.printRepeated(out, ' ', colLength - message.length());
  }

  public static void printLabelledIfDefined(
		  final PrintWriter out, final String label, final int colLength, final String value) {
    if (value == null || value.length() == 0) {
		return;
	}
	  PrintUtils.printLabelled(out, label, colLength, value);
  }

  public static void printLabelled(final PrintWriter out, final String label, final int colLength, final String value) {
	  PrintUtils.printColumn(out, label, colLength);
    out.println(value);
  }

  public static void printRepeated(final PrintWriter out, final char ch, final int n) {
    for (int idx = 0; idx < n; ++idx) {
		out.print(ch);
	}
  }

  public static void printLeftJustified(final PrintWriter out, String s, final int width) {
    if (s.length() > width) {
		s = s.substring(0, width);
	}
    out.print(s);
	  PrintUtils.printRepeated(out, ' ', width - s.length());
  }

  public static void printRightJustified(final PrintWriter out, String s, final int width) {
    if (s.length() > width) {
		s = s.substring(0, width);
	}
	  PrintUtils.printRepeated(out, ' ', width - s.length());
    out.print(s);
  }

  public static void printIndented(final PrintWriter out, final String s, final int width, final int indentWidth) {
    final StringBuffer buffer = new StringBuffer(width);
    for (int i = 0; i < indentWidth; ++i) {
		buffer.append(' ');
	}
	  PrintUtils.printIndented(out, s, width, buffer.toString());
  }

  public static void printIndented(
		  final PrintWriter out, final String s, final int width, final int initialIndentWidth, final int indentWidth) {
    final StringBuffer buffer1 = new StringBuffer(width);
    for (int i = 0; i < initialIndentWidth; ++i) {
		buffer1.append(' ');
	}
    final StringBuffer buffer2 = new StringBuffer(width);
    for (int i = 0; i < indentWidth; ++i) {
		buffer2.append(' ');
	}
	  PrintUtils.printIndented(out, s, width, buffer1.toString(), buffer2.toString());
  }

  public static void printIndented(final PrintWriter out, final String s, final int width, final String indent) {
	  PrintUtils.printIndented(out, s, width, indent, indent);
  }

  public static void printIndented(
		  final PrintWriter out, final String s, final int width, final String initialIndent, final String indent) {
    final String[] p = s.split(" ");
    int rowLength = initialIndent.length();
    int wordLength;
    String thisRow = initialIndent;
    String thisWord;
    for (int i = 0; i < p.length; ++i) {
      thisWord = p[i];
      wordLength = thisWord.length() + 1;
      if (rowLength + wordLength < width) {
        rowLength += wordLength;
        thisRow += thisWord;
        thisRow += " ";
      } else {
        out.println(thisRow);
        thisRow = indent + thisWord + " ";
        rowLength = /* indentLength + */ wordLength;
        continue; //
      }
      if (thisWord.endsWith("\r") || thisWord.endsWith("\n")) //
      { //
        out.print(thisRow);
        thisRow = indent;
        rowLength = 0 /* indentLength */;
        continue;
      }
    }
    if (rowLength != 0) {
      out.println(thisRow);
    }
  }
}
