/*
 * Created on Oct 27, 2003
 *  
 * Distributed under the GPL licens.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.backend;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import nu.rydin.kom.AlreadyMemberException;
import nu.rydin.kom.AmbiguousNameException;
import nu.rydin.kom.AuthorizationException;
import nu.rydin.kom.DuplicateNameException;
import nu.rydin.kom.NoCurrentMessageException;
import nu.rydin.kom.NoMoreMessagesException;
import nu.rydin.kom.NoMoreNewsException;
import nu.rydin.kom.NotAReplyException;
import nu.rydin.kom.NotLoggedInException;
import nu.rydin.kom.NotMemberException;
import nu.rydin.kom.ObjectNotFoundException;
import nu.rydin.kom.UnexpectedException;
import nu.rydin.kom.backend.data.ConferenceManager;
import nu.rydin.kom.backend.data.MembershipManager;
import nu.rydin.kom.backend.data.MessageManager;
import nu.rydin.kom.backend.data.NameManager;
import nu.rydin.kom.backend.data.UserManager;
import nu.rydin.kom.constants.ConferencePermissions;
import nu.rydin.kom.constants.UserPermissions;
import nu.rydin.kom.events.BroadcastMessageEvent;
import nu.rydin.kom.events.ChatMessageEvent;
import nu.rydin.kom.events.Event;
import nu.rydin.kom.events.EventTarget;
import nu.rydin.kom.events.NewMessageEvent;
import nu.rydin.kom.events.UserAttendanceEvent;
import nu.rydin.kom.structs.*;
import nu.rydin.kom.structs.ConferenceInfo;
import nu.rydin.kom.structs.ConferencePermission;
import nu.rydin.kom.structs.Envelope;
import nu.rydin.kom.structs.MembershipInfo;
import nu.rydin.kom.structs.MembershipListItem;
import nu.rydin.kom.structs.Message;
import nu.rydin.kom.structs.MessageHeader;
import nu.rydin.kom.structs.MessageOccurrence;
import nu.rydin.kom.structs.NameAssociation;
import nu.rydin.kom.structs.NamedObject;
import nu.rydin.kom.structs.UnstoredMessage;
import nu.rydin.kom.structs.UserInfo;
import nu.rydin.kom.structs.UserListItem;

/**
 * @author <a href=mailto:pontus@rydin.nu>Pontus Rydin</a>
 */
public class ServerSessionImpl implements ServerSession, EventTarget
{	
	/**
	 * Id of the user of this session
	 */
	private long m_userId;
	
	/**
	 * Current conference id, or -1 if it could not be determined
	 */
	private long m_currentConferenceId;
	
	/**
	 * Time of login
	 */
	private final long m_loginTime;
	
	/**
	 * Id of last message read, or -1 if no message has been read yet.
	 */
	private long m_lastReadMessageId = -1;
	
	/**
	 * A cached list of memberships. Don't forget to reload it
	 * when things change, e.g. the user sign on or off from a conference.
	 */
	private MembershipList m_memberships;
	
	/**
	 * Reply stack.
	 */
	private ReplyStackFrame m_replyStack = null;

	/**
	 * The DataAccess object to use. Reset between transactions
	 */
	private DataAccess m_da;

	/**
	 * The currently active sessions
	 */
	private final SessionManager m_sessions;	

	/**
	 * Has this session been closed?
	 */
	private boolean m_closed = false;	
	
	/**
	 * List of incoming events
	 */
	private final LinkedList m_eventQueue = new LinkedList();
	
	/**
	 * Last suggested command
	 */
	private short m_lastSuggestedCommand = -1;

	/**
	 * Are we valid? If an attempt to gracefully shut down a session fails, 
	 * we may mark a session as invalid, thus prventing any client calls
	 * from getting through.
	 */
	private boolean m_valid = true;	
	
	public ServerSessionImpl(DataAccess da, long userId, SessionManager sessions)
	throws UnexpectedException
	{
		try
		{
			// We'll need a DataAccess while doing this
			//
			m_da = da;
			
			// Set up member variables
			//
			m_userId 			= userId;
			m_loginTime			= System.currentTimeMillis();
			m_sessions			= sessions;
						
			// Load membership infos into cache
			//
			this.reloadMemberships();
				
			// Go to first conference with unread messages
			//
			long firstConf = m_memberships.getNextConferenceWithUnreadMessages(
				userId, da.getConferenceManager());
			this.setCurrentConferenceId(firstConf != -1 ? firstConf : userId);
			
			// Invalidate DataAccess
			//
			m_da = null;
		}
		catch(SQLException e)
		{
			throw new UnexpectedException(this.getLoggedInUserId(), e);
		}
		catch(ObjectNotFoundException e)
		{
			throw new UnexpectedException(this.getLoggedInUserId(), e);
		}	
	}
	
	public void setCurrentConferenceId(long id)
	throws SQLException, ObjectNotFoundException
	{
		// We might need to save some membership stuff. 
		//
		if(m_currentConferenceId != id)
			this.leaveConference();
			
		m_currentConferenceId = id;
	}
	
	public ConferenceInfo getCurrentConference()
	{
		try
		{
			return m_da.getConferenceManager().loadConference(m_currentConferenceId);
		}
		catch(ObjectNotFoundException e)
		{
			// TODO: What do we do here? The current conference may have been 
			// deleted!
			//
			throw new RuntimeException(e);
		}
		catch(SQLException e)
		{
			// SQLExceptions here mean that something has gone terribly wrong!
			//
			throw new RuntimeException(e);
		}				
	}
	
	public long getCurrentConferenceId()
	{
		return m_currentConferenceId;
	}
	
