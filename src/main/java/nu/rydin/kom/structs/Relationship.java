/*
 * Created on Oct 12, 2004
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.structs;

import java.io.Serializable;

/** @author Pontus Rydin */
public class Relationship implements Serializable {
  static final long serialVersionUID = 2005;

  private final long id;
  private final long referer;
  private final long referee;
  private final int kind;
  private final long flags;

  /**
   * @param id
   * @param referer
   * @param referee
   * @param kind
   * @param flags
   */
  public Relationship(final long id, final long referer, final long referee, final int kind, final long flags) {
    super();
    this.id = id;
    this.referer = referer;
    this.referee = referee;
    this.kind = kind;
    this.flags = flags;
  }

  public long getFlags() {
    return flags;
  }

  public long getId() {
    return id;
  }

  public int getKind() {
    return kind;
  }

  public long getReferee() {
    return referee;
  }

  public long getReferer() {
    return referer;
  }
}
