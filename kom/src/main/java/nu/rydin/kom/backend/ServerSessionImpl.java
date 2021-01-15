/*
 * Created on Oct 27, 2003
 *
 * Distributed under the GPL license.
 */
package nu.rydin.kom.backend;

import edu.oswego.cs.dl.util.concurrent.Mutex;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.StringTokenizer;
import nu.rydin.kom.backend.data.ConferenceManager;
import nu.rydin.kom.backend.data.FileManager;
import nu.rydin.kom.backend.data.MembershipManager;
import nu.rydin.kom.backend.data.MessageLogManager;
import nu.rydin.kom.backend.data.MessageManager;
import nu.rydin.kom.backend.data.NameManager;
import nu.rydin.kom.backend.data.RelationshipManager;
import nu.rydin.kom.backend.data.UserManager;
import nu.rydin.kom.constants.Activities;
import nu.rydin.kom.constants.ChatRecipientStatus;
import nu.rydin.kom.constants.CommandSuggestions;
import nu.rydin.kom.constants.ConferencePermissions;
import nu.rydin.kom.constants.FileProtection;
import nu.rydin.kom.constants.FilterFlags;
import nu.rydin.kom.constants.MessageAttributes;
import nu.rydin.kom.constants.MessageLogKinds;
import nu.rydin.kom.constants.RelationshipKinds;
import nu.rydin.kom.constants.SettingKeys;
import nu.rydin.kom.constants.UserFlags;
import nu.rydin.kom.constants.UserPermissions;
import nu.rydin.kom.constants.Visibilities;
import nu.rydin.kom.events.BroadcastAnonymousMessageEvent;
import nu.rydin.kom.events.BroadcastMessageEvent;
import nu.rydin.kom.events.ChatAnonymousMessageEvent;
import nu.rydin.kom.events.ChatMessageEvent;
import nu.rydin.kom.events.DetachRequestEvent;
import nu.rydin.kom.events.Event;
import nu.rydin.kom.events.EventTarget;
import nu.rydin.kom.events.MessageDeletedEvent;
import nu.rydin.kom.events.NewMessageEvent;
import nu.rydin.kom.events.ReloadUserProfileEvent;
import nu.rydin.kom.events.UserAttendanceEvent;
import nu.rydin.kom.exceptions.AlreadyMemberException;
import nu.rydin.kom.exceptions.AmbiguousNameException;
import nu.rydin.kom.exceptions.AuthenticationException;
import nu.rydin.kom.exceptions.AuthorizationException;
import nu.rydin.kom.exceptions.BadPasswordException;
import nu.rydin.kom.exceptions.DuplicateNameException;
import nu.rydin.kom.exceptions.EmailRecipientNotRecognizedException;
import nu.rydin.kom.exceptions.EmailSenderNotRecognizedException;
import nu.rydin.kom.exceptions.MessageNotFoundException;
import nu.rydin.kom.exceptions.NoCurrentMessageException;
import nu.rydin.kom.exceptions.NoMoreMessagesException;
import nu.rydin.kom.exceptions.NoMoreNewsException;
import nu.rydin.kom.exceptions.NoRulesException;
import nu.rydin.kom.exceptions.NotAReplyException;
import nu.rydin.kom.exceptions.NotMemberException;
import nu.rydin.kom.exceptions.ObjectNotFoundException;
import nu.rydin.kom.exceptions.OriginalsNotAllowedException;
import nu.rydin.kom.exceptions.RepliesNotAllowedException;
import nu.rydin.kom.exceptions.SelectionOverflowException;
import nu.rydin.kom.exceptions.UnexpectedException;
import nu.rydin.kom.structs.Bookmark;
import nu.rydin.kom.structs.ConferenceInfo;
import nu.rydin.kom.structs.ConferenceListItem;
import nu.rydin.kom.structs.ConferencePermission;
import nu.rydin.kom.structs.Envelope;
import nu.rydin.kom.structs.FileStatus;
import nu.rydin.kom.structs.GlobalMessageSearchResult;
import nu.rydin.kom.structs.LocalMessageSearchResult;
import nu.rydin.kom.structs.MembershipInfo;
import nu.rydin.kom.structs.MembershipListItem;
import nu.rydin.kom.structs.Message;
import nu.rydin.kom.structs.MessageAttribute;
import nu.rydin.kom.structs.MessageHeader;
import nu.rydin.kom.structs.MessageLocator;
import nu.rydin.kom.structs.MessageLogItem;
import nu.rydin.kom.structs.MessageOccurrence;
import nu.rydin.kom.structs.MessageSearchResult;
import nu.rydin.kom.structs.Name;
import nu.rydin.kom.structs.NameAssociation;
import nu.rydin.kom.structs.NamedObject;
import nu.rydin.kom.structs.ReadLogItem;
import nu.rydin.kom.structs.Relationship;
import nu.rydin.kom.structs.SessionState;
import nu.rydin.kom.structs.SystemInformation;
import nu.rydin.kom.structs.UnstoredMessage;
import nu.rydin.kom.structs.UserInfo;
import nu.rydin.kom.structs.UserListItem;
import nu.rydin.kom.structs.UserLogItem;
import nu.rydin.kom.utils.FileUtils;
import nu.rydin.kom.utils.FilterUtils;
import nu.rydin.kom.utils.Logger;

/**
 * @author <a href=mailto:pontus@rydin.nu>Pontus Rydin</a>
 * @author <a href=mailto:jepson@xyzzy.se>Jepson</a>
 */
public class ServerSessionImpl implements ServerSession, EventTarget, EventSource {
  private static final int MAX_SELECTION = 1000;
  /** User variables shared across sessions for the same user */
  protected final UserContext m_userContext;
  /** Time of login */
  private final long m_loginTime;
  /** The session id (assigned by factory) */
  private final int m_sessionId;
  /** The currently active sessions */
  private final SessionManager m_sessions;
  /** List of incoming events */
  private final LinkedList<Event> m_incomingEvents = new LinkedList<>();
  /**
   * List of deferred events, i.e. events that will be sent once the current transaction is
   * committed.
   */
  private final LinkedList<DeferredEvent> m_deferredEvents = new LinkedList<>();
  /** Usage statistics */
  private final UserLogItem m_stats;
  /** Mutex controlling session access */
  private final Mutex m_mutex = new Mutex();

  /** The user currently logged in */
  private final long m_userId;

  private final SelectedMessages m_selectedMessages = new SelectedMessages();
  /** Timestamp of last heartbeat */
  protected long m_lastHeartbeat = System.currentTimeMillis();
  /** Current conference id, or -1 if it could not be determined */
  private long m_currentConferenceId;

  private short m_clientType;
  /*
   * Id of last message read, or -1 if no message has been read yet.
   */
  private long m_lastReadMessageId = -1;
  /** Reply stack. */
  private ReplyStackFrame m_replyStack = null;
  /** The DataAccess object to use. Reset between transactions */
  private DataAccess m_da;
  /** Has this session been closed? */
  private boolean m_closed = false;
  /** Last suggested command */
  private short m_lastSuggestedCommand = -1;
  /**
   * Are we valid? If an attempt to gracefully shut down a session fails, we may mark a session as
   * invalid, thus prventing any client calls from getting through.
   */
  private boolean m_valid = true;
  /** Backwards-linked list of read message */
  private ReadLogItem m_readLog;
  /** Messages to mark as unread at logout */
  private ReadLogItem m_pendingUnreads;
  /** Last object acted on, used by user list */
  private long m_lastObject;
  /** Free text used by user list, if set by user. */
  private String m_freeActivityText;
  /** Activity types, used by user list. Last activity used to revert from a temp auto change. */
  private short m_currentActivity;

  private short m_lastActivity;

  public ServerSessionImpl(
      final DataAccess da,
      final long userId,
      final int sessionId,
      final short clientType,
      final SessionManager sessions)
      throws UnexpectedException {
    try {
      // Set up statistics collection
      //
      m_stats = new UserLogItem(userId);
      m_stats.setLoggedIn(new Timestamp(System.currentTimeMillis()));

      // We'll need a DataAccess while doing this
      //
      m_da = da;

      // Set up member variables
      //
      m_userId = userId;
      m_clientType = clientType;
      m_loginTime = System.currentTimeMillis();
      m_sessions = sessions;
      m_sessionId = sessionId;
      m_currentActivity = Activities.AUTO;
      m_lastActivity = Activities.AUTO;

      // Create user context
      //
      m_userContext =
          UserContextFactory.getInstance()
              .getOrCreateContext(
                  userId, m_da.getMembershipManager(), m_da.getRelationshipManager());

      // Let's lock the user context while we're manipulating it!
      //
      synchronized (m_userContext) {
        // If we have saved unreads, load them and apply
        //
        try {
          loadUnreadMarkers();
          applyUnreadMarkers();
          m_userContext.saveMemberships(m_da.getMembershipManager());
        } catch (final ObjectNotFoundException e) {
          // No file, just ignore
        }

        // Go to first conference with unread messages
        //
        final long firstConf =
            m_userContext
                .getMemberships()
                .getFirstConferenceWithUnreadMessages(da.getConferenceManager());
        setCurrentConferenceId(firstConf != -1 ? firstConf : userId);

        // Commit. We have to do this manually here, since we're not called through
        // a TransactionalInvocationHandler.
        //
        m_da.commit();
        m_da = null;
      }
    } catch (final SQLException | ObjectNotFoundException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    } finally {
      if (m_da != null) {
        m_da.rollback();
      }
    }
  }

  @Override
  public void finalize() {
    try {
      close();
    } catch (final UnexpectedException e) {
      e.printStackTrace();
    }
  }

  public void acquireMutex() throws InterruptedException {
    m_mutex.acquire();
  }

  public void releaseMutex() {
    m_mutex.release();
  }

  @Override
  public ConferenceInfo getCurrentConference() {
    try {
      return m_da.getConferenceManager().loadConference(m_currentConferenceId);
    } catch (final ObjectNotFoundException | SQLException e) {
      // TODO: What do we do here? The current conference may have been
      // deleted!
      //
      throw new RuntimeException(e);
    } // SQLExceptions here mean that something has gone terribly wrong!
  }

  @Override
  public int getSessionId() {
    return m_sessionId;
  }

  @Override
  public short getClientType() {
    return m_clientType;
  }

  public void setClientType(final short clientType) {
    m_clientType = clientType;
  }

  @Override
  public long getCurrentConferenceId() {
    return m_currentConferenceId;
  }

  @Override
  public void setCurrentConferenceId(final long id) throws UnexpectedException {
    // We might need to save some membership stuff.
    //
    if (m_currentConferenceId != id) {
      leaveConference();
    }

    m_currentConferenceId = id;
  }

  @Override
  public UserInfo getLoggedInUser() {
    try {
      return m_da.getUserManager().loadUser(m_userId);
    } catch (final ObjectNotFoundException | SQLException e) {
      // The logged in user should definately be found!!!
      //
      throw new RuntimeException(e);
    }
  }

  @Override
  public long getLoggedInUserId() {
    return m_userId;
  }

  @Override
  public long getLoginTime() {
    return m_loginTime;
  }

  @Override
  public SessionState getSessionState() {
    try {
      final Map<Long, Long> filterCache = m_userContext.getFilterCache();
      final MembershipList memberships = m_userContext.getMemberships();
      final long conf = getCurrentConferenceId();
      final long user = getLoggedInUserId();
      final ConferenceManager cm = m_da.getConferenceManager();
      final boolean hasFilters = filterCache.size() > 0;

      // While what we found matches a filter...
      //
      for (; ; ) {
        // Do we have any unread mail?
        //
        if ((getLoggedInUser().getFlags1() & UserFlags.PRIORITIZE_MAIL) != 0) {
          try {
            final int nextMail = memberships.getNextMessageInConference(user, cm);
            try {
              if (nextMail != -1) {
                if (hasFilters
                    && applyFiltersForMessage(
                        localToGlobal(user, nextMail), user, nextMail, FilterFlags.MAILS)) {
                  continue;
                }
                return new SessionState(CommandSuggestions.NEXT_MAIL, user, countUnread(user));
              }
            } catch (final MessageNotFoundException e) {
              // Message is probably deleted. Skip it!
              //
              memberships.markAsRead(user, nextMail);
              continue;
            }
          } catch (final ObjectNotFoundException e) {
            // Problem reading next mail. Try conferences instead
            //
            Logger.error(this, "Error finding next mail", e);
          }
        }

        // Do we have any selected messages?
        //
        if (getSelectedMessages().hasUnreadMessages()) {
          return new SessionState(
              CommandSuggestions.NEXT_SELECTED, conf, getSelectedMessages().getUnread());
        }

        // First, try a reply. Then, try any unread message in current conference
        //
        final long nextReply = peekReply();
        if (nextReply != -1) {
          try {
            final SessionState answer =
                new SessionState(CommandSuggestions.NEXT_REPLY, conf, countUnread(conf));
            final MessageOccurrence occ;
            try {
              occ = getMostRelevantOccurrence(conf, nextReply);
            } catch (final MessageNotFoundException e) {
              // Probably deleted. Skip it!
              //
              popReply();
              continue;
            }

            if (hasFilters
                && applyFiltersForMessage(
                    nextReply, occ.getConference(), occ.getLocalnum(), FilterFlags.MESSAGES)) {
              continue;
            }
            return answer;
          } catch (final ObjectNotFoundException e) {
            // Problem getting next reply. Try messages
            //
            Logger.error(this, "Error finding next reply", e);
          }
        }

        // No replies to read. Try a normal message
        //
        int nextMessage = -1;
        try {
          nextMessage = memberships.getNextMessageInConference(conf, cm);
        } catch (final ObjectNotFoundException e) {
          // Can't find current conference, it must have been deleted.
          // Reload memberships and try to go to the next conference.
          //
          try {
            reloadMemberships();
          } catch (final ObjectNotFoundException e2) {
            Logger.error(this, "Strange state!", e);
            return new SessionState(CommandSuggestions.ERROR, -1, 0);
          }
        }

        // So? Did we get anything? Check if that message is filtered.
        //
        try {
          try {
            if (nextMessage != -1) {
              if (hasFilters
                  && applyFiltersForMessage(
                      localToGlobal(conf, nextMessage), conf, nextMessage, FilterFlags.MESSAGES)) {
                continue;
              }
              return new SessionState(CommandSuggestions.NEXT_MESSAGE, conf, countUnread(conf));
            }
          } catch (final MessageNotFoundException e) {
            // Probably deleted. Skip!
            //
            memberships.markAsRead(conf, nextMessage);
            continue;
          }
        } catch (final ObjectNotFoundException e) {
          // Problems finding next message in this conference. Try
          // next conference
          //
          Logger.error(this, "Error when looking for next message", e);
        }

        // Get next conference with unread messages
        //
        final long confId =
            memberships.getNextConferenceWithUnreadMessages(conf, m_da.getConferenceManager());

        // Do we have any unread messages?
        //
        if (confId == -1) {
          return new SessionState(m_lastSuggestedCommand = CommandSuggestions.NO_ACTION, conf, 0);
        }
        return new SessionState(
            m_lastSuggestedCommand = CommandSuggestions.NEXT_CONFERENCE,
            getCurrentConferenceId(),
            0);
      }
    } catch (final SQLException | UnexpectedException e) {
      Logger.error(this, "Strange state!", e);
      return new SessionState(CommandSuggestions.ERROR, -1, 0);
    }
  }