	public UserInfo getLoggedInUser()
	{
		try
		{
			return m_da.getUserManager().loadUser(m_userId);
		}
		catch(ObjectNotFoundException e)
		{
			// The logged in user should definately be found!!!
			//
			throw new RuntimeException(e);
		}
		catch(SQLException e)
		{
			// SQLExceptions here mean that something has gone terribly wrong!
			//
			throw new RuntimeException(e);
		}		
	}
	
	public long getLoggedInUserId()
	{
		return m_userId;
	}
	
	public long getLoginTime()
	{
		return m_loginTime;
	}	
	
	public short suggestNextAction()
	throws UnexpectedException
	{
		try
		{
			// Do we have any unread replies?
			//
			if(this.peekReply() != -1)
				return m_lastSuggestedCommand = NEXT_REPLY;
			
			// Get next conference with unread messages
			//	
			long confId = m_memberships.getNextConferenceWithUnreadMessages(m_currentConferenceId,
					m_da.getConferenceManager());
	
			// Do we have any unread messages?
			//
			if(confId == -1)
				return m_lastSuggestedCommand = NO_ACTION;
				
			// Do we have messages in our current conference?
			//
			if(confId == m_currentConferenceId)
				return m_lastSuggestedCommand = NEXT_MESSAGE;
			return m_lastSuggestedCommand = NEXT_CONFERENCE;
		}
		catch(SQLException e)
		{
			throw new UnexpectedException(this.getLoggedInUserId(), e);
		}
	}
	
	public Envelope innerReadMessage(long messageId)
	throws ObjectNotFoundException, UnexpectedException
	{
		try
		{
			long conf = this.getCurrentConferenceId(); 
			return this.innerReadMessage(m_da.getMessageManager().getMostRelevantOccurrence(
				conf, messageId));
		}
		catch(SQLException e)
		{
			throw new UnexpectedException(this.getLoggedInUserId(), e);
		}
	}
	
	public Envelope readOriginalMessage()
	throws NoCurrentMessageException, NotAReplyException, ObjectNotFoundException, UnexpectedException
	{
		try
		{
			if(m_lastReadMessageId == -1)
				throw new NoCurrentMessageException();
				
			// Retrieve last message read and try to locate the message it replies to
			//
			MessageManager mm = m_da.getMessageManager();
			MessageHeader mh = mm.loadMessageHeader(m_lastReadMessageId);
			long replyTo = mh.getReplyTo();
			if(replyTo == -1)
				throw new NotAReplyException();
				
			// We now know the message number. Go ahead and load it
			//
			return this.innerReadMessage(replyTo);
		}
		catch(SQLException e)
		{
			throw new UnexpectedException(this.getLoggedInUserId(), e);
		}
		
	}
		
	public Envelope readLocalMessage(int localnum)
	throws ObjectNotFoundException, UnexpectedException
	{
		return this.readLocalMessage(this.getCurrentConferenceId(), localnum);
	}
	
	
	public Envelope readLocalMessage(long conf, int localnum)
	throws ObjectNotFoundException, UnexpectedException
	{
		try
		{
			return this.innerReadMessage(m_da.getMessageManager().loadMessageOccurrence(conf, localnum));
		}
		catch(SQLException e)
		{
			throw new UnexpectedException(this.getLoggedInUserId(), e);
		}
	}
	
	public Envelope readNextMessage()
	throws NoMoreMessagesException, ObjectNotFoundException, UnexpectedException
	{
		try
		{
			long confId = this.getCurrentConferenceId(); 
			int next = m_memberships.getNextMessageInConference(confId, m_da.getConferenceManager());
			if(next == -1)
				throw new NoMoreMessagesException();
			this.pushReplies(confId, next);
			return this.readLocalMessage(confId, next);
		}
		catch(SQLException e)
		{
			throw new UnexpectedException(this.getLoggedInUserId(), e);
		}
	}
	
	public Envelope readNextReply()
	throws NoMoreMessagesException, ObjectNotFoundException, UnexpectedException
	{
		try
		{ 
			long next = this.popReply();
			if(next == -1)
				throw new NoMoreMessagesException();
			this.pushReplies(next);				
			return this.innerReadMessage(next);
		}
		catch(SQLException e)
		{
			throw new UnexpectedException(this.getLoggedInUserId(), e);
		}
	}			
		
	public void createConference(String fullname, int permissions, short visibility, long replyConf)
	throws UnexpectedException, AmbiguousNameException, DuplicateNameException, AuthorizationException
	{
		this.checkRights(UserPermissions.CREATE_CONFERENCE);
		try
		{
			long userId = this.getLoggedInUserId();
			long confId = m_da.getConferenceManager().addConference(fullname, userId, permissions, visibility, replyConf);

			// Add membership for administrator
			//
			m_da.getMembershipManager().signup(userId, confId, 0, ConferencePermissions.ALL_PERMISSIONS, 0);
				
			// Flush membership cache
			//
			this.reloadMemberships();
		}
		catch(SQLException e)
		{
			throw new UnexpectedException(this.getLoggedInUserId(), e);
		}
		catch(AlreadyMemberException e)
		{
			// Already member of a conference we just created? Huh?!
			//
			throw new UnexpectedException(this.getLoggedInUserId(), e);
		}
		catch(ObjectNotFoundException e)
		{
			// User or newly created conference not found? Huh?!
			//
			throw new UnexpectedException(this.getLoggedInUserId(), e);
		}
	}
	
	public void gotoConference(long id)
	throws UnexpectedException, ObjectNotFoundException, NotMemberException
	{	
		try
		{
			// Are we members?
			//
			if(!m_da.getMembershipManager().isMember(this.getLoggedInUserId(), id))
				throw new NotMemberException(m_da.getNameManager().getNameById(id));
				
			// All set! Go there!
			//
			this.setCurrentConferenceId(id);
		}
		catch(SQLException e)
		{
			throw new UnexpectedException(this.getLoggedInUserId(), e);
		}
	}
	
