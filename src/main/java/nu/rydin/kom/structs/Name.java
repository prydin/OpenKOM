/*
 * Created on Sep 4, 2004
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.structs;

import java.io.Serializable;
import nu.rydin.kom.backend.data.NameManager;
import nu.rydin.kom.constants.Visibilities;

/** @author Pontus Rydin */
public class Name implements Serializable {
  static final long serialVersionUID = 2005;

  private static Name s_emptyName = new Name("", Visibilities.PUBLIC, NameManager.UNKNOWN_KIND);
  private String m_name;

  private short m_visibility;

  private short m_kind;

  public static Name emptyName() {
    return s_emptyName;
  }

  public Name(String name, short visibility, short kind) {
    m_name = name;
    m_visibility = visibility;
    m_kind = kind;
  }

  public String getName() {
    return m_name;
  }

  public short getVisibility() {
    return m_visibility;
  }

  public short getKind() {
    return m_kind;
  }

  public void hideName() {
    m_name = "";
    m_visibility = Visibilities.PROTECTED;
  }

  public String toString() {
    return m_name.length() > 0 ? m_name : "(PROTECTED)";
  }

  public boolean equals(Object o) {
    if (!(o instanceof Name)) return false;
    Name n = (Name) o;
    if (n.m_name == m_name) return true;
    if (n.m_name == null) return false;
    return ((Name) o).m_name.equals(m_name);
  }

  public int hashCode() {
    return m_name != null ? m_name.hashCode() : 0;
  }
}