  protected boolean applyFiltersForMessage(
      final long message, final long conference, final int localnum, final long filter)
      throws UnexpectedException {
    try {
      final MessageManager mm = m_da.getMessageManager();
      boolean skip = false;
      MessageHeader mh = null;
      try {
        mh = mm.loadMessageHeader(message);
        skip = m_userContext.userMatchesFilter(mh.getAuthor(), filter);
      } catch (final ObjectNotFoundException e) {
        // Message was deleted. Skip!
        //
        skip = true;
      }

      // So... Should we skip this message?
      //
      if (!skip) {
        return false;
      }

      // Skip this message. Mark it as read.
      //
      try {
        m_userContext.getMemberships().markAsRead(conference, localnum);
      } catch (final ObjectNotFoundException e) {
        // Not found when trying to mark it as read? I guess
        // it has disappeared then!
        //
        Logger.error(this, "Object not found when marking as read", e);
      }
      return true;
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public EventSource getEventSource() {
    return this;
  }

  @Override
  public Envelope readLastMessage()
      throws ObjectNotFoundException, NoCurrentMessageException, UnexpectedException,
          AuthorizationException {
    return innerReadMessage(null);
  }

  @Override
  public MessageHeader getLastMessageHeader()
      throws ObjectNotFoundException, NoCurrentMessageException, UnexpectedException {
    if (m_lastReadMessageId == -1) {
      throw new NoCurrentMessageException();
    }
    try {
      return m_da.getMessageManager().loadMessageHeader(m_lastReadMessageId);
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public Envelope readOriginalMessage()
      throws NoCurrentMessageException, NotAReplyException, ObjectNotFoundException,
          AuthorizationException, UnexpectedException {
    try {
      if (m_lastReadMessageId == -1) {
        throw new NoCurrentMessageException();
      }

      // Retrieve last message read and try to locate the message it replies to
      //
      final MessageManager mm = m_da.getMessageManager();
      final MessageHeader mh = mm.loadMessageHeader(m_lastReadMessageId);
      final long replyTo = mh.getReplyTo();
      if (replyTo == -1) {
        throw new NotAReplyException();
      }

      // Do we have the right to see it?
      //
      assertMessageReadPermissions(replyTo);

      // We now know the message number. Go ahead and load it
      //
      return innerReadMessage(replyTo);
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public short getActivity() {
    return m_currentActivity;
  }

  @Override
  public void setActivity(final short a, final boolean keepLastState) {
    if (keepLastState) {
      m_lastActivity = m_currentActivity;
    }
    m_currentActivity = a;
  }

  @Override
  public String getActivityString() {
    return m_freeActivityText;
  }

  @Override
  public void setActivityString(final String a) {
    m_freeActivityText = a;
  }

  @Override
  public long getLastObject() {
    return m_lastObject;
  }

  @Override
  public void setLastObject(final long o) {
    m_lastObject = o;
  }

  @Override
  public void restoreState() {
    m_currentActivity = m_lastActivity;
    m_lastActivity = Activities.AUTO;
    m_lastObject =
        -1; // Since we're no longer operating on an object, we're surely not fiddling with this one
  }

  @Override
  public void clearStates() {
    m_lastActivity = Activities.AUTO;
    restoreState();
  }

  @Override
  public Envelope readMessage(final MessageLocator message)
      throws ObjectNotFoundException, NoCurrentMessageException, UnexpectedException,
          AuthorizationException {
    return innerReadMessage(resolveLocator(message));
  }

  @Override
  public Envelope readNextMessageInCurrentConference()
      throws NoMoreMessagesException, ObjectNotFoundException, UnexpectedException,
          AuthorizationException {
    return readNextMessage(getCurrentConferenceId());
  }

  @Override
  public Envelope readNextMessage(final long confId)
      throws NoMoreMessagesException, ObjectNotFoundException, UnexpectedException,
          AuthorizationException {
    final MembershipList memberships = m_userContext.getMemberships();
    try {
      // Keep on trying until we've skipped all deleted messages
      //
      for (; ; ) {
        final int next =
            memberships.getNextMessageInConference(confId, m_da.getConferenceManager());
        if (next == -1) {
          throw new NoMoreMessagesException();
        }
        try {
          pushReplies(confId, next);
          return readMessage(new MessageLocator(confId, next));
        } catch (final ObjectNotFoundException e) {
          // We hit a deleted message. Mark it as read
          // and continue.
          //
          memberships.markAsRead(confId, next);
        }
      }
    } catch (final SQLException | NoCurrentMessageException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public Envelope readNextReply()
      throws NoMoreMessagesException, ObjectNotFoundException, UnexpectedException,
          AuthorizationException {
    try {
      final long next = popReply();
      if (next == -1) {
        throw new NoMoreMessagesException();
      }
      pushReplies(next);
      return innerReadMessage(next);
    } catch (final NoCurrentMessageException | SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public long createConference(
      final String fullname,
      final String keywords,
      final int permissions,
      final int nonmemberPermissions,
      final short visibility,
      final long replyConf)
      throws UnexpectedException, AmbiguousNameException, DuplicateNameException,
          AuthorizationException {
    checkRights(UserPermissions.CREATE_CONFERENCE);
    try {
      final long userId = getLoggedInUserId();
      final long confId =
          m_da.getConferenceManager()
              .addConference(
                  fullname,
                  keywords,
                  userId,
                  permissions,
                  nonmemberPermissions,
                  visibility,
                  replyConf);

      // Add membership for administrator
      //
      m_da.getMembershipManager()
          .signup(userId, confId, 0, ConferencePermissions.ALL_PERMISSIONS, 0);

      // Flush membership cache
      //
      reloadMemberships();
      return confId;
    } catch (final SQLException | ObjectNotFoundException | AlreadyMemberException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public ConferenceListItem[] listConferencesByDate() throws UnexpectedException {
    try {
      return (ConferenceListItem[])
          censorNames(m_da.getConferenceManager().listByDate(getLoggedInUserId()));
    } catch (final SQLException | ObjectNotFoundException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  /** Returns a list of conferences, sorted by their normalized name. */
  @Override
  public ConferenceListItem[] listConferencesByName() throws UnexpectedException {
    try {
      return (ConferenceListItem[])
          censorNames(m_da.getConferenceManager().listByName(getLoggedInUserId()));
    } catch (final SQLException | ObjectNotFoundException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public void gotoConference(final long id)
      throws UnexpectedException, ObjectNotFoundException, NotMemberException {
    // Going to the current conference?
    //
    if (id == getCurrentConferenceId()) {
      return;
    }

    // Trying to go to a protected conference?
    //
    if (!isVisible(id)) {
      throw new ObjectNotFoundException("id=" + id);
    }
    try {
      // Are we members?
      //
      if (!m_da.getMembershipManager().isMember(getLoggedInUserId(), id)) {
        throw new NotMemberException(new Object[] {getCensoredName(id).getName()});
      }

      // All set! Go there!
      //
      setCurrentConferenceId(id);

      // Clear reply stack
      //
      m_replyStack = null;
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public long gotoNextConference() throws NoMoreNewsException, UnexpectedException {
    try {
      final long nextId =
          m_userContext
              .getMemberships()
              .getNextConferenceWithUnreadMessages(
                  getCurrentConferenceId(), m_da.getConferenceManager());

      // Going nowhere or going to the same conference? We're outta here!
      //
      if (nextId == -1 || nextId == getCurrentConferenceId()) {
        throw new NoMoreNewsException();
      }

      // Move focus...
      //
      setCurrentConferenceId(nextId);

      // Clear reply stack
      //
      m_replyStack = null;
      return nextId;
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public MessageOccurrence storeMessage(final long conf, final UnstoredMessage msg)
      throws ObjectNotFoundException, AuthorizationException, UnexpectedException {
    return storeReplyAsMessage(conf, msg, -1L);
  }

  @Override
  public void changeContactInfo(final UserInfo ui)
      throws ObjectNotFoundException, UnexpectedException, AuthorizationException {
    try {
      // We're only allowed to change address of ourselves, unless
      // we hold the USER_ADMIN priv.
      //
      final long id = ui.getId();
      if (id != getLoggedInUserId()) {
        checkRights(UserPermissions.USER_ADMIN);
      }
      m_da.getUserManager().changeContactInfo(ui);
      sendEvent(id, new ReloadUserProfileEvent(id));
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public MessageOccurrence storeMail(final long recipient, final UnstoredMessage msg)
      throws ObjectNotFoundException, UnexpectedException, AuthorizationException {
    return storeReplyAsMail(recipient, msg, -1L);
  }

  @Override
  public MessageOccurrence storeReplyAsMessage(
      final long conference, final UnstoredMessage msg, final MessageLocator replyTo)
      throws ObjectNotFoundException, UnexpectedException, AuthorizationException,
          NoCurrentMessageException {
    return storeReplyAsMessage(conference, msg, resolveLocator(replyTo).getGlobalId());
  }

  protected MessageOccurrence storeReplyAsMessage(
      final long author, final long conference, final UnstoredMessage msg, final long replyTo)
      throws ObjectNotFoundException, UnexpectedException, AuthorizationException {
    try {
      // If we're posting on behalf on someone else, we must hold the
      // POST_BY_PROXY privilege
      //
      if (author != getLoggedInUserId()) {
        checkRights(UserPermissions.POST_BY_PROXY);
      }
      final MessageManager mm = m_da.getMessageManager();
      final UserInfo myinf = getLoggedInUser();

      // Check that we have the permission to write here. If it's a reply, we should
      // try check if we have the right to reply. It's ok to be able to reply without
      // being able to write. Great for conferences where users are only allowed to
      // reply to something posted by a moderator.
      //
      if (replyTo == -1) {
        assertConferencePermission(author, conference, ConferencePermissions.WRITE_PERMISSION);
      } else {
        assertConferencePermission(author, conference, ConferencePermissions.REPLY_PERMISSION);
      }

      // Store message in conference
      //
      final MessageOccurrence occ =
          mm.addMessage(
              author,
              getCensoredName(author).getName(),
              conference,
              replyTo,
              msg.getSubject(),
              msg.getBody());

      // Mark message as read unless the user is a narcissist.
      //
      if ((myinf.getFlags1() & UserFlags.NARCISSIST) == 0) {
        markMessageAsRead(conference, occ.getLocalnum());
      }

      // Make this the "current" message
      //
      m_lastReadMessageId = occ.getGlobalId();

      // Notify the rest of the world that there is a new message!
      // Notice that we post the event with OUR user id and not the author's.
      // I think that's the right thing to do...
      //
      broadcastEvent(
          new NewMessageEvent(
              getLoggedInUserId(), occ.getConference(), occ.getLocalnum(), occ.getGlobalId()));

      m_stats.incNumPosted();
      return occ;
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  protected MessageOccurrence storeReplyAsMessage(
      final long conference, final UnstoredMessage msg, final long replyTo)
      throws ObjectNotFoundException, UnexpectedException, AuthorizationException {
    return storeReplyAsMessage(getLoggedInUserId(), conference, msg, replyTo);
  }

  @Override
  public MessageOccurrence storeReplyAsMail(
      final long recipient, final UnstoredMessage msg, final MessageLocator replyTo)
      throws ObjectNotFoundException, UnexpectedException, NoCurrentMessageException,
          AuthorizationException {
    return storeReplyAsMail(recipient, msg, resolveLocator(replyTo).getGlobalId());
  }

  protected MessageOccurrence storeReplyAsMail(
      final long recipient, final UnstoredMessage msg, final long replyTo)
      throws ObjectNotFoundException, UnexpectedException, AuthorizationException {
    return storeReplyAsMail(getLoggedInUserId(), recipient, msg, replyTo);
  }

  protected MessageOccurrence storeReplyAsMail(
      final long author, final long recipient, final UnstoredMessage msg, final long replyTo)
      throws ObjectNotFoundException, UnexpectedException, AuthorizationException {
    try {
      // If we're posting on behalf on someone else, we must hold the
      // POST_BY_PROXY privilege
      //
      if (author != getLoggedInUserId()) {
        checkRights(UserPermissions.POST_BY_PROXY);
      }

      final MessageManager mm = m_da.getMessageManager();
      final UserInfo myinf = getLoggedInUser();

      // Store message in recipient's mailbox
      //
      final MessageOccurrence occ =
          mm.addMessage(
              author,
              getCensoredName(author).getName(),
              recipient,
              replyTo,
              msg.getSubject(),
              msg.getBody());

      // Set the mail attribute with the original recipient. This is used
      // to mark a text as a mail, and to keep track of the original
      // recipient in case the occurrences are lost.
      //
      final String payload =
          MessageAttribute.constructUsernamePayload(
              recipient, getCensoredName(recipient).getName());
      mm.addMessageAttribute(occ.getGlobalId(), MessageAttributes.MAIL_RECIPIENT, payload);

      // Store a copy in sender's mailbox
      //
      if ((myinf.getFlags1() & UserFlags.KEEP_COPIES_OF_MAIL) != 0) {
        final MessageOccurrence copy =
            mm.createMessageOccurrence(
                occ.getGlobalId(),
                MessageManager.ACTION_COPIED,
                author,
                getName(author).getName(),
                author);

        // Mark copy as read unless the user is a narcissist.
        //
        if ((myinf.getFlags1() & UserFlags.NARCISSIST) == 0) {
          markMessageAsRead(getLoggedInUserId(), copy.getLocalnum());
        }

        // Make this the "current" message
        //
        m_lastReadMessageId = occ.getGlobalId();
      }

      // Notify recipient of the new message.
      // Notice that we post the event with OUR user id and not the author's.
      // I think that's the right thing to do...
      //
      sendEvent(
          recipient,
          new NewMessageEvent(
              getLoggedInUserId(), recipient, occ.getLocalnum(), occ.getGlobalId()));

      m_stats.incNumPosted();
      return occ;
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public MessageOccurrence storePresentation(final UnstoredMessage msg, long object)
      throws UnexpectedException, AuthorizationException, ObjectNotFoundException {
    try {
      // Permission checks: We have to be presenting ourselves, a conference
      // we're the administrator of or we have to hold the USER_ADMIN (in case
      // we're presenting a user) or CONFERENCE_ADMIN (in case of a conference)
      //
      if (-1L == object) {
        object = getLoggedInUserId();
      }
      final short kind = getObjectKind(object);
      if (!canManipulateObject(object)) {
        throw new AuthorizationException();
      }
      final long conference =
          m_da.getSettingManager()
              .getNumber(
                  kind == NameManager.CONFERENCE_KIND
                      ? SettingKeys.CONFERENCE_PRESENTATIONS
                      : SettingKeys.USER_PRESENTATIONS);
      final MessageManager mm = m_da.getMessageManager();
      final MessageOccurrence occ =
          mm.addMessage(
              getLoggedInUserId(),
              getCensoredName(getLoggedInUserId()).getName(),
              conference,
              -1,
              msg.getSubject(),
              msg.getBody());

      // Mark as read (unless we're a narcissist)
      //
      if ((getLoggedInUser().getFlags1() & UserFlags.NARCISSIST) == 0) {
        markMessageAsRead(conference, occ.getLocalnum());
      }
      broadcastEvent(
          new NewMessageEvent(
              getLoggedInUserId(), occ.getConference(), occ.getLocalnum(), occ.getGlobalId()));
      m_da.getMessageManager()
          .addMessageAttribute(
              occ.getGlobalId(), MessageAttributes.PRESENTATION, Long.toString(object));

      // Make this the "current" message
      //
      m_lastReadMessageId = occ.getGlobalId();
      return occ;
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public Envelope readTaggedMessage(final short tag, long object)
      throws UnexpectedException, ObjectNotFoundException, AuthorizationException {
    try {
      if (-1 == object) {
        object = getLoggedInUserId();
      }
      return innerReadMessage(m_da.getMessageManager().getTaggedMessage(object, tag));
    } catch (final NoCurrentMessageException | SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public void addMessageAttribute(
      final long message, final short attribute, final String payload, final boolean deleteOld)
      throws UnexpectedException, AuthorizationException {
    try {
      final MessageManager mm = m_da.getMessageManager();

      // Determine conference of message
      //
      final long targetConf = mm.getFirstOccurrence(message).getConference();

      // Check that we have the permission to write there.
      //
      assertConferencePermission(targetConf, ConferencePermissions.WRITE_PERMISSION);

      // If this is the kind of attribute that only the owner can change, we need
      // to check that we own the text (or that we have enough privs).
      //
      if (MessageAttributes.onlyOwner[attribute]) {
        if (mm.loadMessageHeader(message).getAuthor() != getLoggedInUserId()) {
          throw new AuthorizationException();
        }
      }

      // Delete already existing "no comment"
      //
      final long user = getLoggedInUserId();
      if (deleteOld) {
        final MessageAttribute[] attributes = mm.getMessageAttributes(message);
        for (final MessageAttribute messageAttribute : attributes) {
          if (messageAttribute.getKind() == attribute && messageAttribute.getUserId() == user) {
            mm.dropMessageAttribute(messageAttribute.getId(), message);
          }
        }
      }

      mm.addMessageAttribute(message, attribute, payload);
    } catch (final ObjectNotFoundException | SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public void storeNoComment(final MessageLocator message)
      throws UnexpectedException, ObjectNotFoundException, NoCurrentMessageException,
          AuthorizationException {
    addMessageAttribute(
        resolveLocator(message).getGlobalId(),
        MessageAttributes.NOCOMMENT,
        MessageAttribute.constructUsernamePayload(
            getLoggedInUser().getId(), getLoggedInUser().getName().toString()),
        true);
  }

  @Override
  public void createUser(
      final String userid,
      final String password,
      final String fullname,
      final String keywords,
      final String address1,
      final String address2,
      final String address3,
      final String address4,
      final String phoneno1,
      final String phoneno2,
      final String email1,
      final String email2,
      final String url,
      final String charset,
      final long flags1,
      final long flags2,
      final long flags3,
      final long flags4,
      final long rights)
      throws UnexpectedException, AmbiguousNameException, DuplicateNameException,
          AuthorizationException {
    checkRights(UserPermissions.USER_ADMIN);
    try {
      // Create the user
      //
      final long id =
          m_da.getUserManager()
              .addUser(
                  userid, password, fullname, keywords, address1, address2, address3, address4,
                  phoneno1, phoneno2, email1, email2, url, charset, "sv_SE", flags1, flags2, flags3,
                  flags4, rights);

      // Add default login script if requested
      //
      try {
        final String content = FileUtils.loadTextFromResource("firsttime.login");
        m_da.getFileManager().store(id, ".login.cmd", content);
      } catch (final FileNotFoundException e) {
        // No file specified? That's totally cool...
      } catch (final IOException e) {
        throw new UnexpectedException(getLoggedInUserId(), e);
      }
    } catch (final SQLException | NoSuchAlgorithmException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public void detach() {
    // Tell frontends to detach (hopefully they will)
    // TODO: Do we need to check that they actually do?
    //
    postEvent(new DetachRequestEvent(m_userId, m_userId));
  }

  @Override
  public synchronized void close() throws UnexpectedException {
    if (m_closed) {
      return; // Closing twice is a noop!
    }
    try {
      // Send notification!
      //
      m_sessions.broadcastEvent(
          new UserAttendanceEvent(
              m_userId, getUser(m_userId).getName(), UserAttendanceEvent.LOGOUT));

      // Handle pending unreads
      //
      applyUnreadMarkers();

      // Make sure all message markers are saved
      //
      leaveConference();

      // Save statistics
      //
      m_stats.setLoggedOut(new Timestamp(System.currentTimeMillis()));
      m_da.getUserLogManager().store(m_stats);

      // Save last login timestamp
      //
      updateLastlogin();

      // Unregister and kiss the world goodbye
      //
      UserContextFactory.getInstance().release(m_userId);
      m_sessions.unRegisterSession(this);
      m_closed = true;
    } catch (final SQLException | ObjectNotFoundException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public int countUnread(final long conference)
      throws ObjectNotFoundException, UnexpectedException {
    try {
      final MembershipList ml = m_userContext.getMemberships();
      int n = ml.countUnread(conference, m_da.getConferenceManager());

      // If we're filtering users, and the conference has a reasonable
      // number of unreads, we adjust for any filtered users. We only
      // adjust as long as we find filtered messages.
      // This will not always yield an accurate result, but it will at
      // least prevent us from going into conferences where all messages
      // are posted by Måns^h^h^h^hfiltered users.
      //
      final int FILTER_ADJUST_THREASHOLD = 10;
      if (n > 0 && n <= FILTER_ADJUST_THREASHOLD && m_userContext.getFilterCache().size() > 0) {
        int localnum;
        for (; ; ) {
          localnum = ml.getNextMessageInConference(conference, m_da.getConferenceManager());
          if (localnum == -1) {
            break;
          }
          final boolean mailbox = conference == getLoggedInUserId();
          if (applyFiltersForMessage(
              localToGlobal(conference, localnum),
              conference,
              localnum,
              mailbox ? FilterFlags.MAILS : FilterFlags.MESSAGES)) {
            --n;
            ml.markAsRead(conference, localnum);
          } else {
            break;
          }
        }
      }
      return n;
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public NameAssociation[] getAssociationsForPattern(final String pattern)
      throws UnexpectedException {
    try {
      return censorNames(m_da.getNameManager().getAssociationsByPattern(pattern));
    } catch (final SQLException | ObjectNotFoundException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public NameAssociation[] getAssociationsForPatternAndKind(final String pattern, final short kind)
      throws UnexpectedException {
    try {
      return censorNames(m_da.getNameManager().getAssociationsByPatternAndKind(pattern, kind));
    } catch (final SQLException | ObjectNotFoundException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public boolean canManipulateObject(final long object)
      throws ObjectNotFoundException, UnexpectedException {
    final short kind = getObjectKind(object);
    final UserInfo ui = getLoggedInUser();
    return ui.getId() == object
        || hasPermissionInConference(object, ConferencePermissions.ADMIN_PERMISSION)
        || (kind == NameManager.USER_KIND && ui.hasRights(UserPermissions.USER_ADMIN))
        || (kind == NameManager.CONFERENCE_KIND && ui.hasRights(UserPermissions.CONFERENCE_ADMIN));
  }

  @Override
  public MessageOccurrence globalToLocalInConference(final long conferenceId, final long globalNum)
      throws ObjectNotFoundException, UnexpectedException {
    try {
      return m_da.getMessageManager()
          .getMostRelevantOccurrence(getLoggedInUserId(), conferenceId, globalNum);
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public MessageOccurrence globalToLocal(final long globalNum)
      throws ObjectNotFoundException, UnexpectedException {
    return globalToLocalInConference(m_currentConferenceId, globalNum);
  }

  @Override
  public long localToGlobal(final long conferenceId, final int localnum)
      throws ObjectNotFoundException, UnexpectedException {
    try {
      return m_da.getMessageManager().getGlobalMessageId(conferenceId, localnum);
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public long localToGlobalInCurrentConference(final int localId)
      throws ObjectNotFoundException, UnexpectedException {
    return localToGlobal(getCurrentConferenceId(), localId);
  }

  @Override
  public long getGlobalMessageId(final MessageLocator textNumber)
      throws ObjectNotFoundException, NoCurrentMessageException, UnexpectedException {
    return resolveLocator(textNumber).getGlobalId();
  }

  @Override
  public void copyMessage(final long globalNum, final long conferenceId)
      throws AuthorizationException, ObjectNotFoundException, UnexpectedException {
    try {
      // Check permissions.
      // TODO: Maybe a special copy-permission would be cool?
      //
      final long me = getLoggedInUserId();

      final MembershipManager mbr = m_da.getMembershipManager();
      if (!mbr.hasPermission(me, conferenceId, ConferencePermissions.WRITE_PERMISSION)) {
        throw new AuthorizationException();
      }
      final MessageManager mm = m_da.getMessageManager();
      final MessageOccurrence occ =
          mm.createMessageOccurrence(
              globalNum, MessageManager.ACTION_COPIED, me, getName(me).getName(), conferenceId);

      // Mark copy as read (unless we're narcissists)
      //
      if ((getLoggedInUser().getFlags1() & UserFlags.NARCISSIST) == 0) {
        markMessageAsRead(conferenceId, occ.getLocalnum());
      }

      // Notify the rest of the world that there is a new message!
      //
      broadcastEvent(new NewMessageEvent(me, conferenceId, occ.getLocalnum(), globalNum));
      m_stats.incNumCopies();
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public long getThreadIdForMessage(final MessageLocator ml)
      throws MessageNotFoundException, UnexpectedException {
    if (!ml.isValid()) {
      throw new MessageNotFoundException();
    }
    try {
      if (-1 == ml.getGlobalId()) {
        ml.setGlobalId(localToGlobal(getCurrentConferenceId(), ml.getLocalnum()));
      }
      return m_da.getMessageManager().getThreadIdForMessage(ml.getGlobalId());
    } catch (final SQLException | ObjectNotFoundException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public void deleteMessageInCurrentConference(final int localNum)
      throws AuthorizationException, ObjectNotFoundException, UnexpectedException {
    deleteMessage(localNum, getCurrentConferenceId());
  }

  @Override
  public void deleteMessage(final int localNum, final long conference)
      throws AuthorizationException, ObjectNotFoundException, UnexpectedException {
    if (!canDeleteOccurrence(localNum, conference)) {
      throw new AuthorizationException();
    }

    try {
      m_da.getMessageManager().dropMessageOccurrence(localNum, conference);
      broadcastEvent(new MessageDeletedEvent(getLoggedInUserId(), conference));
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  private boolean canDeleteOccurrence(final int localNum, final long conference)
      throws UnexpectedException, ObjectNotFoundException {
    try {
      return (m_da.getMessageManager().loadMessageOccurrence(conference, localNum).getUser().getId()
              == getLoggedInUserId())
          || hasPermissionInConference(
              conference,
              ConferencePermissions.DELETE_PERMISSION | ConferencePermissions.ADMIN_PERMISSION);
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  private void moveMessage(final int localNum, final long sourceConfId, final long destConfId)
      throws AuthorizationException, ObjectNotFoundException, UnexpectedException {
    try {
      // Now, to move a message you need to be able to remove it from it's original location
      // and place it in the new location.
      //
      final long me = getLoggedInUserId();
      if (!(hasPermissionInConference(destConfId, ConferencePermissions.WRITE_PERMISSION)
          && canDeleteOccurrence(localNum, sourceConfId))) {
        throw new AuthorizationException();
      }
      final MessageManager mm = m_da.getMessageManager();
      final long globId = localToGlobal(sourceConfId, localNum);

      // Start by creating the new occurrence
      // We must retain the message occurrence, as we'll be using it in the broadcast event.
      //
      final MessageOccurrence occ =
          mm.createMessageOccurrence(
              globId, MessageManager.ACTION_MOVED, me, getName(me).getName(), destConfId);

      // Drop the original occurrence
      //
      mm.dropMessageOccurrence(localNum, sourceConfId);

      // Tag the message with an ATTR_MOVEDFROM attribute containing the source conference id.
      //
      mm.addMessageAttribute(
          globId, MessageAttributes.MOVEDFROM, getCensoredName(sourceConfId).getName());

      // Hello, world!
      //
      broadcastEvent(new NewMessageEvent(me, destConfId, occ.getLocalnum(), occ.getGlobalId()));
      broadcastEvent(new MessageDeletedEvent(me, getCurrentConferenceId()));
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public void moveMessage(final long messageId, final long destConfId)
      throws AuthorizationException, ObjectNotFoundException, UnexpectedException {
    // I'm lazy, I'll just re-use the old move-methods...
    final MessageOccurrence originalOcc;
    try {
      originalOcc = m_da.getMessageManager().getOriginalMessageOccurrence(messageId);
      moveMessage(originalOcc.getLocalnum(), originalOcc.getConference(), destConfId);
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public long getCurrentMessage() throws NoCurrentMessageException {
    if (m_lastReadMessageId == -1) {
      throw new NoCurrentMessageException();
    }
    return m_lastReadMessageId;
  }

  @Override
  public MessageOccurrence getCurrentMessageOccurrence()
      throws NoCurrentMessageException, UnexpectedException {
    try {
      return m_da.getMessageManager()
          .getMostRelevantOccurrence(
              getLoggedInUserId(), m_currentConferenceId, getCurrentMessage());
    } catch (final SQLException | ObjectNotFoundException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public MessageOccurrence getMostRelevantOccurrence(final long conferenceId, final long messageId)
      throws ObjectNotFoundException, UnexpectedException {
    try {
      return m_da.getMessageManager()
          .getMostRelevantOccurrence(getLoggedInUserId(), conferenceId, messageId);
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public MessageOccurrence getOriginalMessageOccurrence(final long messageId)
      throws ObjectNotFoundException, UnexpectedException {
    try {
      return m_da.getMessageManager().getOriginalMessageOccurrence(messageId);
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public Name signup(final long conferenceId)
      throws ObjectNotFoundException, AlreadyMemberException, UnexpectedException,
          AuthorizationException {
    try {
      final long user = getLoggedInUserId();

      // Add membership (and grant all permissions)
      //
      m_da.getMembershipManager().signup(user, conferenceId, 0, 0, 0);

      // Flush membership cache
      //
      reloadMemberships();

      // Return full name of conference
      //
      return getCensoredName(conferenceId);
    } catch (final AlreadyMemberException e) {
      throw new AlreadyMemberException(new Object[] {getCensoredName(conferenceId).getName()});
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public Name[] signupForAllConferences() throws UnexpectedException, ObjectNotFoundException {
    final long uid = getLoggedInUserId();
    final MembershipManager mm = m_da.getMembershipManager();

    // Start by retrieving a list of all conferences
    //
    final long[] allconfs;
    final MembershipInfo[] memberOf;
    try {
      allconfs = m_da.getConferenceManager().getConferenceIdsByPattern("%");
      memberOf = mm.listMembershipsByUser(uid);
    } catch (final SQLException | ObjectNotFoundException e) {
      throw new UnexpectedException(uid, e);
    }

    // Put them in a better structure ..
    //
    final HashSet<Long> s = new HashSet<>();
    for (final long allconf : allconfs) {
      s.add(allconf);
    }

    // .. and clean out anything the user is already a member of.
    //
    for (final MembershipInfo membershipInfo : memberOf) {
      // if (s.contains(joined[i].getConference()))
      // {
      s.remove(membershipInfo.getConference());
      // }
    }

    // Finally, call signup() for each conference left in the set, silently swallowing
    // every exception. This should be more efficient than verifying visibility and rights
    // for every conference. Prove me wrong :-)
    //
    final ArrayList<Name> joins = new ArrayList<>(s.size());
    final Iterator<Long> it = s.iterator();
    while (it.hasNext()) {
      try {
        final long l = it.next();
        mm.signup(uid, l, 0, 0, 0);
        joins.add(getCensoredName(l));
      } catch (final Exception e) {
        // Don't bother differentiating between types, all we care about is that the
        // signup failed.
      }
    }

    // Finally, refresh membership list and return the number of joined conferences.
    //
    try {
      reloadMemberships();
    } catch (final SQLException e) {
      throw new UnexpectedException(uid, e);
    }
    final Name[] joined = new Name[joins.size()];
    joins.toArray(joined);
    return joined;
  }

  @Override
  public Name signoff(final long conferenceId)
      throws ObjectNotFoundException, UnexpectedException, NotMemberException {
    final long userId = getLoggedInUserId();
    try {
      if (!m_da.getMembershipManager().isMember(userId, conferenceId)) {
        throw new NotMemberException(new Object[] {getCensoredName(conferenceId).getName()});
      }
      m_da.getMembershipManager().signoff(userId, conferenceId);
      reloadMemberships();
      return getCensoredName(conferenceId);
    } catch (final SQLException e) {
      throw new UnexpectedException(userId, e);
    }
    // Return full name of conference
    //
  }

  @Override
  public int signoffAllConferences() throws ObjectNotFoundException, UnexpectedException {
    // First, determine which conferences the user is a member of.
    //
    final MembershipManager mm = m_da.getMembershipManager();
    final long uid = getLoggedInUserId();
    final MembershipInfo[] mo;
    try {
      mo = mm.listMembershipsByUser(uid);
    } catch (final SQLException e) {
      throw new UnexpectedException(uid, e);
    }

    // Walk through the array, swallowing any exception silently
    //
    int cnt = 0;
    for (final MembershipInfo membershipInfo : mo) {
      try {
        // Can't sign off mailbox.
        //
        if (uid == membershipInfo.getConference()) {
          continue;
        }

        mm.signoff(uid, membershipInfo.getConference());
        ++cnt;
      } catch (final Exception e) {
        // Intentionally ignoring exception
      }
    }

    // Reload the memberships (should be quick by now) and return count.
    //
    try {
      reloadMemberships();
    } catch (final SQLException e) {
      throw new UnexpectedException(uid, e);
    }
    return cnt;
  }

  @Override
  public long prioritizeConference(final long conference, final long targetconference)
      throws ObjectNotFoundException, UnexpectedException, NotMemberException {
    final long user = getLoggedInUserId();
    try {
      // Check if we're actually member of the two given conferences.
      //
      if (!m_da.getMembershipManager().isMember(user, conference)) {
        throw new NotMemberException(new Object[] {getCensoredName(conference).getName()});
      }
      if (!m_da.getMembershipManager().isMember(user, targetconference)) {
        throw new NotMemberException(new Object[] {getCensoredName(targetconference).getName()});
      }

      // Shufflepuck caf�
      //
      final long result =
          m_da.getMembershipManager().prioritizeConference(user, conference, targetconference);

      // Flush membership cache
      //
      reloadMemberships();
      return result;
    } catch (final SQLException e) {
      throw new UnexpectedException(user, e);
    }
  }

  @Override
  public void autoPrioritizeConferences() throws UnexpectedException {
    try {
      final ConferenceManager cm = m_da.getConferenceManager();
      final MembershipManager mm = m_da.getMembershipManager();
      final MembershipInfo[] mi = mm.listMembershipsByUser(getLoggedInUserId());

      // Put the data in a sortable wrapper and place in a better container.
      //
      final List<SortableMembershipInfo> newOrder = new ArrayList<>(mi.length);
      for (final MembershipInfo membershipInfo : mi) {
        // FIXME: If we experience a performance bottleneck (which I doubt), change
        // this to start by populating a hash table with conference ID + parent
        // count (one call, bulk transfer of n rows) instead of n separate calls.
        // Then again, the autoprioritize command won't get run all that often and
        // we'll be fine with an extra few seconds as long as the so called
        // database doesn't buckle under the load.
        //
        newOrder.add(
            new SortableMembershipInfo(
                membershipInfo, cm.countParentsForConference(membershipInfo.getConference())));
      }
      java.util.Collections.sort(newOrder);

      // OK, we're now sorted according to the Algorithm of Darkness. Walk the list,
      // writing new priorities back to the database.
      //
      final Iterator<SortableMembershipInfo> it = newOrder.listIterator();
      for (int i = 1; it.hasNext(); ++i) {
        mm.updatePriority(i, getLoggedInUserId(), it.next().getId());
      }
    } catch (final Exception e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public UserInfo getUser(final long userId) throws ObjectNotFoundException, UnexpectedException {
    try {
      return m_da.getUserManager().loadUser(userId);
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public ConferenceInfo getConference(final long conferenceId)
      throws ObjectNotFoundException, UnexpectedException {
    try {
      if (!isVisible(conferenceId)) {
        throw new ObjectNotFoundException("id=" + conferenceId);
      }
      return m_da.getConferenceManager().loadConference(conferenceId);
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public NamedObject getNamedObject(final long id)
      throws ObjectNotFoundException, UnexpectedException {
    if (!isVisible(id)) {
      throw new ObjectNotFoundException("id=" + id);
    }
    try {
      // First, try users
      //
      return getUser(id);
    } catch (final ObjectNotFoundException e) {
      // Not a user. Try conference!
      //
      return getConference(id);
    }
  }

  @Override
  public NameAssociation[] listMemberships(final long userId)
      throws ObjectNotFoundException, UnexpectedException {
    try {
      final MembershipInfo[] mi = m_da.getMembershipManager().listMembershipsByUser(userId);
      final int top = mi.length;
      final NameAssociation[] answer = new NameAssociation[top];
      for (int idx = 0; idx < top; ++idx) {
        final long conf = mi[idx].getConference();
        try {
          answer[idx] = new NameAssociation(conf, getCensoredName(conf));
        } catch (final ObjectNotFoundException e) {
          // Probably deleted while we were listing
          //
          answer[idx] =
              new NameAssociation(
                  conf, new Name("???", Visibilities.PUBLIC, NameManager.CONFERENCE_KIND));
        }
      }
      return censorNames(answer);
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public MembershipInfo[] listConferenceMembers(final long confId)
      throws ObjectNotFoundException, UnexpectedException {
    try {
      return m_da.getMembershipManager().listMembershipsByConference(confId);
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public NameAssociation[] listMembersByConference(final long confId)
      throws ObjectNotFoundException, UnexpectedException {
    final MembershipInfo[] mi = listConferenceMembers(confId);
    final NameAssociation[] s = new NameAssociation[mi.length];
    for (int i = 0; i < mi.length; ++i) {
      s[i] = new NameAssociation(mi[i].getUser(), getName(mi[i].getUser()));
    }
    return s;
  }

  @Override
  public Name getName(final long id) throws ObjectNotFoundException, UnexpectedException {
    return getCensoredName(id);
  }

  @Override
  public Name[] getNames(final long[] ids) throws ObjectNotFoundException, UnexpectedException {
    final int top = ids.length;
    final Name[] names = new Name[top];
    for (int idx = 0; idx < top; ++idx) {
      names[idx] = getName(ids[idx]);
    }
    return names;
  }

  @Override
  public String getDebugString() {
    final ByteArrayOutputStream s = new ByteArrayOutputStream();
    m_userContext.getMemberships().printDebugInfo(new PrintStream(s));
    return s.toString();
  }

  public void markAsUnreadAtLogoutInCurrentConference(final int localnum)
      throws UnexpectedException {
    innerMarkAsUnreadAtLogoutInCurrentConference(localnum);
    saveUnreadMarkers();
  }

  @Override
  public void markAsUnreadAtLogout(final MessageLocator message)
      throws UnexpectedException, NoCurrentMessageException, ObjectNotFoundException {
    innerMarkAsUnreadAtLogout(resolveLocator(message).getGlobalId());
    saveUnreadMarkers();
  }

  @Override
  public int markThreadAsUnread(final long root, final boolean immediate)
      throws ObjectNotFoundException, UnexpectedException {
    ReadLogItem old = null;
    if (immediate) {
      old = m_pendingUnreads;
      m_pendingUnreads = null;
    }
    final int n = markThreadAsUnread(root);
    if (immediate) {
      applyUnreadMarkers();
      m_pendingUnreads = old;
    }
    return n;
  }

  @Override
  public int markThreadAsUnread(final long root)
      throws ObjectNotFoundException, UnexpectedException {
    final int n = performTreeOperation(root, new MarkAsUnreadOperation());
    saveUnreadMarkers();
    return n;
  }

  @Override
  public int markSubjectAsUnread(
      final String subject, final boolean localOnly, final boolean immediate)
      throws ObjectNotFoundException, UnexpectedException {
    ReadLogItem old = null;
    if (immediate) {
      old = m_pendingUnreads;
      m_pendingUnreads = null;
    }
    final int n = markSubjectAsUnread(subject, localOnly);
    if (immediate) {
      applyUnreadMarkers();
      m_pendingUnreads = old;
    }
    return n;
  }

  @Override
  public int markSubjectAsUnread(final String subject, final boolean localOnly)
      throws ObjectNotFoundException, UnexpectedException {
    final int n =
        localOnly
            ? performOperationBySubjectInConference(subject, new MarkAsUnreadOperation())
            : performOperationBySubject(subject, new MarkAsUnreadOperation());
    saveUnreadMarkers();
    return n;
  }

  @Override
  public void changeUnread(final int nUnread, final long conference)
      throws ObjectNotFoundException, UnexpectedException {
    try {
      // Update message markers
      //
      final ConferenceManager cm = m_da.getConferenceManager();
      final ConferenceInfo ci = cm.loadConference(conference);
      int high = ci.getLastMessage();
      high = Math.max(0, high - nUnread);
      m_userContext.getMemberships().changeRead(ci.getId(), ci.getFirstMessage(), high);

      // Discard reply stack
      //
      m_replyStack = null;

    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public void changeUnread(final int nUnread) throws ObjectNotFoundException, UnexpectedException {
    changeUnread(nUnread, getCurrentConferenceId());
  }

  @Override
  public void changeUnreadInAllConfs(final int nUnread)
      throws ObjectNotFoundException, UnexpectedException {
    try {
      final ConferenceManager cm = m_da.getConferenceManager();
      final MembershipList ml = m_userContext.getMemberships();
      final MembershipManager mm = m_da.getMembershipManager();
      final MembershipInfo[] mi = mm.listMembershipsByUser(getLoggedInUserId());

      for (final MembershipInfo m : mi) {
        try {
          final ConferenceInfo ci = cm.loadConference(m.getConference());
          int high = ci.getLastMessage();
          high = Math.max(0, high - nUnread);
          ml.changeRead(ci.getId(), ci.getFirstMessage(), high);
        } catch (final Exception e) {
          // When does this happen? If the DB goes away? If a conference is
          // deleted while we run? If the user signs off a conference in another
          // session while running this?
        }
      }
      m_replyStack = null;
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public MembershipListItem[] listNewsFor(final long userId) throws UnexpectedException {
    try {
      final ConferenceManager cm = m_da.getConferenceManager();
      final MembershipManager mm = m_da.getMembershipManager();
      final MembershipInfo[] m = mm.listMembershipsByUser(userId);
      final MembershipList ml = new MembershipList(m);
      final int top = m.length;
      final List<MembershipListItem> list = new ArrayList<>(top);
      for (final MembershipInfo membershipInfo : m) {
        try {
          final long confId = membershipInfo.getConference();
          if (!isVisible(confId)) {
            continue;
          }
          final int n = ml.countUnread(confId, cm);
          if (0 < n) {
            list.add(
                new MembershipListItem(new NameAssociation(confId, getCensoredName(confId)), n));
          }
        } catch (final ObjectNotFoundException e) {
          // Ignore
        }
      }
      final MembershipListItem[] answer = new MembershipListItem[list.size()];
      list.toArray(answer);
      return answer;
    } catch (final SQLException | ObjectNotFoundException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public MembershipListItem[] listNews() throws UnexpectedException {
    final MembershipList memberships = m_userContext.getMemberships();
    final MembershipInfo[] m = memberships.getMemberships();
    final int top = m.length;
    final List<MembershipListItem> list = new ArrayList<>(top);
    for (final MembershipInfo membershipInfo : m) {
      try {
        final long conf = membershipInfo.getConference();

        // Don't include invisible conferences
        //
        if (!isVisible(conf)) {
          continue;
        }
        final int n = countUnread(conf);

        if (n > 0) {
          list.add(new MembershipListItem(new NameAssociation(conf, getCensoredName(conf)), n));
        }
      } catch (final ObjectNotFoundException e) {
        // Probably deleted. Just skip!
      }
    }
    final MembershipListItem[] answer = new MembershipListItem[list.size()];
    list.toArray(answer);
    return answer;
  }

  @Override
  public UserListItem[] listLoggedInUsers() throws UnexpectedException {
    final List<ServerSession> sessions = m_sessions.listSessions();
    final UserListItem[] answer = new UserListItem[sessions.size()];
    int idx = 0;
    for (final Iterator<ServerSession> itor = sessions.iterator(); itor.hasNext(); ++idx) {
      final ServerSession session = itor.next();
      final long confId = session.getCurrentConferenceId();
      final long user = session.getLoggedInUserId();
      String userName = "???";
      boolean inMailbox = false;
      try {
        userName = getCensoredName(user).getName();
        inMailbox = confId == user;
      } catch (final ObjectNotFoundException e) {
        // User deleted! Strange, but we allow it. User will be displayed as "???"
        //
      }
      Name conferenceName = new Name("???", Visibilities.PUBLIC, NameManager.CONFERENCE_KIND);
      try {
        conferenceName = getCensoredName(confId);

        // Wipe out protected names
        //
        if (!isVisible(confId)) {
          conferenceName.hideName();
        }
      } catch (final ObjectNotFoundException e) {
        // Conference deleted. Display as "???"
      }

      answer[idx] =
          new UserListItem(
              session.getSessionId(),
              new NameAssociation(
                  user, new Name(userName, Visibilities.PUBLIC, NameManager.USER_KIND)),
              session.getClientType(),
              (short) 0,
              new NameAssociation(confId, conferenceName),
              inMailbox,
              session.getLoginTime(),
              session.getLastHeartbeat(),
              session.getActivity(),
              session.getActivityString(),
              session.getLastObject());
    }
    return answer;
  }

  @Override
  public boolean hasSession(final long userId) {
    return m_sessions.userHasSession(userId);
  }

  @Override
  public synchronized void postEvent(final Event e) {
    m_incomingEvents.addLast(e);
    notify();
  }

  @Override
  public synchronized Event pollEvent(final int timeoutMs) throws InterruptedException {
    if (m_incomingEvents.isEmpty()) {
      wait(timeoutMs);
    }
    if (m_incomingEvents.isEmpty()) {
      return null;
    }
    return m_incomingEvents.removeFirst();
  }

  @Override
  public long getLastHeartbeat() {
    return m_lastHeartbeat;
  }

  @Override
  public void enableSelfRegistration() throws AuthorizationException, UnexpectedException {
    checkRights(UserPermissions.ADMIN);
    try {
      m_da.getSettingManager().changeSetting(SettingKeys.ALLOW_SELF_REGISTER, "", 1);
    } catch (final SQLException e) {
      throw new UnexpectedException(-1, e);
    }
  }

  @Override
  public void disableSelfRegistration() throws AuthorizationException, UnexpectedException {
    checkRights(UserPermissions.ADMIN);
    try {
      m_da.getSettingManager().changeSetting(SettingKeys.ALLOW_SELF_REGISTER, "", 0);
    } catch (final SQLException e) {
      throw new UnexpectedException(-1, e);
    }
  }

  private void sendChatMessageHelper(final long userId, final String message) {
    if (m_sessions.userHasSession(userId)) {
      if (message.charAt(0) == '!') {
        m_sessions.sendEvent(userId, new ChatAnonymousMessageEvent(userId, message.substring(1)));
      } else {
        m_sessions.sendEvent(
            userId,
            new ChatMessageEvent(
                userId, getLoggedInUserId(), getLoggedInUser().getName(), message));
      }
    }
    m_stats.incNumChats();
  }

  @Override
  public NameAssociation[] sendMulticastMessage(
      final long[] destinations, final String message, final boolean logAsMulticast)
      throws ObjectNotFoundException, UnexpectedException {
    try {
      // Create a message log item. If we have multiple recipients, they all share
      // the same item.
      //
      final UserManager um = m_da.getUserManager();
      final MessageLogManager mlm = m_da.getMessageLogManager();
      final long logId =
          mlm.storeMessage(getLoggedInUserId(), getLoggedInUser().getName().getName(), message);

      // Create a link for the logged in user. Used for retrieving messages sent.
      //
      mlm.storeMessagePointer(
          logId,
          getLoggedInUserId(),
          true,
          logAsMulticast ? MessageLogKinds.MULTICAST : MessageLogKinds.CHAT);

      final ArrayList<NameAssociation> refused = new ArrayList<>(destinations.length);
      boolean explicitToSelf = false;

      // Set to make sure we don't send the message to the same user more than once.
      //
      final HashSet<Long> s = new HashSet<>();
      final HashSet<Name> rec_names = new HashSet<>();
      for (final long each : destinations) {
        if (-1 == each) {
          break;
        } else {
          if (NameManager.USER_KIND == m_da.getNameManager().getObjectKind(each)) {
            // Are we explicitly sending to ourselves`?
            //
            final UserInfo ui = um.loadUser(each);
            if (each == getLoggedInUserId()) {
              explicitToSelf = true;
            }
            final UserContext uc = UserContextFactory.getInstance().getContextOrNull(each);
            if (uc != null && uc.allowsChat(um, getLoggedInUserId())) {
              s.add(each);
              rec_names.add(ui.getName());
            } else {
              refused.add(new NameAssociation(each, ui.getName()));
            }
          } else // conference
          {
            final MembershipInfo[] mi =
                m_da.getMembershipManager().listMembershipsByConference(each);
            rec_names.add(getName(each));
            for (final MembershipInfo membershipInfo : mi) {
              final long uid = membershipInfo.getUser();
              final UserContext uc = UserContextFactory.getInstance().getContextOrNull(uid);
              if (uc != null) {
                final UserInfo ui = um.loadUser(uid);

                // Does the receiver accept chat messages
                //
                if (uc.allowsChat(um, uid)) {
                  s.add(uid);
                } else {
                  refused.add(new NameAssociation(uid, ui.getName()));
                }
              }
            }
          }
        }
      } // for

      // Remove sending user
      // TODO: This should be a flag condition!
      //
      if (!explicitToSelf) {
        s.remove(getLoggedInUserId());
      }

      // Temporary kludge. I need to extend the event class and rewrite
      // some of the formatting.
      //
      StringBuilder msgToSend;
      if (s.size() > 1) {
        msgToSend = new StringBuilder("[");
        for (final Name rec_name : rec_names) {
          msgToSend.append(rec_name.toString());
          msgToSend.append(", ");
        }
        msgToSend = new StringBuilder(msgToSend.substring(0, msgToSend.length() - 2));
        msgToSend.append("] ");
        msgToSend.append(message);
      } else {
        msgToSend = new StringBuilder(message);
      }

      // Now just send it
      //
      for (final long user : s) {
        sendChatMessageHelper(user, msgToSend.toString());

        // Create link from recipient to message
        //
        mlm.storeMessagePointer(
            logId, user, false, logAsMulticast ? MessageLogKinds.MULTICAST : MessageLogKinds.CHAT);
      }

      final NameAssociation[] answer = new NameAssociation[refused.size()];
      refused.toArray(answer);
      return answer;
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public int[] verifyChatRecipients(final long[] recipients) throws UnexpectedException {
    final NameManager nm = m_da.getNameManager();
    final UserManager um = m_da.getUserManager();
    final int top = recipients.length;
    final int[] answer = new int[top];
    for (int idx = 0; idx < top; idx++) {
      final long each = recipients[idx];
      try {
        final short kind = nm.getObjectKind(each);

        // Conferences are always considered ok recipients
        //
        if (kind == NameManager.CONFERENCE_KIND) {
          answer[idx] = ChatRecipientStatus.OK_CONFERENCE;
        } else {
          // User. Check if logged in.
          //
          final UserContext uc = UserContextFactory.getInstance().getContextOrNull(each);
          if (uc == null) {
            answer[idx] = ChatRecipientStatus.NOT_LOGGED_IN;
          } else {
            // Logged in. Do they receive chat messages?
            //
            answer[idx] =
                uc.allowsChat(um, getLoggedInUserId())
                    ? ChatRecipientStatus.OK_USER
                    : ChatRecipientStatus.REFUSES_MESSAGES;
          }
        }
      } catch (final ObjectNotFoundException e) {
        answer[idx] = ChatRecipientStatus.NONEXISTENT;
      }
    }
    return answer;
  }

  @Override
  public NameAssociation[] broadcastChatMessage(final String message, final short kind)
      throws UnexpectedException {
    try {
      // Create a message log item. If we have multiple recipients, they all share
      // the same item.
      //
      final UserManager um = m_da.getUserManager();
      final MessageLogManager mlm = m_da.getMessageLogManager();
      final long logId =
          mlm.storeMessage(getLoggedInUserId(), getLoggedInUser().getName().getName(), message);

      if (message.charAt(0) == '!') {
        m_sessions.broadcastEvent(new BroadcastAnonymousMessageEvent(message.substring(1), logId));
      } else {
        m_sessions.broadcastEvent(
            new BroadcastMessageEvent(
                getLoggedInUserId(), getLoggedInUser().getName(), message, logId, kind));
      }

      // Log to chat log. This could be done in the event handlers, but
      // that would give all kinds of concurrency and transactional problems,
      // since they are executing asynchronously.
      // There is, of course, a slight chance that someone logs in out
      // while doing this and that the log won't be 100% correct, but that's
      // a tradeoff we're willing to make at this point.
      //
      final ArrayList<NameAssociation> bounces = new ArrayList<>();
      for (final UserContext uc : UserContextFactory.getInstance().listContexts()) {
        final long userId = uc.getUserId();
        try {
          // Find out if recipients allows broadcasts
          //
          final UserInfo user = um.loadUser(userId);

          if (uc.allowsBroadcast(um, getLoggedInUserId())) {
            mlm.storeMessagePointer(logId, userId, false, kind);
          } else {
            bounces.add(new NameAssociation(userId, user.getName()));
          }
        } catch (final ObjectNotFoundException e) {
          // Sending to nonexisting user? Probably deleted while
          // we were iterating. Just skip!
          //
        }
      }
      final NameAssociation[] answer = new NameAssociation[bounces.size()];
      bounces.toArray(answer);
      m_stats.incNumBroadcasts();
      return answer;
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public void clearCache() throws AuthorizationException {
    // Only sysops can do this.
    //
    checkRights(UserPermissions.ADMIN);
    CacheManager.instance().clear();

    // Tell all clients to reload their local user profile copy
    //
    broadcastEvent(new ReloadUserProfileEvent(-1));
  }

  @Override
  public short getObjectKind(final long object) throws ObjectNotFoundException {
    return m_da.getNameManager().getObjectKind(object);
  }

  @Override
  public void updateCharacterset(final String charset) throws UnexpectedException {
    try {
      m_da.getUserManager().updateCharacterset(getLoggedInUserId(), charset);
    } catch (final SQLException | ObjectNotFoundException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public void updateTimeZone(final String timeZone) throws UnexpectedException {
    try {
      m_da.getUserManager().changeTimezone(getLoggedInUserId(), timeZone);
    } catch (final SQLException | ObjectNotFoundException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public void setConferencePermissions(final long conf, final long user, final int permissions)
      throws UnexpectedException {
    try {
      final MembershipManager mm = m_da.getMembershipManager();

      // Get hold of conference permission set and calculate negation mask.
      // Any permission granted by default for the conference, but is denied
      // in user-specific mask should be included in the negation mask.
      //
      final int c = getCurrentConference().getPermissions();
      final int negations = c & ~permissions;
      mm.updateConferencePermissions(user, conf, permissions, negations);
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public void setConferencePermissionsInCurrentConference(final long user, final int permissions)
      throws UnexpectedException {
    setConferencePermissions(getCurrentConferenceId(), user, permissions);
  }

  @Override
  public void revokeConferencePermissions(final long conf, final long user)
      throws UnexpectedException {
    try {
      m_da.getMembershipManager().updateConferencePermissions(user, conf, 0, 0xffffffff);
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public void revokeConferencePermissionsInCurrentConference(final long user)
      throws UnexpectedException {
    revokeConferencePermissions(getCurrentConferenceId(), user);
  }

  @Override
  public ConferencePermission[] listConferencePermissions(final long conf)
      throws UnexpectedException {
    try {
      return m_da.getMembershipManager().listPermissions(conf);
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public ConferencePermission[] listConferencePermissionsInCurrentConference()
      throws UnexpectedException {
    return listConferencePermissions(getCurrentConferenceId());
  }

  @Override
  public int getPermissionsInConference(final long conferenceId)
      throws ObjectNotFoundException, UnexpectedException {
    return getUserPermissionsInConference(getLoggedInUserId(), conferenceId);
  }

  @Override
  public int getUserPermissionsInConference(final long userId, final long conferenceId)
      throws ObjectNotFoundException, UnexpectedException {
    try {
      return m_da.getMembershipManager().getPermissions(userId, conferenceId);
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public int getPermissionsInCurrentConference()
      throws ObjectNotFoundException, UnexpectedException {
    try {
      return m_da.getMembershipManager()
          .getPermissions(getLoggedInUserId(), getCurrentConferenceId());
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public boolean hasPermissionInConference(final long conferenceId, final int mask)
      throws ObjectNotFoundException, UnexpectedException {
    return hasPermissionInConference(getLoggedInUserId(), conferenceId, mask);
  }

  public boolean hasPermissionInConference(
      final long userId, final long conferenceId, final int mask)
      throws ObjectNotFoundException, UnexpectedException {
    final UserInfo ui = getUser(userId);
    try {
      // Do we have the permission to disregard conference permissions?
      //
      if (ui.hasRights(UserPermissions.DISREGARD_CONF_PERM))
      // We can only disregard everything but ADMIN_PERMISSION.
      //
      {
        if ((mask & ConferencePermissions.ADMIN_PERMISSION) != mask) {
          return true;
        }
      }
      return m_da.getMembershipManager().hasPermission(ui.getId(), conferenceId, mask);
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public boolean hasPermissionInCurrentConference(final int mask)
      throws ObjectNotFoundException, UnexpectedException {
    return hasPermissionInConference(getCurrentConferenceId(), mask);
  }

  @Override
  public void changeReplyToConference(
      final long originalConferenceId, final long newReplyToConferenceId)
      throws AuthorizationException, ObjectNotFoundException, UnexpectedException {
    // Check rights. This also checks if the original conference exists.
    assertModifyConference(originalConferenceId);

    try {
      m_da.getConferenceManager()
          .changeReplyToConference(originalConferenceId, newReplyToConferenceId);
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public void renameObject(final long id, final String newName)
      throws DuplicateNameException, ObjectNotFoundException, AuthorizationException,
          UnexpectedException {
    try {
      if (!userCanChangeNameOf(id)) {
        throw new AuthorizationException();
      }
      m_da.getNameManager().renameObject(id, newName);
      sendEvent(id, new ReloadUserProfileEvent(id));
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public void changeSuffixOfLoggedInUser(final String suffix)
      throws DuplicateNameException, ObjectNotFoundException, UnexpectedException {
    try {
      final long me = getLoggedInUserId();
      final String name = getName(me).getName();
      m_da.getNameManager().renameObject(me, NameUtils.addSuffix(name, suffix));
      sendEvent(me, new ReloadUserProfileEvent(me));
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public void changeSuffixOfUser(final long id, final String suffix)
      throws DuplicateNameException, ObjectNotFoundException, AuthorizationException,
          UnexpectedException {
    try {
      checkRights(UserPermissions.CHANGE_ANY_NAME);
      final Name name = getName(id);
      m_da.getNameManager().renameObject(id, NameUtils.addSuffix(name.getName(), suffix));
      sendEvent(id, new ReloadUserProfileEvent(id));
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public boolean userCanChangeNameOf(final long id) throws UnexpectedException {
    // Do we have sysop rights? Anything goes!
    //
    if (getLoggedInUser().hasRights(UserPermissions.CHANGE_ANY_NAME)) {
      return true;
    }

    // Otherwise, we may only change names of conferences we're the admin
    // of.
    //
    try {
      return id != getLoggedInUserId()
          && hasPermissionInConference(id, ConferencePermissions.ADMIN_PERMISSION);
    } catch (final ObjectNotFoundException e) {
      // Conference not found. It's probably a user then, and we
      // don't have the right to admin rights for that.
      //
      return false;
    }
  }

  @Override
  public void changePassword(final long userId, final String oldPassword, final String password)
      throws ObjectNotFoundException, AuthorizationException, UnexpectedException,
          BadPasswordException {
    try {
      // Check permissions unless we change our own password
      //
      if (userId != getLoggedInUserId()) {
        checkRights(UserPermissions.USER_ADMIN);
      } else {
        m_da.getUserManager().authenticate(getUser(userId).getUserid(), oldPassword);
      }
      m_da.getUserManager().changePassword(userId, password);
    } catch (final SQLException | NoSuchAlgorithmException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    } catch (final AuthenticationException e) {
      throw new BadPasswordException();
    }
  }

  @Override
  public void changeUserFlags(final long[] set, final long[] reset)
      throws ObjectNotFoundException, UnexpectedException {
    try {
      // Load current flags.
      //
      final UserInfo ui = getLoggedInUser();
      final long id = ui.getId();
      final long[] oldFlags = ui.getFlags();

      // Calculate new flags sets
      //
      final long[] flags = new long[UserFlags.NUM_FLAG_WORD];
      for (int idx = 0; idx < UserFlags.NUM_FLAG_WORD; ++idx) {
        flags[idx] = (oldFlags[idx] | set[idx]) & ~reset[idx];
      }

      // Store in database
      //
      m_da.getUserManager().changeFlags(id, flags);

      // Force clients to reload local cache
      //
      sendEvent(id, new ReloadUserProfileEvent(id));
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public void changeUserPermissions(final long user, final long set, final long reset)
      throws ObjectNotFoundException, AuthorizationException, UnexpectedException {
    try {
      // Check permissions
      //
      checkRights(UserPermissions.USER_ADMIN);

      // Load current permissions
      //
      final UserManager um = m_da.getUserManager();
      final UserInfo ui = um.loadUser(user);
      final long oldFlags = ui.getRights();

      // Store new permissions in database
      //
      um.changePermissions(user, (oldFlags | set) & ~reset);

      // Force clients to invalidate local cache
      //
      sendEvent(user, new ReloadUserProfileEvent(user));
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public void updateConferencePermissions(
      final long id, final int permissions, final int nonmemberpermissions, final short visibility)
      throws ObjectNotFoundException, AuthorizationException, UnexpectedException {
    // Load conference
    //
    final ConferenceInfo ci = getConference(id);

    // Check permissions. If we own the conference, we can change it. Otherwise, we
    // can change it only if we hold the CONFERENCE_ADMIN priv.
    //
    if (ci.getAdministrator() != getLoggedInUserId()
        && !getLoggedInUser().hasRights(UserPermissions.CONFERENCE_ADMIN)) {
      throw new AuthorizationException();
    }

    // Update!
    //
    try {
      m_da.getConferenceManager()
          .changePermissions(id, permissions, nonmemberpermissions, visibility);
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public int skipMessagesBySubject(final String subject, final boolean skipGlobal)
      throws UnexpectedException, ObjectNotFoundException {
    return skipGlobal
        ? performOperationBySubject(subject, new MarkAsReadOperation())
        : performOperationBySubjectInConference(subject, new MarkAsReadOperation());
  }

  @Override
  public int skipThread(final long message)
      throws UnexpectedException, ObjectNotFoundException, SelectionOverflowException {
    try {
      final Message m = m_da.getMessageManager().loadMessage(message);
      final long thread = m.getThread();
      if (thread <= 0) {
        return 0;
      }
      return performThreadOperation(m.getThread(), 100000, new MarkAsReadOperation());
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public int skipBranch(final long root) throws UnexpectedException, ObjectNotFoundException {
    return performTreeOperation(root, new MarkAsReadOperation());
  }

  @Override
  public MessageLogItem[] getChatMessagesFromLog(final int limit) throws UnexpectedException {
    try {
      return m_da.getMessageLogManager().listChatMessages(getLoggedInUserId(), limit);
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public MessageLogItem[] getMulticastMessagesFromLog(final int limit) throws UnexpectedException {
    try {
      return m_da.getMessageLogManager().listMulticastMessages(getLoggedInUserId(), limit);
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public MessageLogItem[] getBroadcastMessagesFromLog(final int limit) throws UnexpectedException {
    try {
      return m_da.getMessageLogManager().listBroadcastMessages(getLoggedInUserId(), limit);
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public HeartbeatListener getHeartbeatListener() {
    return new HeartbeatListenerImpl();
  }

  @Override
  public void killSession(final int sessionId) throws AuthorizationException, UnexpectedException {
    // We have to be sysops to do this!
    //
    checkRights(UserPermissions.ADMIN);

    // SET THEM UP THE BOMB! FOR GREAT JUSTICE!
    //
    try {
      m_sessions.killSessionById(sessionId);
    } catch (final InterruptedException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public void killAllSessions() throws AuthorizationException, UnexpectedException {
    // We have to be sysops to do this!
    //
    checkRights(UserPermissions.ADMIN);

    // Disallow new logins
    //
    prohibitLogin();

    // SET UP US THE BIG BOMB!!
    // Kill all sessions (except this one)
    //
    final List<ServerSession> sessions = m_sessions.listSessions();
    for (final ServerSession each : sessions) {
      if (each != this) {
        killSession(each.getSessionId());
      }
    }
  }

  @Override
  public void prohibitLogin() throws AuthorizationException {
    // We have to be sysops to do this!
    //
    checkRights(UserPermissions.ADMIN);
    m_sessions.prohibitLogin();
  }

  @Override
  public void allowLogin() throws AuthorizationException {
    // We have to be sysops to do this!
    //
    checkRights(UserPermissions.ADMIN);
    m_sessions.allowLogin();
  }

  @Override
  public UserLogItem[] listUserLog(
      final Timestamp start, final Timestamp end, final int offset, final int length)
      throws UnexpectedException {
    try {
      return m_da.getUserLogManager().getByDate(start, end, offset, length);
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public UserLogItem[] listUserLog(
      final long user,
      final Timestamp start,
      final Timestamp end,
      final int offset,
      final int length)
      throws UnexpectedException {
    try {
      return m_da.getUserLogManager().getByUser(user, start, end, offset, length);
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public FileStatus statFile(final long parent, final String name)
      throws ObjectNotFoundException, UnexpectedException {
    try {
      return m_da.getFileManager().stat(parent, name);
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public FileStatus[] listFiles(final long parent, final String pattern)
      throws UnexpectedException, ObjectNotFoundException {
    if (!isVisible(parent)) {
      throw new ObjectNotFoundException("id=" + parent);
    }
    try {
      return m_da.getFileManager().list(parent, pattern);
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public String readFile(final long parent, final String name)
      throws ObjectNotFoundException, AuthorizationException, UnexpectedException {
    try {
      final FileManager fm = m_da.getFileManager();
      if ((getLoggedInUser().getRights() & UserPermissions.ADMIN) == 0
          && !hasPermissionInConference(parent, ConferencePermissions.READ_PERMISSION)
          && (fm.stat(parent, name).getProtection() & FileProtection.ALLOW_READ) == 0) {
        throw new AuthorizationException();
      }
      return fm.read(parent, name);
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public void storeFile(
      final long parent, final String name, final String content, final int permissions)
      throws AuthorizationException, ObjectNotFoundException, UnexpectedException {
    try {
      final FileManager fm = m_da.getFileManager();
      final boolean isSysop = (getLoggedInUser().getRights() & UserPermissions.ADMIN) != 0;
      final boolean hasParentRights =
          isSysop || hasPermissionInConference(parent, ConferencePermissions.WRITE_PERMISSION);
      try {
        final FileStatus fs = fm.stat(parent, name);
        if (!hasParentRights && (fs.getProtection() & FileProtection.ALLOW_WRITE) == 0) {
          throw new AuthorizationException();
        }
      } catch (final ObjectNotFoundException e) {
        // New file. Just check parent permission
        //
        if (!hasParentRights) {
          throw new AuthorizationException();
        }
      }
      fm.store(parent, name, content);
      fm.chmod(parent, name, permissions);
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public void deleteFile(final long parent, final String name)
      throws AuthorizationException, ObjectNotFoundException, UnexpectedException {
    try {
      assertConferencePermission(parent, ConferencePermissions.WRITE_PERMISSION);
      m_da.getFileManager().delete(parent, name);
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public String readSystemFile(final String name)
      throws AuthorizationException, ObjectNotFoundException, UnexpectedException {
    try {
      return readFile(m_da.getUserManager().getSysopId(), name);
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public void storeSystemFile(final String name, final String content)
      throws AuthorizationException, UnexpectedException {
    try {
      final long parent = m_da.getUserManager().getSysopId();
      storeFile(parent, name, content, FileProtection.ALLOW_READ);
    } catch (final SQLException | ObjectNotFoundException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public void deleteSystemFile(final String name)
      throws AuthorizationException, ObjectNotFoundException, UnexpectedException {
    try {
      deleteFile(m_da.getUserManager().getSysopId(), name);
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public void createUserFilter(final long jinge, final long flags)
      throws ObjectNotFoundException, UnexpectedException {
    try {
      final long me = getLoggedInUserId();
      final RelationshipManager rm = m_da.getRelationshipManager();
      final Relationship[] r = rm.find(me, jinge, RelationshipKinds.FILTER);
      if (r.length > 0) {
        rm.changeFlags(r[0].getId(), flags);
      } else {
        rm.addRelationship(me, jinge, RelationshipKinds.FILTER, flags);
      }

      // Update cache
      //
      m_userContext.getFilterCache().put(jinge, flags);
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public void dropUserFilter(final long user) throws ObjectNotFoundException, UnexpectedException {
    try {
      final RelationshipManager rm = m_da.getRelationshipManager();
      final Relationship[] r = rm.find(getLoggedInUserId(), user, RelationshipKinds.FILTER);
      for (final Relationship relationship : r) {
        rm.delete(relationship.getId());
      }

      // Update cache
      //
      m_userContext.getFilterCache().remove(user);
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public Relationship[] listFilters() throws UnexpectedException {
    try {
      return m_da.getRelationshipManager()
          .listByRefererAndKind(getLoggedInUserId(), RelationshipKinds.FILTER);
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public NameAssociation[] listReaders(final MessageLocator ml)
      throws UnexpectedException, NoCurrentMessageException {
    try {
      final long globid;

      if (null == ml) {
        if (-1 == m_lastReadMessageId) {
          throw new NoCurrentMessageException();
        } else {
          globid = m_lastReadMessageId;
        }
      } else {
        globid = localToGlobal(m_currentConferenceId, ml.getLocalnum());
      }

      return m_da.getMembershipManager().listReaders(globid);
    } catch (final SQLException | ObjectNotFoundException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  protected void markAsInvalid() {
    m_valid = false;
  }

  @Override
  public boolean isValid() {
    return m_valid;
  }

  protected void leaveConference() throws UnexpectedException {
    // Save message markers
    //
    m_userContext.saveMemberships(m_da.getMembershipManager());
  }

  protected boolean markMessageAsReadEx(final long conference, final int localnum)
      throws ObjectNotFoundException {
    return m_userContext.getMemberships().markAsReadEx(conference, localnum);
  }

  protected int performThreadOperation(final long thread, final int max, final MessageOperation op)
      throws UnexpectedException, ObjectNotFoundException, SelectionOverflowException {
    try {
      final long[] ids = m_da.getMessageManager().selectByThread(thread, max);
      final int top = ids.length;
      for (final long id : ids) {
        op.perform(id);
      }
      return top;
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  protected int performTreeOperation(final long root, final MessageOperation op)
      throws UnexpectedException, ObjectNotFoundException {
    try {
      final MessageManager mm = m_da.getMessageManager();
      final Stack<Long> stack = new Stack<>();
      stack.add(root);
      int n = 0;
      for (; !stack.isEmpty(); ++n) {
        // Perform operation
        //
        final long id = stack.pop();
        op.perform(id);

        // Push replies
        //
        final MessageHeader[] mh = mm.getReplies(id);
        for (final MessageHeader messageHeader : mh) {
          stack.push(messageHeader.getId());
        }
      }
      return n;
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  protected int performOperationBySubjectInConference(
      final String subject, final MessageOperation op)
      throws UnexpectedException, ObjectNotFoundException {
    final long currentConference = getCurrentConference().getId();
    try {
      final MessageManager mm = m_da.getMessageManager();
      final int[] ids = mm.getLocalMessagesBySubject(subject, currentConference);
      for (final int id : ids) {
        op.perform(localToGlobalInCurrentConference(id));
      }
      return ids.length;
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  protected int performOperationBySubject(final String subject, final MessageOperation op)
      throws UnexpectedException, ObjectNotFoundException {
    try {
      final MessageManager mm = m_da.getMessageManager();
      final long[] globalIds = mm.getMessagesBySubject(subject, getLoggedInUserId());
      for (final long globalId : globalIds) {
        op.perform(globalId);
      }
      return globalIds.length;
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  protected void markMessageAsRead(final long conference, final int localnum) throws SQLException {
    try {
      // Mark it as read in the membership list
      //
      m_userContext.getMemberships().markAsRead(conference, localnum);

      // Update last read message
      //
      m_lastReadMessageId = m_da.getMessageManager().getGlobalMessageId(conference, localnum);

    } catch (final ObjectNotFoundException e) {
      // The text was probably deleted. Do nothing.
      //
    }
  }

  @Override
  public int rollbackReads(final int n) throws UnexpectedException {
    long conf = -1;
    int count = 0;
    for (; count < n && m_readLog != null; m_readLog = m_readLog.getPrevious()) {
      conf = m_readLog.getConference();
      try {
        m_userContext.getMemberships().markAsUnread(conf, m_readLog.getLocalNum());
        ++count;
      } catch (final ObjectNotFoundException e) {
        // The conference disappeared. Not much we can do!
      }
    }

    // Go to the conference of the last unread message
    //
    try {
      if (conf != -1) {
        gotoConference(conf);
      }
    } catch (final NotMemberException e) {
      // We have signed off from that conference. Not much to do
    } catch (final ObjectNotFoundException e) {
      // The conference disappeared. Not much we can do!
    }
    return count;
  }

  @Override
  public void assertConferencePermission(final long conferenceId, final int mask)
      throws AuthorizationException, ObjectNotFoundException, UnexpectedException {
    assertConferencePermission(getLoggedInUserId(), conferenceId, mask);
  }

  public void assertConferencePermission(final long userId, final long conferenceId, final int mask)
      throws AuthorizationException, ObjectNotFoundException, UnexpectedException {
    if (!hasPermissionInConference(userId, conferenceId, mask)) {
      if (mask == ConferencePermissions.REPLY_PERMISSION) {
        throw new RepliesNotAllowedException(
            new Object[] {getCensoredName(conferenceId).getName()});
      } else if (mask == ConferencePermissions.WRITE_PERMISSION) {
        throw new OriginalsNotAllowedException(
            new Object[] {getCensoredName(conferenceId).getName()});
      } else {
        throw new AuthorizationException();
      }
    }
  }

  @Override
  public void assertModifyConference(final long conferenceId)
      throws AuthorizationException, ObjectNotFoundException, UnexpectedException {
    if (!(hasPermissionInConference(conferenceId, ConferencePermissions.ADMIN_PERMISSION)
        || getLoggedInUser().hasRights(UserPermissions.CONFERENCE_ADMIN))) {
      throw new AuthorizationException();
    }
  }

  protected void pushReplies(final long conference, final int localnum)
      throws ObjectNotFoundException, UnexpectedException {
    try {
      pushReplies(m_da.getMessageManager().getGlobalMessageId(conference, localnum));
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  protected void pushReplies(final long messageId) throws UnexpectedException {
    // Not reading in reply-tree-order? We're outta here!
    //
    if ((getLoggedInUser().getFlags1() & UserFlags.READ_REPLY_TREE) == 0) {
      return;
    }
    try {
      long[] replies = m_da.getMessageManager().getReplyIds(messageId);

      // If we're not interested in replies across conferences, we have
      // to filter the list.
      //
      if ((getLoggedInUser().getFlags1() & UserFlags.READ_CROSS_CONF_REPLIES) == 0) {
        final MessageManager mm = m_da.getMessageManager();
        int p = 0;
        for (int idx = 0; idx < replies.length; idx++) {
          final long reply = replies[idx];
          try {
            mm.getOccurrenceInConference(getCurrentConferenceId(), reply);
            replies[p++] = reply;
          } catch (final ObjectNotFoundException e) {
            // Skip...
          }
        }
        if (p != replies.length) {
          final long[] copy = replies;
          replies = new long[p];
          System.arraycopy(copy, 0, replies, 0, p);
        }
      }
      if (replies.length > 0) {
        m_replyStack = new ReplyStackFrame(replies, m_replyStack);
      }
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  protected Envelope innerReadMessage(final long globalId)
      throws ObjectNotFoundException, UnexpectedException, NoCurrentMessageException,
          AuthorizationException {
    return innerReadMessage(new MessageLocator(globalId));
  }

  protected Envelope innerReadMessage(MessageLocator locator)
      throws ObjectNotFoundException, UnexpectedException, NoCurrentMessageException,
          AuthorizationException {
    try {
      // Check that we are allowed to read this
      //
      locator = resolveLocator(locator);
      assertMessageReadPermissions(locator.getGlobalId());

      // Resolve reply to (if any)
      //
      final long conf = getCurrentConferenceId();
      final MessageManager mm = m_da.getMessageManager();
      final ConferenceManager cm = m_da.getConferenceManager();
      final Message message = mm.loadMessage(locator.getConference(), locator.getLocalnum());
      final long replyToId = message.getReplyTo();
      Envelope.RelatedMessage replyTo = null;
      if (replyToId > 0) {
        // This is a reply. Fill in info.
        //
        final MessageOccurrence occ =
            mm.getMostRelevantOccurrence(getLoggedInUserId(), conf, replyToId);
        final MessageHeader replyToMh = mm.loadMessageHeader(replyToId);
        replyTo =
            new Envelope.RelatedMessage(
                occ,
                replyToMh.getAuthor(),
                replyToMh.getAuthorName(),
                occ.getConference(),
                getCensoredName(occ.getConference()),
                occ.getConference() == conf);
      }

      // Create receiver list
      //
      final MessageOccurrence[] occ = message.getOccurrences();
      int top = occ.length;
      final NameAssociation[] receivers = new NameAssociation[top];
      for (int idx = 0; idx < top; ++idx) {
        receivers[idx] =
            new NameAssociation(
                occ[idx].getConference(), getCensoredName(occ[idx].getConference()));
      }

      // Create attributes list
      //
      final MessageAttribute[] attr = mm.getMessageAttributes(message.getId());

      // Create list of replies
      //
      final MessageHeader[] replyHeaders = mm.getReplies(message.getId());
      top = replyHeaders.length;
      final ArrayList<Envelope.RelatedMessage> list = new ArrayList<>(top);

      for (int idx = 0; idx < top; ++idx) {
        final MessageHeader each = replyHeaders[idx];
        final MessageOccurrence replyOcc =
            mm.getMostRelevantOccurrence(getLoggedInUserId(), conf, each.getId());

        // Don't show replies written by filtered users
        //
        if (m_userContext.userMatchesFilter(each.getAuthor(), FilterFlags.MESSAGES)) {
          continue;
        }

        // Don't show personal replies
        //
        if (cm.isMailbox(replyOcc.getConference())) {
          continue;
        }

        // Don't show replies in conferences we're not allowed to see
        //
        if (getName(replyOcc.getConference()).getVisibility() == Visibilities.PROTECTED
            && !hasPermissionInConference(
                replyOcc.getConference(), ConferencePermissions.READ_PERMISSION)) {
          continue;
        }

        // Add to list
        //
        list.add(
            new Envelope.RelatedMessage(
                replyOcc,
                each.getAuthor(),
                each.getAuthorName(),
                replyOcc.getConference(),
                getCensoredName(replyOcc.getConference()),
                replyOcc.getConference() == conf));
      }
      final Envelope.RelatedMessage[] replies = new Envelope.RelatedMessage[list.size()];
      list.toArray(replies);

      // Done assembling envelope. Now, mark the message as read in all
      // conferences where it appears and we are members.
      //
      final MessageOccurrence[] occs =
          mm.getVisibleOccurrences(getLoggedInUserId(), locator.getGlobalId());
      top = occs.length;
      for (int idx = 0; idx < top; ++idx) {
        final MessageOccurrence each = occs[idx];
        markMessageAsRead(each.getConference(), each.getLocalnum());
      }

      // Put it in the read log. Notice that we log only one occurrence, as
      // it would confuse users if implicitly read occurences were included.
      //
      if (top > 0) {
        appendToReadLog(occs[0]);
      }

      // Make this the "current" message
      //
      m_lastReadMessageId = locator.getGlobalId();

      // Create Envelope and return
      //
      m_stats.incNumRead();
      return new Envelope(message, toOccurrence(locator), replyTo, receivers, occ, attr, replies);
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  /** Retrieves all message attributes, if any, of the given type for the given message. */
  @Override
  public MessageAttribute[] getMatchingMessageAttributes(final long message, final short kind)
      throws UnexpectedException {
    try {
      return kind == -1
          ? m_da.getMessageManager().getMessageAttributes(message)
          : m_da.getMessageManager().getMatchingMessageAttributes(message, kind);
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  /** Retrieves all message attributes for a given message */
  @Override
  public MessageAttribute[] getMessageAttributes(final long message) throws UnexpectedException {
    return getMatchingMessageAttributes(message, (short) -1);
  }

  /** Checks that at least one occurrence of the message is readable to the logged in user. */
  protected void assertMessageReadPermissions(final long globalId)
      throws AuthorizationException, ObjectNotFoundException, UnexpectedException {
    if (!hasMessageReadPermissions(globalId)) {
      throw new AuthorizationException();
    }
  }

  /** Checks that at least one occurrence of the message is readable to the logged in user. */
  protected boolean hasMessageReadPermissions(final long globalId)
      throws ObjectNotFoundException, UnexpectedException {
    try {
      // Get all occurrences
      //
      final MessageManager mm = m_da.getMessageManager();
      final MembershipManager mbrMgr = m_da.getMembershipManager();
      final MessageOccurrence[] occs = mm.getOccurrences(globalId);
      final long me = getLoggedInUserId();

      // Check whether we have read access in at least one of them
      // Treat no occurrences at all as an object not found, rather than
      // authorization problem
      //
      final int top = occs.length;
      if (top == 0) {
        throw new ObjectNotFoundException("Messageid=" + globalId);
      }

      for (final MessageOccurrence occ : occs) {
        // Get out of here as soon as we have read access in a conference
        // where this text occurrs.
        //
        if (mbrMgr.hasPermission(me, occ.getConference(), ConferencePermissions.READ_PERMISSION)) {
          return true;
        }
      }

      // We didn't find an occurrence in a conference we have access to?
      // We're not allowed to see it, then!
      //
      return false;
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  protected long popReply() throws UnexpectedException, SQLException {
    return peekReply() != -1 ? m_replyStack.pop() : -1;
  }

  protected long peekReply() throws SQLException, UnexpectedException {
    long reply;

    // Do we have anything at all in the stack?
    //
    if (m_replyStack == null) {
      return -1;
    }

    // Loop until we have an unread reply
    //
    for (; ; m_replyStack.pop()) {

      // Frame exhausted? Try next!
      //
      if (!m_replyStack.hasMore()) {
        m_replyStack = m_replyStack.next();
      }
      if (m_replyStack == null) {
        return -1;
      }

      // Fetch next reply global id and translate into local occurrence
      //
      reply = m_replyStack.peek();
      try {
        final MessageOccurrence occ =
            m_da.getMessageManager()
                .getMostRelevantOccurrence(getLoggedInUserId(), m_currentConferenceId, reply);

        // Check that we have permission to see this one
        //
        if (!hasMessageReadPermissions(reply)) {
          continue;
        }

        // If it's unread, we're done
        //
        if (m_userContext.getMemberships().isUnread(occ.getConference(), occ.getLocalnum())) {
          break;
        }
      } catch (final ObjectNotFoundException e) {
        // Not found. Probably deleted, so just skip it!
      }
    }
    return reply;
  }

  protected void setDataAccess(final DataAccess da) {
    m_da = da;
  }

  protected void appendToReadLog(final MessageOccurrence occ) {
    m_readLog = new ReadLogItem(m_readLog, occ.getConference(), occ.getLocalnum());
  }

  @Override
  public void checkRights(final long mask) throws AuthorizationException {
    if (!getLoggedInUser().hasRights(mask)) {
      throw new AuthorizationException();
    }
  }

  @Override
  public boolean checkForUserid(final String userid) throws UnexpectedException {
    final UserManager um = m_da.getUserManager();
    try {
      um.getUserIdByLogin(userid);
      return true;
    } catch (final ObjectNotFoundException e) {
      return false;
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  /**
   * Sends an event to a specified user
   *
   * @param userId
   * @param e The event
   */
  protected void sendEvent(final long userId, final Event e) {
    // Push events onto a transaction-local queue that will
    // be flushed once the current transaction is committed.
    //
    synchronized (m_deferredEvents) {
      m_deferredEvents.add(new DeferredSend(userId, e));
    }
  }

  protected void reloadMemberships() throws SQLException, ObjectNotFoundException {
    m_userContext.reloadMemberships(m_da.getMembershipManager());
  }

  protected void flushEvents() {
    synchronized (m_deferredEvents) {
      for (final DeferredEvent m_deferredEvent : m_deferredEvents) {
        m_deferredEvent.dispatch(m_sessions);
      }
      m_deferredEvents.clear();
    }
  }

  protected void discardEvents() {
    m_deferredEvents.clear();
  }

  /**
   * Broadcasts an event to all active users
   *
   * @param e The event
   */
  protected void broadcastEvent(final Event e) {
    // Push events onto a transaction-local queue that will
    // be flushed once the current transaction is committed.
    //
    synchronized (m_deferredEvents) {
      m_deferredEvents.add(new DeferredBroadcast(e));
    }
  }

  // Implementation of EventTarget
  //
  @Override
  public void onEvent(final Event e) {
    // Catch-all method for events without a dedicated methods.
    // Just stuff it in the queue
    //
    postEvent(e);
  }

  @Override
  public void onEvent(final ChatMessageEvent e) {
    if (allowsChatInEventHandler(e.getOriginatingUser())) {
      postEvent(e);
    }
  }

  @Override
  public void onEvent(final ChatAnonymousMessageEvent e) {
    if (allowsChatInEventHandler(e.getOriginatingUser())) {
      postEvent(e);
    }
  }

  @Override
  public void onEvent(final BroadcastMessageEvent e) {
    if (allowsBroadcastInEventHandler(e.getOriginatingUser())) {
      postEvent(e);
    }
  }

  @Override
  public void onEvent(final BroadcastAnonymousMessageEvent e) {
    if (allowsBroadcastInEventHandler(e.getOriginatingUser())) {
      postEvent(e);
    }
  }

  @Override
  public void onEvent(final UserAttendanceEvent e) {
    final UserInfo ui = getLoggedInUserInEventHandler();
    if (ui.testFlags(0, UserFlags.SHOW_ATTENDANCE_MESSAGES)
        && (e.getType() != UserAttendanceEvent.AWOKE
            || ui.testFlags(0, UserFlags.SHOW_END_OF_IDLE_MESSAGE))) {
      postEvent(e);
    }
  }

  @Override
  public void onEvent(final ReloadUserProfileEvent e) {
    // Just post it!
    //
    postEvent(e);
  }

  @Override
  public void onEvent(final MessageDeletedEvent e) {
    final long conf = e.getConference();
    try {
      m_userContext.getMemberships().get(conf);
      postEvent(e);
    } catch (final ObjectNotFoundException ex) {
      // Not member. No need to notify client
      //
    }
  }

  @Override
  public synchronized void onEvent(final NewMessageEvent e) {
    // Is this a new text in our mailbox? Then we're definately interested in it
    // and should always pass it on!
    //
    final boolean debug = Logger.isDebugEnabled(this);
    if (e.getConference() == getLoggedInUserId()) {
      if (debug) {
        Logger.debug(this, "New mail. Passing event to client");
      }
      postEvent(e);
      return;
    }

    // Already have unread messages? No need to send event!
    //
    if (debug) {
      Logger.debug(this, "NewMessageEvent received. Conf=" + e.getConference());
    }
    if (m_lastSuggestedCommand == CommandSuggestions.NEXT_MESSAGE
        || m_lastSuggestedCommand == CommandSuggestions.NEXT_REPLY) {
      if (debug) {
        Logger.debug(this, "Event ignored since we already have an unread text in this conference");
      }
      return;
    }

    // Don't send notification unless we're members.
    //
    final long conf = e.getConference();
    try {
      // Last suggested command was NEXT_CONFERENCE and message
      // was not posted in current conference? No need to pass it on.
      //
      if (m_lastSuggestedCommand == CommandSuggestions.NEXT_CONFERENCE
          && conf != getCurrentConferenceId()) {
        if (debug) {
          Logger.debug(
              this, "Event ignored because we're already suggesting going to the next conference");
        }
        return;
      }

      // Try to load the membership to see if we should care.
      //
      m_userContext.getMemberships().get(conf);
      if (debug) {
        Logger.debug(this, "Passing event to client");
      }
      postEvent(e);
    } catch (final ObjectNotFoundException ex) {
      // Not a member. No need to notify client
      //
      if (debug) {
        Logger.debug(
            this, "Event ignored because we're not members where the new message was posted");
      }
      return;
    }
  }

  protected UserInfo getUserInEventHandler(final long user) {
    // Borrow a UserManager from the pool
    //
    final DataAccessPool pool = DataAccessPool.instance();
    DataAccess da = null;
    try {
      da = pool.getDataAccess();

      // Load user
      //
      return da.getUserManager().loadUser(user);
    } catch (final Exception e) {
      // We're called from an event handler, so what can we do?
      //
      Logger.error(this, "Error in event handler", e);
      return null;
    } finally {
      if (da != null) {
        pool.returnDataAccess(da);
      }
    }
  }

  protected UserInfo getLoggedInUserInEventHandler() {
    return getUserInEventHandler(getLoggedInUserId());
  }

  protected boolean testUserFlagInEventHandler(
      final long user, final int flagword, final long mask) {
    final UserInfo ui = getUserInEventHandler(user);
    if (ui == null) {
      return false;
    }
    return ui.testFlags(flagword, mask);
  }

  @Override
  public LocalMessageSearchResult[] listAllMessagesLocally(
      final long conference, final int start, final int length) throws UnexpectedException {
    try {
      return m_da.getMessageManager().listAllMessagesLocally(conference, start, length);
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public LocalMessageSearchResult[] listMessagesLocallyByAuthor(
      final long conference, final long user, final int offset, final int length)
      throws UnexpectedException {
    try {
      return m_da.getMessageManager().listMessagesLocallyByAuthor(conference, user, offset, length);
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public GlobalMessageSearchResult[] listMessagesGloballyByAuthor(
      final long user, final int offset, final int length) throws UnexpectedException {
    try {
      return removeDuplicateMessages(
          censorMessages(
              m_da.getMessageManager().listMessagesGloballyByAuthor(user, offset, length)));
    } catch (final SQLException | ObjectNotFoundException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public MessageHeader getMessageHeader(final MessageLocator locator)
      throws ObjectNotFoundException, AuthorizationException, NoCurrentMessageException,
          UnexpectedException {
    final long globalId = resolveLocator(locator).getGlobalId();
    assertMessageReadPermissions(globalId);
    try {
      return m_da.getMessageManager().loadMessageHeader(globalId);
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public void deleteConference(final long conference)
      throws AuthorizationException, UnexpectedException {
    try {
      // Do we have the right to do this?
      //
      assertModifyConference(conference);

      // So far so, so good. Go ahead and delete!
      //
      m_da.getMessageManager().deleteConference(conference);
      m_da.getNameManager().dropNamedObject(conference);
    } catch (final SQLException | ObjectNotFoundException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public Envelope getLastRulePostingInConference(final long conference)
      throws ObjectNotFoundException, NoRulesException, UnexpectedException,
          AuthorizationException {
    try {
      return readMessage(
          new MessageLocator(
              conference,
              m_da.getMessageManager()
                  .findLastOccurrenceInConferenceWithAttrStmt(
                      MessageAttributes.RULEPOST, conference)));
    } catch (final NoCurrentMessageException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    } catch (final SQLException e) {
      throw new NoRulesException();
    }
  }

  @Override
  public Envelope getLastRulePosting()
      throws ObjectNotFoundException, NoRulesException, UnexpectedException,
          AuthorizationException {
    return getLastRulePostingInConference(m_currentConferenceId);
  }

  @Override
  public MessageOccurrence storeRulePosting(final UnstoredMessage msg)
      throws AuthorizationException, UnexpectedException, ObjectNotFoundException {
    try {
      final MessageOccurrence mo = storeMessage(m_currentConferenceId, msg);
      m_da.getMessageManager()
          .addMessageAttribute(mo.getGlobalId(), MessageAttributes.RULEPOST, null);
      return mo;
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public void changeSetting(final String name, final String value)
      throws AuthorizationException, UnexpectedException {
    try {
      checkRights(UserPermissions.ADMIN);
      m_da.getSettingManager().changeSetting(name, value, 0);
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public void changeSetting(final String name, final long value)
      throws AuthorizationException, UnexpectedException {
    try {
      checkRights(UserPermissions.ADMIN);
      m_da.getSettingManager().changeSetting(name, null, value);
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public void updateLastlogin() throws ObjectNotFoundException, UnexpectedException {
    try {
      m_da.getUserManager().updateLastlogin(getLoggedInUserId());
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public SystemInformation getSystemInformation() throws UnexpectedException {
    try {
      final CacheManager cm = CacheManager.instance();
      return new SystemInformation(
          m_sessions.canLogin(),
          cm.getNameCache().getStatistics(),
          cm.getUserCache().getStatistics(),
          cm.getConferenceCache().getStatistics(),
          cm.getPermissionCache().getStatistics(),
          m_da.getUserManager().countUsers(),
          m_da.getConferenceManager().countConferences(),
          m_da.getMessageManager().countMessages());
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public GlobalMessageSearchResult[] searchMessagesGlobally(
      final String searchterm, final int offset, final int length) throws UnexpectedException {
    try {
      return removeDuplicateMessages(
          censorMessages(
              m_da.getMessageManager().searchMessagesGlobally(searchterm, offset, length)));
    } catch (final SQLException | ObjectNotFoundException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public LocalMessageSearchResult[] searchMessagesLocally(
      final long conference, final String searchterm, final int offset, final int length)
      throws UnexpectedException {
    try {
      return removeDuplicateMessages(
          m_da.getMessageManager().searchMessagesLocally(conference, searchterm, offset, length));
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public LocalMessageSearchResult[] grepMessagesLocally(
      final long conference, final String searchterm, final int offset, final int length)
      throws UnexpectedException {
    try {
      return removeDuplicateMessages(
          m_da.getMessageManager().grepMessagesLocally(conference, searchterm, offset, length));
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  protected boolean isVisibleFor(final long conferenceId, final long userId)
      throws ObjectNotFoundException, UnexpectedException {
    try {
      return m_userContext.getMemberships().getOrNull(conferenceId) != null
          || m_da.getNameManager().getNameById(conferenceId).getVisibility() == Visibilities.PUBLIC
          || m_da.getMembershipManager()
              .hasPermission(userId, conferenceId, ConferencePermissions.READ_PERMISSION);
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  protected boolean isVisible(final long conferenceId)
      throws ObjectNotFoundException, UnexpectedException {
    return isVisibleFor(conferenceId, getLoggedInUserId());
  }

  protected Name getCensoredName(final long id)
      throws ObjectNotFoundException, UnexpectedException {
    try {
      return isVisible(id)
          ? m_da.getNameManager().getNameById(id)
          : new Name("", Visibilities.PROTECTED, NameManager.UNKNOWN_KIND);

    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  private List<MessageSearchResult> innerRemoveDuplicateMessages(
      final MessageSearchResult[] messages) {
    // The list is already sorted and we want to preserve the order, so we can
    // take the easy route to making thing unique.
    //
    long lastId = -1;
    final List<MessageSearchResult> answer = new ArrayList<MessageSearchResult>(messages.length);
    for (final MessageSearchResult each : messages) {
      if (each.getGlobalId() != lastId) {
        answer.add(each);
        lastId = each.getGlobalId();
      }
    }
    return answer;
  }

  protected GlobalMessageSearchResult[] removeDuplicateMessages(
      final GlobalMessageSearchResult[] messages) {
    final List<MessageSearchResult> result = innerRemoveDuplicateMessages(messages);
    return result.toArray(new GlobalMessageSearchResult[result.size()]);
  }

  protected LocalMessageSearchResult[] removeDuplicateMessages(
      final LocalMessageSearchResult[] messages) {
    final List<MessageSearchResult> result = innerRemoveDuplicateMessages(messages);
    return result.toArray(new LocalMessageSearchResult[result.size()]);
  }

  protected GlobalMessageSearchResult[] censorMessages(final GlobalMessageSearchResult[] messages)
      throws ObjectNotFoundException, UnexpectedException {
    return (GlobalMessageSearchResult[])
        FilterUtils.applyFilter(
            messages,
            new FilterUtils.Filter() {
              @Override
              public boolean include(final Object obj) throws UnexpectedException {
                try {
                  final GlobalMessageSearchResult gms = (GlobalMessageSearchResult) obj;
                  return hasMessageReadPermissions(gms.getGlobalId())
                      && !m_userContext.userMatchesFilter(
                          gms.getAuthor().getId(), FilterFlags.MESSAGES);
                } catch (final ObjectNotFoundException e) {
                  // Not found? Certainly not to be included!
                  //
                  Logger.error(this, e);
                  return false;
                }
              }
            });
  }

  protected NameAssociation[] censorNames(final NameAssociation[] names)
      throws ObjectNotFoundException, UnexpectedException {
    return (NameAssociation[])
        FilterUtils.applyFilter(
            names,
            new FilterUtils.Filter() {
              @Override
              public boolean include(final Object obj) throws UnexpectedException {
                try {
                  return isVisible(((NameAssociation) obj).getId());
                } catch (final ObjectNotFoundException e) {
                  // Not found? Certainly not visible!
                  //
                  Logger.error(this, e);
                  return false;
                }
              }
            });
  }

  protected boolean allowsChatInEventHandler(final long sender) {
    try {
      final DataAccess da = DataAccessPool.instance().getDataAccess();
      try {
        return m_userContext.allowsChat(da.getUserManager(), sender);
      } finally {
        try {
          da.rollback();
        } finally {
          DataAccessPool.instance().returnDataAccess(da);
        }
      }
    } catch (final Exception e) {
      Logger.error(this, "Error in event handler", e);
      return false;
    }
  }

  protected boolean allowsBroadcastInEventHandler(final long sender) {
    try {
      final DataAccess da = DataAccessPool.instance().getDataAccess();
      try {
        return m_userContext.allowsBroadcast(da.getUserManager(), sender);
      } finally {
        try {
          da.rollback();
        } finally {
          DataAccessPool.instance().returnDataAccess(da);
        }
      }
    } catch (final Exception e) {
      Logger.error(this, "Error in event handler", e);
      return false;
    }
  }

  protected void innerMarkAsUnreadAtLogoutInCurrentConference(final int localnum)
      throws UnexpectedException {
    m_pendingUnreads = new ReadLogItem(m_pendingUnreads, getCurrentConferenceId(), localnum);
  }

  protected void innerMarkAsUnreadAtLogout(final long messageId) throws UnexpectedException {
    try {
      final MessageOccurrence occ =
          m_da.getMessageManager()
              .getMostRelevantOccurrence(getLoggedInUserId(), getCurrentConferenceId(), messageId);
      m_pendingUnreads = new ReadLogItem(m_pendingUnreads, occ.getConference(), occ.getLocalnum());
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    } catch (final ObjectNotFoundException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  protected void saveUnreadMarkers() throws UnexpectedException {
    try {
      final StringBuffer content = new StringBuffer();
      for (ReadLogItem each = m_pendingUnreads; each != null; each = each.getPrevious()) {
        content.append(each.externalizeToString());
        if (each.getPrevious() != null) {
          content.append(',');
        }
      }
      final FileManager fm = m_da.getFileManager();
      fm.store(getLoggedInUserId(), ".unread", content.toString());
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  protected void loadUnreadMarkers() throws ObjectNotFoundException, UnexpectedException {
    try {
      final FileManager fm = m_da.getFileManager();
      final String content = fm.read(getLoggedInUserId(), ".unread");
      for (final StringTokenizer st = new StringTokenizer(content, ","); st.hasMoreTokens(); ) {
        final String each = st.nextToken();
        final int p = each.indexOf(':');
        final String confStr = each.substring(0, p);
        final String localNumStr = each.substring(p + 1);
        m_pendingUnreads =
            new ReadLogItem(
                m_pendingUnreads, Long.parseLong(confStr), Integer.parseInt(localNumStr));
      }
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  public void applyUnreadMarkers() throws UnexpectedException {
    for (; m_pendingUnreads != null; m_pendingUnreads = m_pendingUnreads.getPrevious()) {
      try {
        m_userContext
            .getMemberships()
            .markAsUnread(m_pendingUnreads.getConference(), m_pendingUnreads.getLocalNum());
      } catch (final ObjectNotFoundException e) {
        // The conference or message was deleted. Just ignore
        //
      }
    }

    // Delete backup file (if exists)
    //
    final FileManager fm = m_da.getFileManager();
    try {
      fm.delete(getLoggedInUserId(), ".unread");
    } catch (final ObjectNotFoundException e) {
      // No file to delete, no big deal!
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public long countMessagesLocallyByAuthor(final long conference, final long user)
      throws UnexpectedException, AuthorizationException, ObjectNotFoundException {
    try {
      assertConferencePermission(conference, ConferencePermissions.READ_PERMISSION);
      final MessageManager mm = m_da.getMessageManager();
      return mm.countMessagesLocallyByAuthor(conference, user);
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public long countMessagesGloballyByAuthor(final long user) throws UnexpectedException {
    try {
      final MessageManager mm = m_da.getMessageManager();
      return mm.countMessagesGloballyByAuthor(user, getLoggedInUserId());
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public long countSearchMessagesGlobally(final String searchterm) throws UnexpectedException {
    try {
      final MessageManager mm = m_da.getMessageManager();
      return mm.countSearchMessagesGlobally(searchterm, getLoggedInUserId());
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public long countGrepMessagesLocally(final long conference, final String searchterm)
      throws UnexpectedException, AuthorizationException, ObjectNotFoundException {
    try {
      assertConferencePermission(conference, ConferencePermissions.READ_PERMISSION);
      final MessageManager mm = m_da.getMessageManager();
      return mm.countGrepMessagesLocally(conference, searchterm);
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public long countSearchMessagesLocally(final long conference, final String searchterm)
      throws UnexpectedException, AuthorizationException, ObjectNotFoundException {
    try {
      assertConferencePermission(conference, ConferencePermissions.READ_PERMISSION);
      final MessageManager mm = m_da.getMessageManager();
      return mm.countSearchMessagesLocally(conference, searchterm);
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public long countAllMessagesLocally(final long conference)
      throws UnexpectedException, AuthorizationException, ObjectNotFoundException {
    try {
      assertConferencePermission(conference, ConferencePermissions.READ_PERMISSION);
      final MessageManager mm = m_da.getMessageManager();
      return mm.countAllMessagesLocally(conference);
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public MessageSearchResult[] listCommentsGloballyToAuthor(
      final long user, final Timestamp startDate, final int offset, final int length)
      throws UnexpectedException {
    try {
      return removeDuplicateMessages(
          censorMessages(
              m_da.getMessageManager()
                  .listCommentsGloballyToAuthor(user, startDate, offset, length)));
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    } catch (final ObjectNotFoundException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public long countCommentsGloballyToAuthor(final long user, final Timestamp startDate)
      throws UnexpectedException {
    try {
      final MessageManager mm = m_da.getMessageManager();
      return mm.countCommentsGloballyToAuthor(user, getLoggedInUserId(), startDate);
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public NameAssociation[] findObjects(final String pattern) throws UnexpectedException {
    try {
      return censorNames(m_da.getNameManager().findObjects(pattern));
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    } catch (final ObjectNotFoundException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public void changeKeywords(final long id, final String keywords)
      throws UnexpectedException, ObjectNotFoundException, AuthorizationException {
    try {
      if (!canManipulateObject(id)) {
        throw new AuthorizationException();
      }
      m_da.getNameManager().changeKeywords(id, keywords);
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public void changeEmailAlias(final long id, final String emailAlias)
      throws UnexpectedException, ObjectNotFoundException, AuthorizationException {
    try {
      if (!canManipulateObject(id)) {
        throw new AuthorizationException();
      }
      m_da.getNameManager().changeEmailAlias(id, emailAlias);
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public MessageLocator resolveLocator(final MessageLocator message)
      throws ObjectNotFoundException, NoCurrentMessageException, UnexpectedException {
    if (message == null) {
      return getMostRelevantOccurrence(getCurrentConferenceId(), getCurrentMessage());
    }
    if (!message.isValid()) {
      throw new ObjectNotFoundException("Invalid message locator"); // Should not happen...
    }
    if (message.getGlobalId() == -1) {
      final long conference =
          message.getConference() != -1 ? message.getConference() : getCurrentConferenceId();
      return new MessageLocator(
          localToGlobal(conference, message.getLocalnum()), conference, message.getLocalnum());
    } else if (message.getLocalnum() == -1) {
      return getMostRelevantOccurrence(
          message.getConference() == -1 ? getCurrentConferenceId() : message.getConference(),
          message.getGlobalId());
    } else {
      return message;
    }
  }

  private MessageOccurrence toOccurrence(final MessageLocator message)
      throws ObjectNotFoundException, UnexpectedException {
    if (message instanceof MessageOccurrence) {
      return (MessageOccurrence) message;
    }
    try {
      return m_da.getMessageManager()
          .loadMessageOccurrence(message.getConference(), message.getLocalnum());
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public void addBookmark(MessageLocator message, final String annotation)
      throws UnexpectedException, ObjectNotFoundException, NoCurrentMessageException {
    try {
      message = resolveLocator(message);
      m_da.getMessageManager().addBookmark(getLoggedInUserId(), message.getGlobalId(), annotation);
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public void deleteBookmark(MessageLocator message)
      throws UnexpectedException, ObjectNotFoundException, NoCurrentMessageException {
    try {
      message = resolveLocator(message);
      m_da.getMessageManager().deleteBookmark(getLoggedInUserId(), message.getGlobalId());
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public Bookmark[] listBookmarks() throws UnexpectedException {
    try {
      return m_da.getMessageManager().listBookmarks(getLoggedInUserId());
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public MessageOccurrence postIncomingEmail(
      final String sender,
      final String receiver,
      final Date sent,
      final Date received,
      final String subject,
      final String content)
      throws EmailRecipientNotRecognizedException, EmailSenderNotRecognizedException,
          AuthorizationException, UnexpectedException {
    // Make sure we hold the apropriate privileges
    //
    checkRights(UserPermissions.POST_ANYWHERE | UserPermissions.POST_BY_PROXY);

    final UserManager um = m_da.getUserManager();
    final NameManager nm = m_da.getNameManager();
    final MessageManager mm = m_da.getMessageManager();
    try {
      // Try to map sender and receiver email
      //
      final long senderId = um.matchEmailSender(sender);
      final long receiverId = nm.findByEmailAlias(receiver);

      // Are we sending to a person or a conference?
      //
      final MessageOccurrence occ;
      final UnstoredMessage msg = new UnstoredMessage(subject, content);
      switch (getName(receiverId).getKind()) {
        case NameManager.CONFERENCE_KIND:
          /// We need to check permissions if we're posting to a conference
          //
          if (!hasPermissionInConference(
              senderId, receiverId, ConferencePermissions.WRITE_PERMISSION)) {
            throw new AuthorizationException();
          }
          occ = storeReplyAsMessage(senderId, receiverId, msg, -1);
          break;
        case NameManager.USER_KIND:
          // No need to check permissions here. Everyone can send mail
          //
          occ = storeReplyAsMail(senderId, receiverId, msg, -1);
          break;
        default:
          throw new IllegalStateException("Don't know how to handle message");
      }

      // Store sender email address, sent and received timestamps as message attributes
      //
      final long id = occ.getGlobalId();
      mm.addMessageAttribute(id, MessageAttributes.EMAIL_SENDER, sender);
      if (sent != null) {
        mm.addMessageAttribute(id, MessageAttributes.EMAIL_SENT, Long.toString(sent.getTime()));
      }
      if (received != null) {
        mm.addMessageAttribute(
            id, MessageAttributes.EMAIL_RECEIVED, Long.toString(received.getTime()));
      }
      return occ;
    } catch (final ObjectNotFoundException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    } catch (final SQLException e) {
      throw new UnexpectedException(getLoggedInUserId(), e);
    }
  }

  @Override
  public SelectedMessages getSelectedMessages() {
    return m_selectedMessages;
  }

  @Override
  public boolean selectGrepMessagesLocally(final long conference, final String searchterm)
      throws UnexpectedException {
    m_selectedMessages.setMessages(
        grepMessagesLocally(conference, searchterm, 0, ServerSessionImpl.MAX_SELECTION),
        isSelectionReversed());
    return m_selectedMessages.getMessages().length < ServerSessionImpl.MAX_SELECTION;
  }

  @Override
  public boolean selectMessagesGlobally(final String searchterm) throws UnexpectedException {
    m_selectedMessages.setMessages(
        searchMessagesGlobally(searchterm, 0, ServerSessionImpl.MAX_SELECTION),
        isSelectionReversed());
    return m_selectedMessages.getMessages().length < ServerSessionImpl.MAX_SELECTION;
  }

  @Override
  public boolean selectMessagesGloballyByAuthor(final long user) throws UnexpectedException {
    m_selectedMessages.setMessages(
        listMessagesGloballyByAuthor(user, 0, ServerSessionImpl.MAX_SELECTION),
        isSelectionReversed());
    return m_selectedMessages.getMessages().length < ServerSessionImpl.MAX_SELECTION;
  }

  @Override
  public boolean selectMessagesLocally(final long conference, final String searchterm)
      throws UnexpectedException {
    m_selectedMessages.setMessages(
        searchMessagesLocally(conference, searchterm, 0, ServerSessionImpl.MAX_SELECTION),
        isSelectionReversed());
    return m_selectedMessages.getMessages().length < ServerSessionImpl.MAX_SELECTION;
  }

  @Override
  public boolean selectMessagesLocallyByAuthor(final long conference, final long user)
      throws UnexpectedException {
    m_selectedMessages.setMessages(
        listMessagesLocallyByAuthor(conference, user, 0, ServerSessionImpl.MAX_SELECTION),
        isSelectionReversed());
    return m_selectedMessages.getMessages().length < ServerSessionImpl.MAX_SELECTION;
  }

  @Override
  public boolean selectCommentsGloballyToAuthor(final long user, final Timestamp startDate)
      throws UnexpectedException {
    m_selectedMessages.setMessages(
        listCommentsGloballyToAuthor(user, startDate, 0, ServerSessionImpl.MAX_SELECTION),
        isSelectionReversed());
    return m_selectedMessages.getMessages().length < ServerSessionImpl.MAX_SELECTION;
  }

  private boolean isSelectionReversed() {
    return (getLoggedInUser().getFlags1() & UserFlags.SELECT_YOUNGEST_FIRST) == 0;
  }

  private static interface MessageOperation {
    public void perform(long messageId) throws ObjectNotFoundException, UnexpectedException;
  }

  private abstract static class DeferredEvent {
    protected final Event m_event;

    public DeferredEvent(final Event event) {
      m_event = event;
    }

    public abstract void dispatch(SessionManager manager);
  }

  private static class DeferredSend extends DeferredEvent {
    private final long m_recipient;

    public DeferredSend(final long recipient, final Event event) {
      super(event);
      m_recipient = recipient;
    }

    @Override
    public void dispatch(final SessionManager manager) {
      manager.sendEvent(m_recipient, m_event);
    }
  }

  private static class DeferredBroadcast extends DeferredEvent {
    public DeferredBroadcast(final Event event) {
      super(event);
    }

    @Override
    public void dispatch(final SessionManager manager) {
      manager.broadcastEvent(m_event);
    }
  }

  private class HeartbeatListenerImpl implements HeartbeatListener {
    @Override
    public void heartbeat() {
      final ServerSessionImpl that = ServerSessionImpl.this;
      final long now = System.currentTimeMillis();
      if (now - that.m_lastHeartbeat > ServerSettings.getIdleNotificationThreashold()) {
        final UserInfo ui = that.getLoggedInUserInEventHandler();
        if (ui != null) {
          that.m_sessions.broadcastEvent(
              new UserAttendanceEvent(ui.getId(), ui.getName(), UserAttendanceEvent.AWOKE));
        }
      }
      that.m_lastHeartbeat = System.currentTimeMillis();
    }
  }

  private class MarkAsUnreadOperation implements MessageOperation {
    @Override
    public void perform(final long messageId) throws ObjectNotFoundException, UnexpectedException {
      innerMarkAsUnreadAtLogout(messageId);
    }
  }

  private class MarkAsReadOperation implements MessageOperation {
    @Override
    public void perform(final long messageId) throws ObjectNotFoundException, UnexpectedException {
      final ServerSessionImpl that = ServerSessionImpl.this;
      try {
        final MessageManager mm = that.m_da.getMessageManager();
        final MessageOccurrence[] mos =
            mm.getVisibleOccurrences(that.getLoggedInUserId(), messageId);
        for (int idx = 0; idx < mos.length; ++idx) {
          final MessageOccurrence mo = mos[idx];
          that.markMessageAsReadEx(mo.getConference(), mo.getLocalnum());
        }
      } catch (final SQLException e) {
        throw new UnexpectedException(that.getLoggedInUserId(), e);
      }
    }
  }

  private class SortableMembershipInfo implements Comparable<SortableMembershipInfo> {
    private final MembershipInfo m_mi;
    private final long m_parentCount;

    public SortableMembershipInfo(final MembershipInfo mi, final long parentCount) {
      m_mi = mi;
      m_parentCount = parentCount;
    }

    public MembershipInfo getMembershipInfo() {
      return m_mi;
    }

    protected long getId() {
      return m_mi.getConference();
    }

    protected long getPriority() {
      return m_mi.getPriority();
    }

    protected long getParentCount() {
      return m_parentCount;
    }

    @Override
    public int compareTo(final SortableMembershipInfo that) {
      return getParentCount() < that.getParentCount()
          ? -1
          : (getParentCount() > that.getParentCount()
              ? 1
              : (getPriority() < that.getPriority() ? -1 : 1));
    }
  }
}