	public long gotoNextConference()
	throws NoMoreNewsException, UnexpectedException
	{
		try
		{
			long nextId = m_memberships.getNextConferenceWithUnreadMessages(
				this.getCurrentConferenceId(), m_da.getConferenceManager());
		
			// Going nowhere or going to the same conference? We're outta here!
			//
			if(nextId == -1 || nextId == this.getCurrentConferenceId())
				throw new NoMoreNewsException();
		
			// Move focus...
			//
			this.setCurrentConferenceId(nextId);	
			return nextId;
		}
		catch(SQLException e)
		{
			throw new UnexpectedException(this.getLoggedInUserId(), e);
		}
		catch(ObjectNotFoundException e)
		{
			throw new UnexpectedException(this.getLoggedInUserId(), e);
		}
	}

	
	public void createUser(String userid, String password, String fullname, String address1,
		String address2, String address3, String address4, String phoneno1, 
		String phoneno2, String email1, String email2, String url, String charset, long flags, long rights)
	throws UnexpectedException, AmbiguousNameException, DuplicateNameException, AuthorizationException
	{
		this.checkRights(UserPermissions.CREATE_USER);
		try
		{
			m_da.getUserManager().addUser(userid, password, fullname, address1, address2, address3, address4, 
				phoneno1, phoneno2, email1, email2, url, charset, flags, rights);
		}
		catch(SQLException e)
		{
			throw new UnexpectedException(this.getLoggedInUserId(), e);
		}
		catch(NoSuchAlgorithmException e)
		{
			throw new UnexpectedException(this.getLoggedInUserId(), e);
		}
	}
	
		
	public void close()
	throws UnexpectedException
	{
		try
		{
			// Send notification!
			//
			m_sessions.broadcastEvent(new UserAttendanceEvent(m_userId, 
				this.getUser(m_userId).getName(), UserAttendanceEvent.LOGOUT)); 

			// Make sure all message markers are saved
			//
			this.leaveConference();
			m_sessions.unRegisterSession(this);
			m_closed = true;			
		}
		catch(SQLException e)
		{
			throw new UnexpectedException(this.getLoggedInUserId(), e);
		}
		catch(ObjectNotFoundException e)
		{
			throw new UnexpectedException(this.getLoggedInUserId(), e);
		}				
	}	
	
	public void finalize()
	{
		try
		{
			if(!m_closed)
				this.close();
		}
		catch(UnexpectedException e)
		{
			e.printStackTrace();
		}
	}
	
	public int countUnread(long conference)
	throws ObjectNotFoundException, UnexpectedException
	{
		try
		{
			return m_memberships.countUnread(conference, m_da.getConferenceManager());
		}
		catch(SQLException e)
		{
			throw new UnexpectedException(this.getLoggedInUserId(), e);
		}
	}
	
	public NameAssociation[] getAssociationsForPattern(String pattern)
	throws UnexpectedException
	{
		try
		{
			return m_da.getNameManager().getAssociationsByPattern(pattern);
		}
		catch(SQLException e)
		{
			throw new UnexpectedException(this.getLoggedInUserId(), e);
		}
	}
	
	public NameAssociation[] getAssociationsForPatternAndKind(String pattern, short kind)
	throws UnexpectedException
	{
		try
		{
			return m_da.getNameManager().getAssociationsByPatternAndKind(pattern, kind);
		}
		catch(SQLException e)
		{
			throw new UnexpectedException(this.getLoggedInUserId(), e);
		}
	}
	
	public MessageOccurrence storeMessage(UnstoredMessage msg)
	throws AuthorizationException, UnexpectedException
	{
		return this.storeReplyInCurrentConference(msg, -1L);
	}
	
	public MessageOccurrence storeReplyToCurrentMessage(UnstoredMessage msg)
	throws NoCurrentMessageException, AuthorizationException, UnexpectedException
	{
		if(m_lastReadMessageId == -1)
			throw new NoCurrentMessageException();
		return this.storeReplyInCurrentConference(msg, m_lastReadMessageId);	
	}
	
	public MessageOccurrence storeReplyInCurrentConference(UnstoredMessage msg, long replyTo)
	throws UnexpectedException, AuthorizationException
	{
		try
		{
			// Determine target conference
			//
			long targetConf;
			if(replyTo != -1)
			{
				MessageOccurrence occ = m_da.getMessageManager().getMostRelevantOccurrence(
					this.getCurrentConferenceId(), replyTo);
				ConferenceInfo ci = m_da.getConferenceManager().loadConference(occ.getConference());
				
				// Does the conference specify a separate reply conference?
				//
				targetConf = ci.getReplyConf();
				if(targetConf == -1)
					targetConf = this.getCurrentConferenceId();
			}
			else
				targetConf = this.getCurrentConferenceId();
			return this.storeReply(targetConf, msg, replyTo);
		}
		catch(ObjectNotFoundException e)
		{
			throw new UnexpectedException(this.getLoggedInUserId(), e);
		}
		catch(SQLException e)
		{
			throw new UnexpectedException(this.getLoggedInUserId(), e);
		}
	}
	
