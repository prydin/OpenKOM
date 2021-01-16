/*
 * Created on Oct 25, 2003
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.backend;

import java.io.PrintStream;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import nu.rydin.kom.backend.data.ConferenceManager;
import nu.rydin.kom.backend.data.MembershipManager;
import nu.rydin.kom.exceptions.ObjectNotFoundException;
import nu.rydin.kom.structs.ConferenceInfo;
import nu.rydin.kom.structs.MembershipInfo;
import nu.rydin.kom.structs.MessageRange;
import nu.rydin.kom.structs.MessageRangeList;

/**
 * @author Pontus Rydin
 * @author Jepson
 */
public class MembershipList {
  /** Memberships keyed by conference */
  private final Map<Long, MembershipInfo> m_conferenceTable = new HashMap<>();

  /** Memberships in the order they are prioritized */
  private final MembershipInfo[] m_order;

  /** Memberships where the list of read messages has to be saved */
  private final Set<MembershipInfo> m_dirty = new HashSet<>();

  /**
   * Creates a <tt>MembershipList</tt> based on an array or <tt>MembershipInfo</rr>
   *
   * @param memberships
   */
  public MembershipList(final MembershipInfo[] memberships) {
    final int top = memberships.length;
    m_order = new MembershipInfo[top];
    for (int idx = 0; idx < top; ++idx) {
      final MembershipInfo each = memberships[idx];
      final long conf = each.getConference();
      m_order[idx] = each;
      m_conferenceTable.put(conf, each);
    }
  }

  public MembershipInfo getOrNull(final long conference) {
    return m_conferenceTable.get(conference);
  }

  public MembershipInfo get(final long conference) throws ObjectNotFoundException {
    final MembershipInfo mi = getOrNull(conference);
    if (mi == null) {
      throw new ObjectNotFoundException("Membership conference=" + conference);
    }
    return mi;
  }

  public void markAsRead(final long conference, final int localnum) {
    final MembershipInfo mi = m_conferenceTable.get(conference);
    if (mi == null) {
      return; // We're not members, so we don't care!
    }

    // Update ranges and mark as dirty
    //
    final MessageRangeList l = mi.getReadMessages();
    mi.setReadMessages(
        l == null ? new MessageRangeList(new MessageRange(localnum, localnum)) : l.add(localnum));
    m_dirty.add(mi);
  }

  public void markAsUnread(final long conference, final int localnum) {
    final MembershipInfo mi = m_conferenceTable.get(conference);
    if (mi == null) {
      return; // We're not members, so we don't care!
    }

    // Update ranges and mark as dirty
    //
    final MessageRangeList l = mi.getReadMessages();
    mi.setReadMessages(l == null ? null : l.subtract(localnum));
    m_dirty.add(mi);
  }

  public boolean markAsReadEx(final long conference, final int localnum) throws ObjectNotFoundException {
    if (!isUnread(conference, localnum)) {
      return false;
    } else {
      markAsRead(conference, localnum);
      return true;
    }
  }

  public synchronized int countUnread(final long conference, final ConferenceManager cm)
      throws ObjectNotFoundException, SQLException {
    final ConferenceInfo ci = cm.loadConference(conference);
    final MessageRange total = new MessageRange(ci.getFirstMessage(), ci.getLastMessage());
    final MembershipInfo mi = m_conferenceTable.get(conference);
    if (mi == null) {
      return 0;
    }

    // Check that the list of unread messages is fully contained
    // in the list of existing messages. If not, adjust!
    //
    MessageRangeList read = mi.getReadMessages();
    if (read != null && !read.containedIn(total)) {
      // Ooops... List of read messages is not contained by the list of
      // what we think are existing messages. Someone has deleted a message
      // and we need to adjust the list of read messages
      //
      read = read.intersect(total);
      mi.setReadMessages(read);
      m_dirty.add(mi);
    }
    return total.countOverlapping(read);
  }

  public int getNextMessageInConference(final long confId, final ConferenceManager cm)
      throws ObjectNotFoundException, SQLException {
    final MembershipInfo mi = get(confId);
    final ConferenceInfo ci = cm.loadConference(confId);
    final MessageRangeList mr = mi.getReadMessages();
    final int min = ci.getFirstMessage();
    return mr == null ? (min != 0 ? min : -1) : mr.getFirstUnread(min, ci.getLastMessage());
  }

  public boolean isUnread(final long confId, final int num) throws ObjectNotFoundException {
    final MembershipInfo mi = get(confId);
    final MessageRangeList mr = mi.getReadMessages();
    return mr == null || !mr.includes(num);
  }

  public void changeRead(final long confId, final int low, final int high) throws ObjectNotFoundException {
    final MembershipInfo mi = get(confId);
    mi.setReadMessages(new MessageRangeList(new MessageRange(low, high)));
    m_dirty.add(mi);
  }

  public long getFirstConferenceWithUnreadMessages(final ConferenceManager cm) throws SQLException {
    return innerNextConferenceWithUnreadMessages(0, cm);
  }

  public long getNextConferenceWithUnreadMessages(final long startId, final ConferenceManager cm)
      throws SQLException {
    // Find the first conference. Linear search. Believe me: It's
    // fast enough in this case.
    //
    final int top = m_order.length;

    // If the first conference was not found, "offset" will remain at zero,
    // meaining that we start looking from the very first conference we're
    // a member of. This is useful when the first conference is deleted.
    // Typically, when this happens, the search will commence at the user's
    // private mailbox.
    //
    int offset = 0;
    for (int idx = 0; idx < top; ++idx) {
      if (m_order[idx].getConference() == startId) {
        offset = idx + 1;
        break;
      }
    }
    return innerNextConferenceWithUnreadMessages(offset, cm);
  }

  private long innerNextConferenceWithUnreadMessages(final int startIndex, final ConferenceManager cm)
      throws SQLException {
    // Find the first conference with unread messages
    //
    final int top = m_order.length;
    for (int idx = 0; idx < top; ++idx) {
      final MembershipInfo each = m_order[(idx + startIndex) % top];
      final long id = each.getConference();

      try {
        if (countUnread(id, cm) > 0) {
          return id;
        }
      } catch (final ObjectNotFoundException e) {
        // This conference could not be found. It's probably been
        // deleted, so just continue searching.
      }
    }

    // Nothing found
    //
    return -1;
  }

  public void save(final long userId, final MembershipManager mm) throws SQLException {
    for (final Iterator<MembershipInfo> itor = m_dirty.iterator(); itor.hasNext(); ) {
      final MembershipInfo each = itor.next();
      try {
        mm.updateMarkers(userId, each.getConference(), each.getReadMessages());
      } catch (final ObjectNotFoundException e) {
        // TODO: This probably means that the conference has
        // been deleted. Should we try to do anything intelligent here?
      }
      itor.remove();
    }
  }

  public MembershipInfo[] getMemberships() {
    return m_order;
  }

  public void printDebugInfo(final PrintStream out) {
    for (final MembershipInfo each : m_order) {
      out.println("Conf: " + each.getConference());
      out.print("Read markers:");
      final MessageRangeList markers = each.getReadMessages();
      MessageRangeList rl = markers;
      while (rl != null) {
        final MessageRange r = rl.getRange();
        out.print(r.getMin() + "-" + r.getMax());
        rl = (MessageRangeList) rl.next();
        if (rl != markers) {
          out.print(", ");
        } else {
          break;
        }
      }
      out.println();
      out.println();
    }
  }
}
