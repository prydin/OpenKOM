/*
 * Created on Oct 5, 2003
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.backend;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import nu.rydin.kom.structs.Name;
import nu.rydin.kom.structs.NameAssociation;

/** @author Pontus Rydin */
public class SQLUtils {
  public static long[] extractLongs(final ResultSet rs, final int index) throws SQLException {
    final List<Long> l = new ArrayList<>();
    while (rs.next()) {
      l.add(rs.getLong(index));
    }
    final int top = l.size();
    final long[] answer = new long[top];
    for (int idx = 0; idx < top; ++idx) {
      answer[idx] = l.get(idx);
    }
    return answer;
  }

  public static int[] extractInts(final ResultSet rs, final int index) throws SQLException {
    final List<Integer> l = new ArrayList<>();
    while (rs.next()) {
      l.add(rs.getInt(index));
    }
    final int top = l.size();
    final int[] answer = new int[top];
    for (int idx = 0; idx < top; ++idx) {
      answer[idx] = l.get(idx);
    }
    return answer;
  }

  public static Name[] extractStrings(
          final ResultSet rs, final int nameIndex, final int visibilityIndex, final int kindIndex, final String pattern)
      throws SQLException {
    final List<Name> l = new ArrayList<>();
    while (rs.next()) {
      final String name = rs.getString(nameIndex);
      if (NameUtils.match(pattern, name, false)) {
        l.add(new Name(name, rs.getShort(visibilityIndex), rs.getShort(kindIndex)));
      }
    }
    final int top = l.size();
    final Name[] answer = new Name[top];
    l.toArray(answer);
    return answer;
  }

  public static NameAssociation[] extractNames(
          final ResultSet rs, final int idIndex, final int nameIndex, final int visibilityIndex, final int kindIndex, final String pattern)
      throws SQLException {
    final List<NameAssociation> l = new ArrayList<>();
    while (rs.next()) {
      final String name = rs.getString(nameIndex);
      if (pattern == null || NameUtils.match(pattern, name, false)) {
        l.add(
            new NameAssociation(
                rs.getLong(idIndex),
                new Name(name, rs.getShort(visibilityIndex), rs.getShort(kindIndex))));
      }
    }
    final int top = l.size();
    final NameAssociation[] answer = new NameAssociation[top];
    l.toArray(answer);
    return answer;
  }
}