	public MessageOccurrence storeReply(long conference, UnstoredMessage msg, long replyTo)
	throws AuthorizationException, UnexpectedException
	{
		try
		{
			long user = this.getLoggedInUserId();
			
			// Check that we have the permission to write here. If it's a reply, we should
			// try check if we have the right to reply. It's ok to be able to reply without
			// being able to write. Great for conferences where users are only allowed to
			// reply to something posted by a moderator.
			//
			if(replyTo == -1)
				this.assertConferencePermission(conference, ConferencePermissions.WRITE_PERMISSION);
			else
				this.assertConferencePermission(conference, ConferencePermissions.REPLY_PERMISSION);
			
			// If this is a reply, we 
			
			MessageManager mm = m_da.getMessageManager();  
			MessageOccurrence occ = mm.addMessage(user,
				m_da.getNameManager().getNameById(user),
				conference, replyTo, msg.getSubject(), msg.getBody());
			this.markMessageAsRead(conference, occ.getLocalnum());
			
			// Are we replying to a mail? In that case, store the mail in the recipient's
			// mailbox too!
			//
			if(replyTo != -1 && conference == this.getLoggedInUserId())
			{
				MessageHeader mh = mm.loadMessageHeader(replyTo);
				mm.createMessageOccurrence(occ.getGlobalId(), MessageManager.ACTION_COPIED, 
					this.getLoggedInUserId(), this.getName(this.getLoggedInUserId()), mh.getAuthor());		
			}
			
			// Notify the rest of the world that there is a new message!
			//
			this.broadcastEvent(
				new NewMessageEvent(this.getLoggedInUserId(), occ.getConference(), occ.getLocalnum(), 
					occ.getGlobalId()));
			return occ;
		}
		catch(SQLException e)
		{
			throw new UnexpectedException(this.getLoggedInUserId(), e);
		}
		catch(ObjectNotFoundException e)
		{
			throw new UnexpectedException(this.getLoggedInUserId(), e);
		}	
	}

	//TODO (skrolle) Why did I create this when it's never used? :-)
	public void storeNoCommentToCurrentMessage()
	throws NoCurrentMessageException, AuthorizationException, UnexpectedException
	{
		if(m_lastReadMessageId == -1)
			throw new NoCurrentMessageException();
		this.storeNoComment(m_lastReadMessageId);	
	}
	
	public void storeNoComment(long message)
	throws UnexpectedException, AuthorizationException
	{
		try
		{
			// Determine conference of message
			long targetConf = m_da.getMessageManager().getFirstOccurrence(message).getConference();

			// Check that we have the permission to write there.
			this.assertConferencePermission(targetConf, ConferencePermissions.WRITE_PERMISSION);

			MessageManager mm = m_da.getMessageManager(); 

			long me = this.getLoggedInUserId();

			//TODO (skrolle) Add userid to payload
			//TODO (skrolle) Scrap temporary shitty payload construction mechanism
			mm.addMessageAttribute(message, MessageManager.ATTR_NOCOMMENT, MessageAttribute.constructNoCommentPayload(this.getLoggedInUser().getName()));
		}
		catch(ObjectNotFoundException e)
		{
			throw new UnexpectedException(this.getLoggedInUserId(), e);
		}
		catch(SQLException e)
		{
			throw new UnexpectedException(this.getLoggedInUserId(), e);
		}
	}

	
	public MessageOccurrence storeReplyToLocal(UnstoredMessage msg, long replyToConfId, int replyToLocalnum)
	throws AuthorizationException, ObjectNotFoundException, UnexpectedException
	{
		try
		{
			long replyTo = m_da.getMessageManager().getGlobalMessageId(replyToConfId, replyToLocalnum);
			return this.storeReplyInCurrentConference(msg, replyTo);
		}
		catch(SQLException e)
		{
			throw new UnexpectedException(this.getLoggedInUserId(), e);
		}
		catch(ObjectNotFoundException e)
		{
			throw new UnexpectedException(this.getLoggedInUserId(), e);
		}	
	}
	
	public MessageOccurrence storeReplyToLocalInCurrentConference(UnstoredMessage msg, int replyToLocalnum)
	throws AuthorizationException, ObjectNotFoundException, UnexpectedException
	{
		return this.storeReplyToLocal(msg, this.getCurrentConferenceId(), replyToLocalnum);
	}
	
	public long localToGobal(long conferenceId, int localnum)
	throws ObjectNotFoundException, UnexpectedException
	{
		try
		{
			return m_da.getMessageManager().getGlobalMessageId(conferenceId, localnum);
		}
		catch(SQLException e)
		{
			throw new UnexpectedException(this.getLoggedInUserId(), e);
		}
	}
	
	public MessageOccurrence storeMail(UnstoredMessage msg, long user)
	throws ObjectNotFoundException, UnexpectedException
	{
		try
		{
			// Store message in recipient's mailbox
			//
			MessageManager mm = m_da.getMessageManager();
			long me = this.getLoggedInUserId();
			MessageOccurrence occ = mm.addMessage(me, m_da.getNameManager().getNameById(me), 
				user, -1, msg.getSubject(), msg.getBody()); 
			
			// Store a copy in sender's mailbox
			//
			MessageOccurrence copy = mm.createMessageOccurrence(
				occ.getGlobalId(), MessageManager.ACTION_COPIED, me, this.getName(me), me);
			
			// Mark local copy as read
			//
			this.markMessageAsRead(me, copy.getLocalnum());
			return occ;
		}
		catch(SQLException e)
		{
			throw new UnexpectedException(this.getLoggedInUserId(), e);
		}
	}
	
	public void copyMessage(long globalNum, long conferenceId)
	throws AuthorizationException, ObjectNotFoundException, UnexpectedException
	{
		try
		{
			// Check permissions.
			// TODO: Maybe a special copy-permission would be cool?
			//
			long me = this.getLoggedInUserId();
			
			//TODO (skrolle) Why not use assertConferencePermission?
			MembershipManager mbr = m_da.getMembershipManager();
			if(!mbr.hasPermission(me, conferenceId, ConferencePermissions.WRITE_PERMISSION))
				throw new AuthorizationException();
			MessageManager mm = m_da.getMessageManager();
			MessageOccurrence occ = mm.createMessageOccurrence(globalNum, MessageManager.ACTION_COPIED, 
				me, this.getName(me), conferenceId);
			
			// Notify the rest of the world that there is a new message!
			//
			this.broadcastEvent(new NewMessageEvent(me, conferenceId, occ.getLocalnum(), globalNum));
		}
		catch(SQLException e)
		{
			throw new UnexpectedException(this.getLoggedInUserId(), e);
		}		
	}
	
