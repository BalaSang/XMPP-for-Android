/**
 * $RCSfile: JingleSession.java,v $
 * $Revision: 1.20 $
 * $Date: 2007/07/18 18:29:21 $
 *
 * Copyright (C) 2002-2006 Jive Software. All rights reserved.
 * ====================================================================
 * The Jive Software License (based on Apache Software License, Version 1.1)
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution,
 *    if any, must include the following acknowledgment:
 *       "This product includes software developed by
 *        Jive Software (http://www.jivesoftware.com)."
 *    Alternately, this acknowledgment may appear in the software itself,
 *    if and wherever such third-party acknowledgments normally appear.
 *
 * 4. The names "Smack" and "Jive Software" must not be used to
 *    endorse or promote products derived from this software without
 *    prior written permission. For written permission, please
 *    contact webmaster@jivesoftware.com.
 *
 * 5. Products derived from this software may not be called "Smack",
 *    nor may "Smack" appear in their name, without prior written
 *    permission of Jive Software.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL JIVE SOFTWARE OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 */

package org.jivesoftware.smackx.jingle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.jivesoftware.smack.Connection;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.PacketExtension;
import org.jivesoftware.smack.packet.XMPPError;
import org.jivesoftware.smackx.jingle.listeners.JingleListener;
import org.jivesoftware.smackx.jingle.listeners.JingleMediaListener;
import org.jivesoftware.smackx.jingle.listeners.JingleSessionListener;
import org.jivesoftware.smackx.jingle.listeners.JingleTransportListener;
import org.jivesoftware.smackx.jingle.media.JingleMediaManager;
import org.jivesoftware.smackx.jingle.media.JingleMediaSession;
import org.jivesoftware.smackx.jingle.media.MediaNegotiator;
import org.jivesoftware.smackx.jingle.media.MediaReceivedListener;
import org.jivesoftware.smackx.jingle.media.PayloadType;
import org.jivesoftware.smackx.jingle.nat.JingleTransportManager;
import org.jivesoftware.smackx.jingle.nat.TransportCandidate;
import org.jivesoftware.smackx.jingle.nat.TransportNegotiator;
import org.jivesoftware.smackx.jingle.nat.TransportResolver;
import org.jivesoftware.smackx.packet.Jingle;
import org.jivesoftware.smackx.packet.JingleError;

/**
 * An abstract Jingle session.
 * <p/>
 * This class contains some basic properties of every Jingle session. However,
 * the concrete implementation can be found in subclasses.
 * 
 * @author Alvaro Saurin
 * @author Jeff Williams
 */
