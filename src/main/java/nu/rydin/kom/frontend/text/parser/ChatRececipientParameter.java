/*
 * Created on Sep 13, 2004
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.frontend.text.parser;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import nu.rydin.kom.backend.NameUtils;
import nu.rydin.kom.backend.ServerSession;
import nu.rydin.kom.backend.data.NameManager;
import nu.rydin.kom.exceptions.AmbigiousAndNotLoggedInException;
import nu.rydin.kom.exceptions.KOMException;
import nu.rydin.kom.exceptions.NotLoggedInException;
import nu.rydin.kom.exceptions.ObjectNotFoundException;
import nu.rydin.kom.frontend.text.Context;
import nu.rydin.kom.frontend.text.NamePicker;
import nu.rydin.kom.structs.NameAssociation;
import nu.rydin.kom.structs.UserListItem;

/** @author Pontus Rydin */
public class ChatRececipientParameter extends NamedObjectParameter {
  public static final NameAssociation ALL_USERS =
      new NameAssociation(-1, "alla", NameManager.UNKNOWN_KIND);

  public ChatRececipientParameter(final String missingObjectQuestionKey, final boolean isRequired) {
    super(missingObjectQuestionKey, isRequired);
  }

  public ChatRececipientParameter(final boolean isRequired) {
    super(isRequired);
  }

  @Override
  public Object resolveFoundObject(final Context context, final Match match)
      throws KOMException, IOException, InterruptedException {
    // First, get the list of logged in users and extract the names
    //
    String pattern = match.getMatchedString();

    if (pattern.equals("*")) {
      return ALL_USERS;
    }

    final ServerSession session = context.getSession();

    // Use a set since we want each name only once, even if the
    // user is logged in multiple times
    //
    final Set<NameAssociation> set = new HashSet<>();
    if (pattern.startsWith("*")) {
      // Argument was prefixed by "*". That means, we want to select
      // recipient from the list of conferences.
      pattern = pattern.substring(1); // Remove "*".

      final NameAssociation[] conferences =
          session.getAssociationsForPatternAndKind(pattern, NameManager.CONFERENCE_KIND);
      Collections.addAll(set, conferences);
    } else {
      // Otherwise, we select it from the list of logged in users.

      final long numId = getNamedObjectIdIfPresent(pattern);
      final UserListItem[] users = session.listLoggedInUsers();
      for (final UserListItem user : users) {
        final NameAssociation name = user.getUser();

        // First, see if we were passed a numeric ID instead of a name; if so, we
        // won't have to call NameUtils.match().
        //
        if (-1 != numId) {
          if (numId == name.getId()) {
            set.add(name);
          }
        } else {
          if (NameUtils.match(pattern, name.getName().getName(), false)) {
            set.add(name);
          }
        }
      }
    }

    // What did we get?
    //
    switch (set.size()) {
      case 0:
        {
          // Either the object doesn't exist, or the it represents
          // a user the wasn't logged in. Find out what the problem
          // is!
          if (pattern.startsWith("*")) {
            pattern = pattern.substring(1); // Remove "*".
            throw new ObjectNotFoundException(pattern);
          } else {
            final NameAssociation[] names =
                session.getAssociationsForPatternAndKind(pattern, NameManager.USER_KIND);
            if (names.length == 0) {
              throw new ObjectNotFoundException(pattern);
            } else if (names.length == 1) {
              throw new NotLoggedInException(names[0].getName().toString());
            } else {
              throw new AmbigiousAndNotLoggedInException(pattern);
            }
          }
          //
        }
      case 1:
        return set.iterator().next();
      default:
        {
          final NameAssociation[] names = new NameAssociation[set.size()];
          set.toArray(names);
          return NamePicker.pickName(names, context);
        }
    }
  }

  @Override
  protected boolean isValidName(final String name) {
    return (super.isValidName(name)
        || name.equals("*")
        || (name.startsWith("*") && super.isValidName(name.substring(1))));
  }

  @SuppressWarnings("finally")
  private long getNamedObjectIdIfPresent(final String pattern) {
    long l = -1;
    try {
      l = Long.parseLong(pattern);
    } finally {
      return l;
    }
  }
}