	public MessageOccurrence globalToLocalInConference(long conferenceId, long globalNum)
	throws ObjectNotFoundException, UnexpectedException
	{
		try
		{
			return m_da.getMessageManager().getMostRelevantOccurrence(conferenceId, globalNum);
		}
		catch(SQLException e)
		{
			throw new UnexpectedException(this.getLoggedInUserId(), e);
		}
	}
	
	public MessageOccurrence globalToLocal(long globalNum)
	throws ObjectNotFoundException, UnexpectedException
	{
		return this.globalToLocalInConference(m_currentConferenceId, globalNum);
	}
	
	public long getCurrentMessage()
	throws NoCurrentMessageException
	{
		if(m_lastReadMessageId == -1)
			throw new NoCurrentMessageException();
		return m_lastReadMessageId;
	}
	
	public MessageOccurrence getCurrentMessageOccurrence()
	throws NoCurrentMessageException, UnexpectedException
	{
		try
		{
			return m_da.getMessageManager().getMostRelevantOccurrence(m_currentConferenceId, 
				this.getCurrentMessage());
		}
		catch(SQLException e)
		{
			throw new UnexpectedException(this.getLoggedInUserId(), e);
		}
		catch(ObjectNotFoundException e)
		{
			throw new UnexpectedException(this.getLoggedInUserId(), e);
		}
	}
	
	public String signup(long conferenceId)
	throws ObjectNotFoundException, AlreadyMemberException, UnexpectedException, AuthorizationException
	{
		try
		{
			long user = this.getLoggedInUserId();
						
			// Add membership (and grant all permissions)
			//
			m_da.getMembershipManager().signup(user, conferenceId, 0, 0, 0);
			
			// Flush membership cache
			//
			this.reloadMemberships();
			
			// Return full name of conference
			//
			return m_da.getNameManager().getNameById(conferenceId);
		}
		catch(SQLException e)
		{
			throw new UnexpectedException(this.getLoggedInUserId(), e);
		}
	}
	
	public UserInfo getUser(long userId)
	throws ObjectNotFoundException, UnexpectedException
	{
		try
		{
			return m_da.getUserManager().loadUser(userId);
		}
		catch(SQLException e)
		{
			throw new UnexpectedException(this.getLoggedInUserId(), e);		
		}
	}
	
	public ConferenceInfo getConference(long conferenceId)
	throws ObjectNotFoundException, UnexpectedException
	{
		try
		{
			return m_da.getConferenceManager().loadConference(conferenceId);
		}
		catch(SQLException e)
		{
			throw new UnexpectedException(this.getLoggedInUserId(), e);		
		}
	}
	
	public NamedObject getNamedObject(long id)
	throws ObjectNotFoundException, UnexpectedException
	{
		try
		{
			// First, try users
			//
			return this.getUser(id);
		}
		catch(ObjectNotFoundException e)
		{
			// Not a user. Try conference!
			//
			return this.getConference(id);
		}
	}
	
	public NameAssociation[] listMemberships(long userId)
	throws ObjectNotFoundException, UnexpectedException
	{
		try
		{
			NameManager nm = m_da.getNameManager();
			MembershipInfo[] mi = m_da.getMembershipManager().listMembershipsByUser(userId);
			int top = mi.length;
			NameAssociation[] answer = new NameAssociation[top];
			for(int idx = 0; idx < top; ++idx)
			{	
				long conf = mi[idx].getConference();
				try
				{
					answer[idx] = new NameAssociation(conf, nm.getNameById(conf));
				}
				catch(ObjectNotFoundException e)
				{
					// Probably delete while we were listing
					//
					answer[idx] = new NameAssociation(conf, "???");
				}
			}
			return answer;
		}
		catch(SQLException e)
		{
			throw new UnexpectedException(this.getLoggedInUserId(), e);
		}
	}
	
	public String getName(long id)
	throws ObjectNotFoundException, UnexpectedException
	{
		try
		{
			return m_da.getNameManager().getNameById(id);
		}
		catch(SQLException e)
		{
			throw new UnexpectedException(this.getLoggedInUserId(), e);
		}
	}
	
	public String[] getNames(long[] ids)
	throws ObjectNotFoundException, UnexpectedException
	{
		int top = ids.length;
		String[] names = new String[top];
		for(int idx = 0; idx < top; ++idx)
			names[idx] = this.getName(ids[idx]);
		return names;
	}
	
	public String getDebugString()
	{
		ByteArrayOutputStream s = new ByteArrayOutputStream();
		m_memberships.printDebugInfo(new PrintStream(s));
		return s.toString();	
	}
	
	public void changeUnread(int nUnread)
	throws ObjectNotFoundException, UnexpectedException
	{
		try
		{
			// Update message markers
			//
			ConferenceManager cm = m_da.getConferenceManager();
			ConferenceInfo ci = cm.loadConference(this.getCurrentConferenceId());
			int high = ci.getLastMessage();
			high = Math.max(0, high - nUnread);
			m_memberships.changeRead(ci.getId(), ci.getFirstMessage(), high);
			
			// Discard reply stack
			//
			m_replyStack = null;
			
		}
		catch(SQLException e)
		{
			throw new UnexpectedException(this.getLoggedInUserId(), e);
		}
	}
	