public class JingleSession extends JingleNegotiator implements
		MediaReceivedListener {

	private static final SmackLogger LOGGER = SmackLogger
			.getLogger(JingleSession.class);

	// static
	private static final HashMap sessions = new HashMap();

	private static final Random randomGenerator = new Random();

	// non-static

	/**
	 * A convience method to create an error packet.
	 * 
	 * @param ID
	 *            The packet ID of the
	 * @param to
	 *            To whom the packet is addressed.
	 * @param from
	 *            From whom the packet is sent.
	 * @param errCode
	 *            The error code.
	 * @param errStr
	 *            The error string.
	 * @return The created IQ packet.
	 */
	public static IQ createError(String ID, String to, String from,
			int errCode, XMPPError error) {

		final IQ iqError = createIQ(ID, to, from, IQ.Type.ERROR);
		iqError.setError(error);

		LOGGER.debug("Created Error Packet:" + iqError.toXML());

		return iqError;
	}

	/**
	 * A convience method to create an IQ packet.
	 * 
	 * @param ID
	 *            The packet ID of the
	 * @param to
	 *            To whom the packet is addressed.
	 * @param from
	 *            From whom the packet is sent.
	 * @param type
	 *            The iq type of the packet.
	 * @return The created IQ packet.
	 */
	public static IQ createIQ(String ID, String to, String from, IQ.Type type) {
		final IQ iqPacket = new IQ() {
			@Override
			public String getChildElementXML() {
				return null;
			}
		};

		iqPacket.setPacketID(ID);
		iqPacket.setTo(to);
		iqPacket.setFrom(from);
		iqPacket.setType(type);

		return iqPacket;
	}

	/**
	 * Generate a unique session ID.
	 */
	protected static String generateSessionId() {
		return String.valueOf(Math.abs(randomGenerator.nextLong()));
	}

	/**
	 * Returns the JingleSession related to a particular connection.
	 * 
	 * @param con
	 *            A XMPP connection
	 * @return a Jingle session
	 */
	public static JingleSession getInstanceFor(Connection con) {
		if (con == null) {
			throw new IllegalArgumentException("Connection cannot be null");
		}

		JingleSession result = null;
		synchronized (sessions) {
			if (sessions.containsKey(con)) {
				result = (JingleSession) sessions.get(con);
			}
		}

		return result;
	}

	private String initiator; // Who started the communication

	private String responder; // The other endpoint

	private String sid; // A unique id that identifies this session

	ConnectionListener connectionListener;

	PacketListener packetListener;

	PacketFilter packetFilter;

	protected List<JingleMediaManager> jingleMediaManagers = null;

	private final boolean closed = false;

	private JingleSessionState sessionState;

	private final List<ContentNegotiator> contentNegotiators;

	private JingleSessionRequest sessionRequest;

	private final Connection connection;

	private String sessionInitPacketID;

	private final Map<String, JingleMediaSession> mediaSessionMap;

	/**
	 * JingleSession constructor (for an outgoing Jingle session)
	 * 
	 * @param conn
	 *            Connection
	 * @param initiator
	 *            the initiator JID
	 * @param responder
	 *            the responder JID
	 * @param jingleMediaManager
	 *            the jingleMediaManager
	 */
	public JingleSession(Connection conn, JingleSessionRequest request,
			String initiator, String responder,
			List<JingleMediaManager> jingleMediaManagers) {
		this(conn, initiator, responder, generateSessionId(),
				jingleMediaManagers);
		sessionRequest = request;
	}

	/**
	 * Full featured JingleSession constructor
	 * 
	 * @param conn
	 *            the Connection which is used
	 * @param initiator
	 *            the initiator JID
	 * @param responder
	 *            the responder JID
	 * @param sessionid
	 *            the session ID
	 * @param jingleMediaManager
	 *            the jingleMediaManager
	 */
	public JingleSession(Connection conn, String initiator, String responder,
			String sessionid, List<JingleMediaManager> jingleMediaManagers) {
		super();

		this.initiator = initiator;
		this.responder = responder;
		sid = sessionid;
		this.jingleMediaManagers = jingleMediaManagers;
		setSession(this);
		connection = conn;

		// Initially, we don't known the session state.
		setSessionState(JingleSessionStateUnknown.getInstance());

		contentNegotiators = new ArrayList<ContentNegotiator>();
		mediaSessionMap = new HashMap<String, JingleMediaSession>();

		// Add the session to the list and register the listeneres
		registerInstance();
		installConnectionListeners(conn);
	}

	/**
	 * Add a new content negotiator on behalf of a <content> section received.
	 */
	public void addContentNegotiator(ContentNegotiator inContentNegotiator) {
		contentNegotiators.add(inContentNegotiator);
	}

	/**
	 * The jingle session may have one or more media managers that are trying to
	 * establish media sessions. When the media manager succeeds in creating a
	 * media session is registers it with the session by the media manager's
	 * static name. This routine is where the media manager does the
	 * registering.
	 */
	public void addJingleMediaSession(String mediaManagerName,
			JingleMediaSession mediaSession) {
		mediaSessionMap.put(mediaManagerName, mediaSession);
	}

	/**
	 * Add a listener for jmf negotiation events
	 * 
	 * @param li
	 *            The listener
	 */
	public void addMediaListener(JingleMediaListener li) {
		for (final ContentNegotiator contentNegotiator : contentNegotiators) {
			if (contentNegotiator.getMediaNegotiator() != null) {
				contentNegotiator.getMediaNegotiator().addListener(li);
			}
		}

	}

	/**
	 * Add a listener for transport negotiation events
	 * 
	 * @param li
	 *            The listener
	 */
	public void addTransportListener(JingleTransportListener li) {
		for (final ContentNegotiator contentNegotiator : contentNegotiators) {
			if (contentNegotiator.getTransportNegotiator() != null) {
				contentNegotiator.getTransportNegotiator().addListener(li);
			}
		}
	}

	/**
	 * Terminate negotiations.
	 */
	@Override
	public void close() {
		if (isClosed()) {
			return;
		}

		// Set the session state to ENDED.
		setSessionState(JingleSessionStateEnded.getInstance());

		for (final ContentNegotiator contentNegotiator : contentNegotiators) {

			contentNegotiator.stopJingleMediaSession();

			for (final TransportCandidate candidate : contentNegotiator
					.getTransportNegotiator().getOfferedCandidates()) {
				candidate.removeCandidateEcho();
			}

			contentNegotiator.close();
		}
		removePacketListener();
		removeConnectionListener();
		getConnection().removeConnectionListener(connectionListener);
		LOGGER.debug("Negotiation Closed: " + getConnection().getUser() + " "
				+ sid);
		super.close();

	}

	/**
	 * Acknowledge a IQ packet.
	 * 
	 * @param iq
	 *            The IQ to acknowledge
	 */
	public IQ createAck(IQ iq) {
		IQ result = null;

		if (iq != null) {
			// Don't acknowledge ACKs, errors...
			if (iq.getType().equals(IQ.Type.SET)) {
				final IQ ack = createIQ(iq.getPacketID(), iq.getFrom(),
						iq.getTo(), IQ.Type.RESULT);

				// No! Don't send it. Let it flow to the normal way IQ results
				// get processed and sent.
				// getConnection().sendPacket(ack);
				result = ack;
			}
		}
		return result;
	}

	/**
	 * Complete and send an error. Complete all the null fields in an IQ error
	 * reponse, using the sesssion information we have or some info from the
	 * incoming packet.
	 * 
	 * @param iq
	 *            The Jingle packet we are responing to
	 * @param error
	 *            the IQ packet we want to complete and send
	 */
	public IQ createJingleError(IQ iq, JingleError jingleError) {
		IQ errorPacket = null;
		if (jingleError != null) {
			errorPacket = createIQ(getSid(), iq.getFrom(), iq.getTo(),
					IQ.Type.ERROR);

			final List<PacketExtension> extList = new ArrayList<PacketExtension>();
			extList.add(jingleError);
			final XMPPError error = new XMPPError(0, XMPPError.Type.CANCEL,
					jingleError.toString(), "", extList);

			// Fill in the fields with the info from the Jingle packet
			errorPacket.setPacketID(iq.getPacketID());
			errorPacket.setError(error);
			// errorPacket.addExtension(jingleError);

			// NO! Let the normal state machinery do all of the sending.
			// getConnection().sendPacket(perror);
			LOGGER.error("Error sent: " + errorPacket.toXML());
		}
		return errorPacket;
	}

	/**
	 * Dispatch an incoming packet. The method is responsible for recognizing
	 * the packet type and, depending on the current state, delivering the
	 * packet to the right event handler and wait for a response.
	 * 
	 * @param iq
	 *            the packet received
	 * @return the new Jingle packet to send.
	 * @throws XMPPException
	 */
	@Override
	public List<IQ> dispatchIncomingPacket(IQ iq, String id)
			throws XMPPException {
		final List<IQ> responses = new ArrayList<IQ>();
		IQ response = null;

		if (iq != null) {
			if (iq.getType().equals(IQ.Type.ERROR)) {
				// Process errors
				// TODO getState().eventError(iq);
			} else if (iq.getType().equals(IQ.Type.RESULT)) {
				// Process ACKs
				if (isExpectedId(iq.getPacketID())) {

					// The other side provisionally accepted our
					// session-initiate.
					// Kick off some negotiators.
					if (iq.getPacketID().equals(sessionInitPacketID)) {
						startNegotiators();
					}
					removeExpectedId(iq.getPacketID());
				}
			} else if (iq instanceof Jingle) {
				// It is not an error: it is a Jingle packet...
				final Jingle jin = (Jingle) iq;
				final JingleActionEnum action = jin.getAction();

				// Depending on the state we're in we'll get different
				// processing actions.
				// (See Design Patterns AKA GoF State behavioral pattern.)
				response = getSessionState().processJingle(this, jin, action);
			}
		}

		if (response != null) {
			// Save the packet id, for recognizing ACKs...
			addExpectedId(response.getPacketID());
			responses.add(response);
		}

		return responses;
	}

	@Override
	protected void doStart() {

	}

	// ----------------------------------------------------------------------------------------------------------
	// Receive section
	// ----------------------------------------------------------------------------------------------------------

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}

		final JingleSession other = (JingleSession) obj;

		if (initiator == null) {
			if (other.initiator != null) {
				return false;
			}
		} else if (!initiator.equals(other.initiator)) {
			// Todo check behavior
			// return false;
		}

		if (responder == null) {
			if (other.responder != null) {
				return false;
			}
		} else if (!responder.equals(other.responder)) {
			return false;
		}

		if (sid == null) {
			if (other.sid != null) {
				return false;
			}
		} else if (!sid.equals(other.sid)) {
			return false;
		}

		return true;
	}

	@Override
	public Connection getConnection() {
		return connection;
	}

	/**
	 * Get the session initiator
	 * 
	 * @return the initiator
	 */
	public String getInitiator() {
		return initiator;
	}

	// ----------------------------------------------------------------------------------------------------------
	// Send section
	// ----------------------------------------------------------------------------------------------------------

	/**
	 * Get the Media Manager of this Jingle Session
	 * 
	 * @return
	 */
	public List<JingleMediaManager> getMediaManagers() {
		return jingleMediaManagers;
	}

	/**
	 * The jingle session may have one or more media managers that are trying to
	 * establish media sessions. When the media manager succeeds in creating a
	 * media session is registers it with the session by the media manager's
	 * static name. This routine is where other objects can access the
	 * registered media sessions. NB: If the media manager has not succeeded in
	 * establishing a media session then this could return null.
	 */
	public JingleMediaSession getMediaSession(String mediaManagerName) {
		return mediaSessionMap.get(mediaManagerName);
	}

	/**
	 * Get the session responder
	 * 
	 * @return the responder
	 */
	public String getResponder() {
		return responder;
	}

	public JingleSessionState getSessionState() {
		return sessionState;
	}

	/**
	 * Get the session ID
	 * 
	 * @return the sid
	 */
	public String getSid() {
		return sid;
	}

	/**
	 * Send a content info message.
	 */
	// public synchronized void sendContentInfo(ContentInfo ci) {
	// sendPacket(new Jingle(new JingleContentInfo(ci)));
	// }
	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return Jingle.getSessionHash(getSid(), getInitiator());
	}

	/**
	 * Configure a session, setting some action listeners...
	 * 
	 * @param connection
	 *            The connection to set up
	 */
	private void installConnectionListeners(final Connection connection) {
		if (connection != null) {
			connectionListener = new ConnectionListener() {
				@Override
				public void connectionClosed() {
					unregisterInstanceFor(connection);
				}

				@Override
				public void connectionClosedOnError(java.lang.Exception e) {
					unregisterInstanceFor(connection);
				}

				@Override
				public void reconnectingIn(int i) {
				}

				@Override
				public void reconnectionFailed(Exception exception) {
				}

				@Override
				public void reconnectionSuccessful() {
				}
			};
			connection.addConnectionListener(connectionListener);
		}
	}

	public boolean isClosed() {
		return getSessionState().equals(JingleSessionStateEnded.getInstance());
	}

	/**
	 * Return true if all of the media managers have finished
	 */
	public boolean isFullyEstablished() {
		boolean result = true;
		for (final ContentNegotiator contentNegotiator : contentNegotiators) {
			if (!contentNegotiator.isFullyEstablished()) {
				result = false;
			}
		}
		return result;
	}

	// Instances management

	/**
	 * Called when new Media is received.
	 */
	@Override
	public void mediaReceived(String participant) {
		triggerMediaReceived(participant);
	}

	/**
	 * Process and respond to an incoming packet.
	 * <p/>
	 * This method is called from the packet listener dispatcher when a new
	 * packet has arrived. The method is responsible for recognizing the packet
	 * type and, depending on the current state, delivering it to the right
	 * event handler and wait for a response. The response will be another
	 * Jingle packet that will be sent to the other end point.
	 * 
	 * @param iq
	 *            the packet received
	 * @return the new Jingle packet to send.
	 * @throws XMPPException
	 */
	public synchronized void receivePacketAndRespond(IQ iq)
			throws XMPPException {
		final List<IQ> responses = new ArrayList<IQ>();

		String responseId = null;

		LOGGER.debug("Packet: " + iq.toXML());

		try {

			// Dispatch the packet to the JingleNegotiators and get back a list
			// of the results.
			responses.addAll(dispatchIncomingPacket(iq, null));

			if (iq != null) {
				responseId = iq.getPacketID();

				// Send the IQ to each of the content negotiators for further
				// processing.
				// Each content negotiator may pass back a list of JingleContent
				// for addition to the response packet.

				for (final ContentNegotiator contentNegotiator : contentNegotiators) {
					// If at this point the content negotiator isn't started,
					// it's because we sent a session-init jingle
					// packet from startOutgoing() and we're waiting for the
					// other side to let us know they're ready
					// to take jingle packets. (This packet might be a
					// session-terminate, but that will get handled
					// later.
					if (!contentNegotiator.isStarted()) {
						contentNegotiator.start();
					}
					responses.addAll(contentNegotiator.dispatchIncomingPacket(
							iq, responseId));
				}

			}
			// Acknowledge the IQ reception
			// Not anymore. The state machine generates an appropriate response
			// IQ that
			// gets sent back at the end of this routine.
			// sendAck(iq);

		} catch (final JingleException e) {
			// Send an error message, if present
			final JingleError error = e.getError();
			if (error != null) {
				responses.add(createJingleError(iq, error));
			}

			// Notify the session end and close everything...
			triggerSessionClosedOnError(e);
		}

		// // If the response is anything other than a RESULT then send it now.
		// if ((response != null) &&
		// (!response.getType().equals(IQ.Type.RESULT))) {
		// getConnection().sendPacket(response);
		// }

		// Loop through all of the responses and send them.
		for (final IQ response : responses) {
			sendPacket(response);
		}
	}

	/**
	 * Register this instance.
	 */
	private void registerInstance() {
		synchronized (sessions) {
			sessions.put(getConnection(), this);
		}
	}

	private void removeConnectionListener() {
		if (connectionListener != null) {
			getConnection().removeConnectionListener(connectionListener);

			LOGGER.debug("JINGLE SESSION: REMOVE CONNECTION LISTENER");
		}
	}

	/**
	 * Remove a listener for jmf negotiation events
	 * 
	 * @param li
	 *            The listener
	 */
	public void removeMediaListener(JingleMediaListener li) {
		for (final ContentNegotiator contentNegotiator : contentNegotiators) {
			if (contentNegotiator.getMediaNegotiator() != null) {
				contentNegotiator.getMediaNegotiator().removeListener(li);
			}
		}
	}

	/**
	 * Remove the packet listener used for processing packet.
	 */
	protected void removePacketListener() {
		if (packetListener != null) {
			getConnection().removePacketListener(packetListener);

			LOGGER.debug("JINGLE SESSION: REMOVE PACKET LISTENER");
		}
	}

	/**
	 * Remove a listener for transport negotiation events
	 * 
	 * @param li
	 *            The listener
	 */
	public void removeTransportListener(JingleTransportListener li) {
		for (final ContentNegotiator contentNegotiator : contentNegotiators) {
			if (contentNegotiator.getTransportNegotiator() != null) {
				contentNegotiator.getTransportNegotiator().removeListener(li);
			}
		}
	}

	// Listeners

	/**
	 * @param inJingle
	 * @param inAction
	 */
	private void sendActiveStateAction(Jingle inJingle,
			JingleActionEnum inAction) {

	}

	/**
	 * @param inJingle
	 * @param inAction
	 */
	private void sendEndedStateAction(Jingle inJingle, JingleActionEnum inAction) {

	}

	/**
	 * Complete and send a packet. Complete all the null fields in a Jingle
	 * reponse, using the session information we have or some info from the
	 * incoming packet.
	 * 
	 * @param iq
	 *            The Jingle packet we are responing to
	 * @param jout
	 *            the Jingle packet we want to complete and send
	 */
	public Jingle sendFormattedJingle(IQ iq, Jingle jout) {
		if (jout != null) {
			if (jout.getInitiator() == null) {
				jout.setInitiator(getInitiator());
			}

			if (jout.getResponder() == null) {
				jout.setResponder(getResponder());
			}

			if (jout.getSid() == null) {
				jout.setSid(getSid());
			}

			final String me = getConnection().getUser();
			final String other = getResponder().equals(me) ? getInitiator()
					: getResponder();

			if (jout.getTo() == null) {
				if (iq != null) {
					jout.setTo(iq.getFrom());
				} else {
					jout.setTo(other);
				}
			}

			if (jout.getFrom() == null) {
				if (iq != null) {
					jout.setFrom(iq.getTo());
				} else {
					jout.setFrom(me);
				}
			}

			// The the packet.
			if ((getConnection() != null) && (getConnection().isConnected())) {
				getConnection().sendPacket(jout);
			}
		}
		return jout;
	}

	/**
	 * Complete and send a packet. Complete all the null fields in a Jingle
	 * reponse, using the session information we have.
	 * 
	 * @param jout
	 *            the Jingle packet we want to complete and send
	 */
	public Jingle sendFormattedJingle(Jingle jout) {
		return sendFormattedJingle(null, jout);
	}

	public void sendPacket(IQ iq) {

		if (iq instanceof Jingle) {

			sendFormattedJingle((Jingle) iq);

		} else {

			getConnection().sendPacket(iq);
		}
	}

	// Triggers

	/**
	 * @param inJingle
	 * @param inAction
	 */
	// private void sendUnknownStateAction(Jingle inJingle, JingleActionEnum
	// inAction) {
	//
	// if (inAction == JingleActionEnum.SESSION_INITIATE) {
	// // Prepare to receive and act on response packets.
	// updatePacketListener();
	//
	// // Send the actual packet.
	// sendPacket(inJingle);
	//
	// // Change to the PENDING state.
	// setSessionState(JingleSessionStateEnum.PENDING);
	// } else {
	// throw new
	// IllegalStateException("Only session-initiate allowed in the UNKNOWN state.");
	// }
	// }
	/**
	 * @param inJingle
	 * @param inAction
	 */
	private void sendPendingStateAction(Jingle inJingle,
			JingleActionEnum inAction) {

	}

	/**
	 * Set the session initiator
	 * 
	 * @param initiator
	 *            the initiator to set
	 */
	public void setInitiator(String initiator) {
		this.initiator = initiator;
	}

	/**
	 * Set the Media Manager of this Jingle Session
	 * 
	 * @param jingleMediaManager
	 */
	public void setMediaManagers(List<JingleMediaManager> jingleMediaManagers) {
		this.jingleMediaManagers = jingleMediaManagers;
	}

	/**
	 * Set the session responder.
	 * 
	 * @param responder
	 *            the receptor to set
	 */
	public void setResponder(String responder) {
		this.responder = responder;
	}

	/**
	 * Validate the state changes.
	 */

	public void setSessionState(JingleSessionState stateIs) {

		LOGGER.debug("Session state change: " + sessionState + "->" + stateIs);
		stateIs.enter();
		sessionState = stateIs;
	}

	/**
	 * Set the session ID
	 * 
	 * @param sessionId
	 *            the sid to set
	 */
	protected void setSid(String sessionId) {
		sid = sessionId;
	}

	/**
	 * Setup the listeners that act on events coming from the lower level
	 * negotiators.
	 */

	public void setupListeners() {

		final JingleMediaListener jingleMediaListener = new JingleMediaListener() {
			@Override
			public void mediaClosed(PayloadType cand) {
			}

			@Override
			public void mediaEstablished(PayloadType pt) {
				if (isFullyEstablished()) {
					final Jingle jout = new Jingle(
							JingleActionEnum.SESSION_ACCEPT);

					// Build up a response packet from each media manager.
					for (final ContentNegotiator contentNegotiator : contentNegotiators) {
						if (contentNegotiator.getNegotiatorState() == JingleNegotiatorState.SUCCEEDED) {
							jout.addContent(contentNegotiator
									.getJingleContent());
						}
					}
					// Send the "accept" and wait for the ACK
					addExpectedId(jout.getPacketID());
					sendPacket(jout);

					// triggerSessionEstablished();

				}
			}
		};

		final JingleTransportListener jingleTransportListener = new JingleTransportListener() {

			@Override
			public void transportClosed(TransportCandidate cand) {
			}

			@Override
			public void transportClosedOnError(XMPPException e) {
			}

			@Override
			public void transportEstablished(TransportCandidate local,
					TransportCandidate remote) {
				if (isFullyEstablished()) {

					// Indicate that this session is active.
					setSessionState(JingleSessionStateActive.getInstance());

					for (final ContentNegotiator contentNegotiator : contentNegotiators) {
						if (contentNegotiator.getNegotiatorState() == JingleNegotiatorState.SUCCEEDED) {
							contentNegotiator.triggerContentEstablished();
						}
					}

					if (getSessionState().equals(
							JingleSessionStatePending.getInstance())) {

						final Jingle jout = new Jingle(
								JingleActionEnum.SESSION_ACCEPT);

						// Build up a response packet from each media manager.
						for (final ContentNegotiator contentNegotiator : contentNegotiators) {
							if (contentNegotiator.getNegotiatorState() == JingleNegotiatorState.SUCCEEDED) {
								jout.addContent(contentNegotiator
										.getJingleContent());
							}
						}
						// Send the "accept" and wait for the ACK
						addExpectedId(jout.getPacketID());
						sendPacket(jout);
					}
				}
			}
		};

		addMediaListener(jingleMediaListener);
		addTransportListener(jingleTransportListener);
	}

	// Packet and error creation

	/**
	 * This is the starting point for responding to a new session.
	 */
	public void startIncoming() {

		// updatePacketListener();
	}

	/**
	 * When we initiate a session we need to start a bunch of negotiators right
	 * after we receive the result packet for our session-initiate. This is
	 * where we start them.
	 * 
	 */
	private void startNegotiators() {

		for (final ContentNegotiator contentNegotiator : contentNegotiators) {
			final TransportNegotiator transNeg = contentNegotiator
					.getTransportNegotiator();
			transNeg.start();
		}
	}

	/**
	 * This is the starting point for intitiating a new session.
	 * 
	 * @throws IllegalStateException
	 */
	public void startOutgoing() throws IllegalStateException {

		updatePacketListener();
		setSessionState(JingleSessionStatePending.getInstance());

		final Jingle jingle = new Jingle(JingleActionEnum.SESSION_INITIATE);

		// Create a content negotiator for each media manager on the session.
		for (final JingleMediaManager mediaManager : getMediaManagers()) {
			final ContentNegotiator contentNeg = new ContentNegotiator(this,
					ContentNegotiator.INITIATOR, mediaManager.getName());

			// Create the media negotiator for this content description.
			contentNeg.setMediaNegotiator(new MediaNegotiator(this,
					mediaManager, mediaManager.getPayloads(), contentNeg));

			final JingleTransportManager transportManager = mediaManager
					.getTransportManager();
			TransportResolver resolver = null;
			try {
				resolver = transportManager.getResolver(this);
			} catch (final XMPPException e) {
				e.printStackTrace();
			}

			if (resolver.getType().equals(TransportResolver.Type.rawupd)) {
				contentNeg
						.setTransportNegotiator(new TransportNegotiator.RawUdp(
								this, resolver, contentNeg));
			}
			if (resolver.getType().equals(TransportResolver.Type.ice)) {
				contentNeg.setTransportNegotiator(new TransportNegotiator.Ice(
						this, resolver, contentNeg));
			}

			addContentNegotiator(contentNeg);
		}

		// Give each of the content negotiators a chance to return a portion of
		// the structure to make the Jingle packet.
		for (final ContentNegotiator contentNegotiator : contentNegotiators) {
			jingle.addContent(contentNegotiator.getJingleContent());
		}

		// Save the session-initiate packet ID, so that we can respond to it.
		sessionInitPacketID = jingle.getPacketID();

		sendPacket(jingle);

		// Now setup to track the media negotiators, so that we know when (if)
		// to send a session-accept.
		setupListeners();

		// Give each of the content negotiators a chance to start
		// and return a portion of the structure to make the Jingle packet.

		// Don't do this anymore. The problem is that the other side might not
		// be ready.
		// Later when we receive our first jingle packet from the other side
		// we'll fire-up the negotiators
		// before processing it. (See receivePacketAndRespond() above.
		// for (ContentNegotiator contentNegotiator : contentNegotiators) {
		// contentNegotiator.start();
		// }
	}

	/**
	 * Trigger a session redirect event.
	 */
	// protected void triggerSessionRedirect(String arg) {
	// List<JingleListener> listeners = getListenersList();
	// for (JingleListener li : listeners) {
	// if (li instanceof JingleSessionListener) {
	// JingleSessionListener sli = (JingleSessionListener) li;
	// sli.sessionRedirected(arg, this);
	// }
	// }
	// }
	/**
	 * Trigger a session decline event.
	 */
	// protected void triggerSessionDeclined(String reason) {
	// List<JingleListener> listeners = getListenersList();
	// for (JingleListener li : listeners) {
	// if (li instanceof JingleSessionListener) {
	// JingleSessionListener sli = (JingleSessionListener) li;
	// sli.sessionDeclined(reason, this);
	// }
	// }
	// for (ContentNegotiator contentNegotiator : contentNegotiators) {
	// for (TransportCandidate candidate :
	// contentNegotiator.getTransportNegotiator().getOfferedCandidates())
	// candidate.removeCandidateEcho();
	// }
	// }
	/**
	 * Terminates the session with default reason.
	 * 
	 * @throws XMPPException
	 */
	public void terminate() throws XMPPException {
		terminate("Closed Locally");
	}

	/**
	 * Terminates the session with a custom reason.
	 * 
	 * @throws XMPPException
	 */
	public void terminate(String reason) throws XMPPException {
		if (isClosed()) {
			return;
		}
		LOGGER.debug("Terminate " + reason);
		final Jingle jout = new Jingle(JingleActionEnum.SESSION_TERMINATE);
		jout.setType(IQ.Type.SET);
		sendPacket(jout);
		triggerSessionClosed(reason);
	}

	/**
	 * Trigger a session established event.
	 */
	// protected void triggerSessionEstablished() {
	// List<JingleListener> listeners = getListenersList();
	// for (JingleListener li : listeners) {
	// if (li instanceof JingleSessionListener) {
	// JingleSessionListener sli = (JingleSessionListener) li;
	// sli.sessionEstablished(this);
	// }
	// }
	// }
	/**
	 * Trigger a media received event.
	 */
	protected void triggerMediaReceived(String participant) {
		final List<JingleListener> listeners = getListenersList();
		for (final JingleListener li : listeners) {
			if (li instanceof JingleSessionListener) {
				final JingleSessionListener sli = (JingleSessionListener) li;
				sli.sessionMediaReceived(this, participant);
			}
		}
	}

	/**
	 * Trigger a session closed event.
	 */
	protected void triggerSessionClosed(String reason) {
		// for (ContentNegotiator contentNegotiator : contentNegotiators) {
		//
		// contentNegotiator.stopJingleMediaSession();
		//
		// for (TransportCandidate candidate :
		// contentNegotiator.getTransportNegotiator().getOfferedCandidates())
		// candidate.removeCandidateEcho();
		// }

		final List<JingleListener> listeners = getListenersList();
		for (final JingleListener li : listeners) {
			if (li instanceof JingleSessionListener) {
				final JingleSessionListener sli = (JingleSessionListener) li;
				sli.sessionClosed(reason, this);
			}
		}
		close();
	}

	/**
	 * Trigger a session closed event due to an error.
	 */
	protected void triggerSessionClosedOnError(XMPPException exc) {
		for (final ContentNegotiator contentNegotiator : contentNegotiators) {

			contentNegotiator.stopJingleMediaSession();

			for (final TransportCandidate candidate : contentNegotiator
					.getTransportNegotiator().getOfferedCandidates()) {
				candidate.removeCandidateEcho();
			}
		}
		final List<JingleListener> listeners = getListenersList();
		for (final JingleListener li : listeners) {
			if (li instanceof JingleSessionListener) {
				final JingleSessionListener sli = (JingleSessionListener) li;
				sli.sessionClosedOnError(exc, this);
			}
		}
		close();
	}

	/**
	 * Clean a session from the list.
	 * 
	 * @param connection
	 *            The connection to clean up
	 */
	private void unregisterInstanceFor(Connection connection) {
		synchronized (sessions) {
			sessions.remove(connection);
		}
	}

	/**
	 * Install the packet listener. The listener is responsible for responding
	 * to any packet that we receive...
	 */
	protected void updatePacketListener() {
		removePacketListener();

		LOGGER.debug("UpdatePacketListener");

		packetListener = new PacketListener() {
			@Override
			public void processPacket(Packet packet) {
				try {
					receivePacketAndRespond((IQ) packet);
				} catch (final XMPPException e) {
					e.printStackTrace();
				}
			}
		};

		packetFilter = new PacketFilter() {
			@Override
			public boolean accept(Packet packet) {

				if (packet instanceof IQ) {
					final IQ iq = (IQ) packet;

					final String me = getConnection().getUser();

					if (!iq.getTo().equals(me)) {
						return false;
					}

					final String other = getResponder().equals(me) ? getInitiator()
							: getResponder();

					if (iq.getFrom() == null
							|| !iq.getFrom().equals(other == null ? "" : other)) {
						return false;
					}

					if (iq instanceof Jingle) {
						final Jingle jin = (Jingle) iq;

						final String sid = jin.getSid();
						if (sid == null || !sid.equals(getSid())) {
							LOGGER.debug("Ignored Jingle(SID) " + sid + "|"
									+ getSid() + " :" + iq.toXML());
							return false;
						}
						final String ini = jin.getInitiator();
						if (!ini.equals(getInitiator())) {
							LOGGER.debug("Ignored Jingle(INI): " + iq.toXML());
							return false;
						}
					} else {
						// We accept some non-Jingle IQ packets: ERRORs and ACKs
						if (iq.getType().equals(IQ.Type.SET)) {
							LOGGER.debug("Ignored Jingle(TYPE): " + iq.toXML());
							return false;
						} else if (iq.getType().equals(IQ.Type.GET)) {
							LOGGER.debug("Ignored Jingle(TYPE): " + iq.toXML());
							return false;
						}
					}
					return true;
				}
				return false;
			}
		};

		getConnection().addPacketListener(packetListener, packetFilter);
	}
}
