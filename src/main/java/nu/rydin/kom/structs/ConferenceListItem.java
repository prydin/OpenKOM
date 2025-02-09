/*
 * Created on Sep 7, 2004
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.structs;

import java.io.Serializable;
import java.sql.Timestamp;

/** @author Pontus Rydin */
public class ConferenceListItem extends NameAssociation implements Serializable {
  static final long serialVersionUID = 2005;

  private final Timestamp created;

  private final Timestamp lastActive;

  private final boolean member;

  private final boolean owner;

  private final int order;

  public ConferenceListItem(
      long id,
      Name name,
      Timestamp created,
      Timestamp lastActive,
      boolean member,
      boolean owner,
      int order) {
    super(id, name);
    this.created = created;
    this.lastActive = lastActive;
    this.member = member;
    this.owner = owner;
    this.order = order;
  }

  public Timestamp getCreated() {
    return created;
  }

  public Timestamp getLastActive() {
    return lastActive;
  }

  public boolean isMember() {
    return member;
  }

  public boolean isOwner() {
    return owner;
  }

  public int getOrder() {
    return order;
  }
}