	public MembershipListItem[] listNews()
	throws UnexpectedException
	{
		try
		{
			ConferenceManager cm = m_da.getConferenceManager();
			NameManager nm = m_da.getNameManager();
			MembershipInfo[] m = m_memberships.getMemberships();
			int top = m.length;
			List list = new ArrayList(top);
			for(int idx = 0; idx < top; ++idx)
			{
				try
				{
					MembershipInfo each = m[idx];
					long conf = each.getConference();
					int n = m_memberships.countUnread(conf, cm); 
					if(n > 0)
						list.add(new MembershipListItem(new NameAssociation(conf, nm.getNameById(conf)), n));
				}
				catch(ObjectNotFoundException e)
				{
					// Probably deleted. Just skip!
				}
			}
			MembershipListItem[] answer = new MembershipListItem[list.size()];
			list.toArray(answer);
			return answer;
		}
		catch(SQLException e)
		{
			throw new UnexpectedException(this.getLoggedInUserId(), e);
		}
	}
	
	public UserListItem[] listLoggedInUsers()
	throws UnexpectedException
	{
		try
		{
			ServerSession[] sessions = m_sessions.listSessions();
			UserManager um = m_da.getUserManager();
			NameManager nm = m_da.getNameManager();
			int top = sessions.length;
			UserListItem[] answer = new UserListItem[top];
			for(int idx = 0; idx < top; ++idx)
			{
				ServerSession session = sessions[idx];
				long confId = session.getCurrentConferenceId();
				long user = session.getLoggedInUserId();
				String userName = "???";
				boolean inMailbox = false;
				try
				{
					userName = nm.getNameById(user);
					inMailbox = confId == user;
				}
				catch(ObjectNotFoundException e)
				{
					// User deleted! Strange, but we allow it. User will be displayed as "???"
					//
				}
				String conferenceName = "???";
				try
				{
					conferenceName = nm.getNameById(confId);
				}
				catch(ObjectNotFoundException e)
				{
					// Conference deleted. Display as "???"
				}
				answer[idx] = new UserListItem(user, userName, (short) 0, conferenceName, inMailbox); 
			}
			return answer;
		}
		catch(SQLException e)
		{
			throw new UnexpectedException(this.getLoggedInUserId(), e);
		}
	}
	
	public synchronized void postEvent(Event e)
	{
		m_eventQueue.addLast(e);
		this.notify();
	}
	
	public synchronized Event pollEvent(int timeoutMs)
	throws InterruptedException
	{
		Event e = null;
		if(m_eventQueue.isEmpty())
			this.wait(timeoutMs);
		if(m_eventQueue.isEmpty())
			return null;
		return (Event) m_eventQueue.removeFirst();
	}
	
	public void sendChatMessage(long userId, String message)
	throws NotLoggedInException
	{
		if(m_sessions.getSession(userId) == null)
			throw new NotLoggedInException();
		m_sessions.sendEvent(userId, new ChatMessageEvent(userId, this.getLoggedInUserId(), 
			this.getLoggedInUser().getName(), message));
	}
	
	public void broadcastChatMessage(String message)
	{
		m_sessions.broadcastEvent(new BroadcastMessageEvent(this.getLoggedInUserId(), 
			this.getLoggedInUser().getName(), message));
	}
	
	public void updateCharacterset(String charset)
	throws UnexpectedException
	{
		try
		{
			m_da.getUserManager().updateCharacterset(this.getLoggedInUserId(), charset);
		}
		catch(SQLException e)
		{
			throw new UnexpectedException(this.getLoggedInUserId(), e);
		}
		catch(ObjectNotFoundException e)
		{
			throw new UnexpectedException(this.getLoggedInUserId(), e);
		}
	}
	
	public void setConferencePermissions(long conf, long user, int permissions)
	throws UnexpectedException
	{
		try
		{
			ServerSession session = m_sessions.getSession(user);
			MembershipManager mm = m_da.getMembershipManager();
			
			// Get hold of conference permission set and calculate negation mask.
			// Any permission granted by default for the conference, but is denied
			// in user-specific mask should be included in the negation mask.
			//
			int c = this.getCurrentConference().getPermissions();
			int negations = c & ~permissions;  
			mm.updateConferencePermissions(user, conf, permissions, negations);
		}
		catch(SQLException e)
		{
			throw new UnexpectedException(this.getLoggedInUserId(), e);
		}
	}
	
	public void setConferencePermissionsInCurrentConference(long user, int permissions)
	throws UnexpectedException
	{
		this.setConferencePermissions(this.getCurrentConferenceId(), user, permissions);
	}
	
	
	public void revokeConferencePermissions(long conf, long user)
	throws UnexpectedException
	{
		try
		{
			m_da.getMembershipManager().updateConferencePermissions(user, conf, 0, 0xffffffff);
		}
		catch(SQLException e)
		{
			throw new UnexpectedException(this.getLoggedInUserId(), e);
		}		
	}
	
	public void revokeConferencePermissionsInCurrentConference(long user)
	throws UnexpectedException
	{
		this.revokeConferencePermissions(this.getCurrentConferenceId(), user);
	}
	
	public ConferencePermission[] listConferencePermissions(long conf)
	throws UnexpectedException
	{
		try
		{
			return m_da.getMembershipManager().listPermissions(conf);
		}
		catch(SQLException e)
		{
			throw new UnexpectedException(this.getLoggedInUserId(), e);
		}
	}
	
	public ConferencePermission[] listConferencePermissionsInCurrentConference()
	throws UnexpectedException
	{
		return this.listConferencePermissions(this.getCurrentConferenceId());
	}
	
	public int getPermissionsInConference(long conferenceId)
	throws UnexpectedException
	{
		return this.getUserPermissionsInConference(this.getLoggedInUserId(), conferenceId);
	}
	
	public int getUserPermissionsInConference(long userId, long conferenceId)
	throws UnexpectedException
	{
		try
		{
			return m_da.getMembershipManager().getPermissions(userId, conferenceId);
		}
		catch(SQLException e)
		{
			throw new UnexpectedException(this.getLoggedInUserId(), e);
		}
	}		
	
	public int getPermissionsInCurrentConference()
	throws UnexpectedException
	{
		try
		{
			return m_da.getMembershipManager().getPermissions(this.getLoggedInUserId(), 
				this.getCurrentConferenceId());
		}
		catch(SQLException e)
		{
			throw new UnexpectedException(this.getLoggedInUserId(), e);
		}		
	}
	
	public boolean hasPermissionInConference(long conferenceId, int mask)
	throws ObjectNotFoundException, UnexpectedException
	{
		try
		{
			// Do we have the permission to disregard conference permissions?
			//
			if(this.getLoggedInUser().hasRights(UserPermissions.DISREGARD_CONF_PERM))
				return true;				
			return m_da.getMembershipManager().hasPermission(this.getLoggedInUserId(), conferenceId, mask);
		}
		catch(SQLException e)
		{
			throw new UnexpectedException(this.getLoggedInUserId(), e);
		}
	}
	
	public boolean hasPermissionInCurrentConference(int mask)
	throws AuthorizationException, ObjectNotFoundException, UnexpectedException
	{
		return hasPermissionInConference(this.getCurrentConferenceId(), mask);
	}
	
	public void renameObject(long id, String newName)
	throws DuplicateNameException, ObjectNotFoundException, AuthorizationException, UnexpectedException
	{
		try
		{
			if(!this.userCanChangeNameOf(id))
				throw new AuthorizationException();
			m_da.getNameManager().renameObject(id, newName);
		}
		catch(SQLException e)
		{
			throw new UnexpectedException(this.getLoggedInUserId(), e);
		}
	}
	
	public void changeSuffixOfLoggedInUser(String suffix)
	throws DuplicateNameException, ObjectNotFoundException, AuthorizationException, UnexpectedException
	{
		try
		{
			long me = this.getLoggedInUserId();
			String name = this.getName(me);
			m_da.getNameManager().renameObject(me, NameUtils.addSuffix(name, suffix));
		}
		catch(SQLException e)
		{
			throw new UnexpectedException(this.getLoggedInUserId(), e);
		}			
	}
	
	public void changeSuffixOfUser(long id, String suffix)
	throws DuplicateNameException, ObjectNotFoundException, AuthorizationException, UnexpectedException
	{
		try
		{
			this.checkRights(UserPermissions.CHANGE_ANY_NAME);
			String name = this.getName(id);
			m_da.getNameManager().renameObject(id, NameUtils.addSuffix(name, suffix));
		}
		catch(SQLException e)
		{
			throw new UnexpectedException(this.getLoggedInUserId(), e);
		}			
	}
	
	
	public boolean userCanChangeNameOf(long id)
	throws UnexpectedException
	{
		// Do we have sysop rights? Anything goes!
		//
		if(this.getLoggedInUser().hasRights(UserPermissions.CHANGE_ANY_NAME))
			return true;
			
		// Otherwise, we may only change names of conferences we're the admin
		// of.
		//
		try
		{
			return this.hasPermissionInConference(id, ConferencePermissions.ADMIN_PERMISSION);
		}
		catch(ObjectNotFoundException e)
		{
			// Conference not found. It's probably a user then, and we 
			// don't have the right to admin rights for that.
			//
			return false;
		}
	}


	protected void markAsInvalid()
	{
		m_valid = false;
	}
	
	protected boolean isValid()
	{
		return m_valid;
	}
	
	protected void leaveConference()
	throws SQLException
	{
		// Save message markers
		//
		m_memberships.save(m_userId, m_da.getMembershipManager());
	}
	
	protected void reloadMemberships()
	throws ObjectNotFoundException, SQLException
	{
		// Load membership infos into cache
		//
		if(m_memberships != null)
			m_memberships.save(m_userId, m_da.getMembershipManager());
		m_memberships = new MembershipList(m_da.getMembershipManager().listMembershipsByUser(m_userId));
	}
	
	protected void markMessageAsRead(long conference, int localnum)
	throws SQLException
	{
		try
		{
			// Mark it as read in the membership list
			//
			m_memberships.markAsRead(conference, localnum);
			
			// Update last read message
			//
			m_lastReadMessageId = m_da.getMessageManager().getGlobalMessageId(conference, localnum);
			
		}
		catch(ObjectNotFoundException e)
		{
			// The text was probably deleted. Do nothing.
			//
		}
	}
	
	protected void assertConferencePermission(long conferenceId, int mask)
	throws AuthorizationException, ObjectNotFoundException, UnexpectedException
	{
		if(!this.hasPermissionInConference(conferenceId, mask))
			throw new AuthorizationException();
	}
		
	protected void pushReplies(long conference, int localnum)
	throws ObjectNotFoundException, UnexpectedException
	{
		try
		{
			this.pushReplies(m_da.getMessageManager().getGlobalMessageId(conference, localnum));
		}
		catch(SQLException e)
		{
			throw new UnexpectedException(this.getLoggedInUserId(), e);	
		}
	}
	
	protected void pushReplies(long messageId)
	throws ObjectNotFoundException, UnexpectedException
	{
		try
		{
			long[] replies = m_da.getMessageManager().getReplyIds(messageId);
			if(replies.length > 0)
				m_replyStack = new ReplyStackFrame(replies, m_replyStack);
		}
		catch(SQLException e)
		{
			throw new UnexpectedException(this.getLoggedInUserId(), e);	
		}
	}
		
	protected Envelope innerReadMessage(MessageOccurrence primaryOcc)
	throws ObjectNotFoundException, UnexpectedException
	{
		try
		{
			// Resolve reply to (if any)
			//
			long conf = this.getCurrentConferenceId();
			MessageManager mm = m_da.getMessageManager();
			NameManager nm = m_da.getNameManager();
			Message message = mm.loadMessage(primaryOcc.getConference(), primaryOcc.getLocalnum());
			long replyToId = message.getReplyTo();
			Envelope.RelatedMessage replyTo = null;
			if(replyToId > 0)
			{
				// This is a reply. Fill in info.
				//
				MessageOccurrence occ = mm.getMostRelevantOccurrence(conf, replyToId);
				replyTo = new Envelope.RelatedMessage(occ, mm.loadMessageHeader(replyToId).getAuthorName(), 
					nm.getNameById(occ.getConference()), occ.getConference() == conf);
			} 
				
			// Create receiver list
			//
			MessageOccurrence[] occ = message.getOccurrences();
			int top = occ.length;
			String[] receivers = new String[top]; 
			for(int idx = 0; idx < top; ++idx)
				receivers[idx] = nm.getNameById(occ[idx].getConference());  
			
			// Create attributes list
			//
			MessageAttribute[] attr = mm.getMessageAttributes(message.getId());
			
			// Create list of replies
			//
			MessageHeader[] replyHeaders = mm.getReplies(message.getId());
			top = replyHeaders.length;
			Envelope.RelatedMessage[] replies = new Envelope.RelatedMessage[top];
			for(int idx = 0; idx < top; ++idx)
			{
				MessageHeader each = replyHeaders[idx];
				MessageOccurrence replyOcc = mm.getMostRelevantOccurrence(conf, each.getId()); 
				replies[idx] = new Envelope.RelatedMessage(replyOcc, each.getAuthorName(),
					nm.getNameById(replyOcc.getConference()), replyOcc.getConference() == conf);  
			}
			
			// Done assembling envelope. Now, mark the message as read in all
			// conferences where it appears and we are members.
			//
			MessageOccurrence[] occs = mm.getVisibleOccurrences(this.getLoggedInUserId(), 
				primaryOcc.getGlobalId());
			top = occs.length;
			for(int idx = 0; idx < top; ++idx)
			{
				MessageOccurrence each = occs[idx];
				this.markMessageAsRead(each.getConference(), each.getLocalnum());
			}
			
			// Create Envelope and return
			//
			return new Envelope(message, primaryOcc, replyTo, receivers, occ, attr, replies);
		}
		catch(SQLException e)
		{
			throw new UnexpectedException(this.getLoggedInUserId(), e);
		}
	}	
	
	protected long popReply()
	throws ObjectNotFoundException, SQLException
	{
		return this.peekReply() != -1 ? m_replyStack.pop() : -1;
	}
	
	protected long peekReply()
	throws SQLException
	{
		long reply = -1;

		// Do we have anything at all in the stack?
		//
		if(m_replyStack == null)
			return -1;


		// Loop until we have an unread reply
		//
		for(;;)
		{

			// Frame exhausted? Try next!
			//
			if(!m_replyStack.hasMore())
				m_replyStack = m_replyStack.next();
			if(m_replyStack == null)
				return -1;
				
			// Fetch next reply global id adn translate into local occurrence
			//
			reply = m_replyStack.peek();
			try
			{
				MessageOccurrence occ = m_da.getMessageManager().getMostRelevantOccurrence(m_currentConferenceId, reply);
				
				// If it's unread, we're done
				//
				if(m_memberships.isUnread(occ.getConference(), occ.getLocalnum()))
					break;
			}
			catch(ObjectNotFoundException e)
			{
				// Not found. Probably deleted, so just skip it!
			}
				
			// Already read, so pop it from the frame and try again
			//
			m_replyStack.pop();
		}
		return reply;
	}
	
	
	protected void setDataAccess(DataAccess da)
	{
		m_da = da;	
	}
	
	public void checkRights(long mask)
	throws AuthorizationException
	{
		if(!this.getLoggedInUser().hasRights(mask))
			throw new AuthorizationException(); 
	}	
	
	/**
	 * Sends an event to a specified user
	 * 
	 * @param userId
	 * @param e The event
	 */
	protected void sendEvent(long userId, Event e)
	{
		m_sessions.sendEvent(userId, e);
	}
	
	/**
	 * Broadcasts an event to all active users
	 * 
	 * @param e The event
	 */
	protected void broadcastEvent(Event e)
	{
		m_sessions.broadcastEvent(e);
	}
	
	// Implementation of EventTarget
	//
	public void onEvent(Event e)
	{
		// Catch-all method for events without a dedicated methods.
		// Just stuff it in the queue
		//
		this.postEvent(e);
	}
	
	public void onEvent(ChatMessageEvent e)
	{
		// Just post it!
		// 
		this.postEvent(e);
	}
	
	public void onEvent(BroadcastMessageEvent e)
	{
		// Just post it!
		// 
		this.postEvent(e);
	}
	
	public void onEvent(UserAttendanceEvent e)
	{
		// Just post it!
		// 
		this.postEvent(e);
	}		
	
	public synchronized void onEvent(NewMessageEvent e)
	{
		// Already have unread messages? No need to send event! 
		// 
		if(m_lastSuggestedCommand == NEXT_MESSAGE || m_lastSuggestedCommand == NEXT_REPLY)
			return;
			
		// Don't send notification unless we're members.
		//
		long conf = e.getConference();
		try
		{
			m_memberships.get(conf);
			this.postEvent(e);
		}
		catch(ObjectNotFoundException ex)
		{
			// Not member. No need to notify client
			//
			return;
		}
	}
}
