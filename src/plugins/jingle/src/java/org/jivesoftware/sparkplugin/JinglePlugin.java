/**
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Lesser Public License (LGPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.sparkplugin;

import org.jivesoftware.Spark;
import org.jivesoftware.resource.SparkRes;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smackx.ServiceDiscoveryManager;
import org.jivesoftware.smackx.jingle.IncomingJingleSession;
import org.jivesoftware.smackx.jingle.JingleManager;
import org.jivesoftware.smackx.jingle.JingleSession;
import org.jivesoftware.smackx.jingle.JingleSessionRequest;
import org.jivesoftware.smackx.jingle.OutgoingJingleSession;
import org.jivesoftware.smackx.jingle.listeners.JingleSessionListener;
import org.jivesoftware.smackx.jingle.listeners.JingleSessionRequestListener;
import org.jivesoftware.smackx.jingle.media.PayloadType;
import org.jivesoftware.smackx.jingle.mediaimpl.jmf.JmfMediaManager;
import org.jivesoftware.smackx.jingle.mediaimpl.jspeex.SpeexMediaManager;
import org.jivesoftware.smackx.jingle.mediaimpl.multi.MultiMediaManager;
import org.jivesoftware.smackx.jingle.nat.BridgedTransportManager;
import org.jivesoftware.smackx.jingle.nat.ICETransportManager;
import org.jivesoftware.smackx.jingle.nat.JingleTransportManager;
import org.jivesoftware.smackx.jingle.nat.STUN;
import org.jivesoftware.smackx.jingle.nat.TransportCandidate;
import org.jivesoftware.smackx.packet.DiscoverInfo;
import org.jivesoftware.spark.PresenceManager;
import org.jivesoftware.spark.SparkManager;
import org.jivesoftware.spark.phone.Phone;
import org.jivesoftware.spark.phone.PhoneManager;
import org.jivesoftware.spark.plugin.Plugin;
import org.jivesoftware.spark.ui.ChatRoom;
import org.jivesoftware.spark.ui.ChatRoomListenerAdapter;
import org.jivesoftware.spark.ui.ChatRoomNotFoundException;
import org.jivesoftware.spark.ui.TranscriptWindow;
import org.jivesoftware.spark.ui.rooms.ChatRoomImpl;
import org.jivesoftware.spark.util.SwingWorker;
import org.jivesoftware.spark.util.log.Log;
import org.jivesoftware.sparkplugin.JingleStateManager.JingleRoomState;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * A simple Jingle Plugin for Spark that uses server Media Proxy for the transport and NAT Traversal
 */
public class JinglePlugin implements Plugin, JingleSessionListener, Phone {

    private final Map<String, JingleSession> sessions = new HashMap<String, JingleSession>();

    private JingleManager jingleManager;

    private JingleStateManager stateManager;

    private static final String JINGLE_NAMESPACE = "http://www.xmpp.org/extensions/xep-0166.html#ns";

    private Map<String, Boolean> jingleFeature = new HashMap<String, Boolean>();

    public void initialize() {
        // Add to PhoneManager
        PhoneManager.getInstance().addPhone(this);

        // Initialize state manager.
        stateManager = JingleStateManager.getInstance();

        // Adds a tab handler.
        SparkManager.getChatManager().addSparkTabHandler(new JingleTabHandler());

        final SwingWorker jingleLoadingThread = new SwingWorker() {
            public Object construct() {

                String stunServer = "stun.xten.net";
                int stunPort = 3478;

                if (STUN.serviceAvailable(SparkManager.getConnection())) {
                    STUN stun = STUN.getSTUNServer(SparkManager.getConnection());
                    if (stun != null) {
                        stunServer = stun.getHost();
                        stunPort = stun.getPort();
                    }
                }

                JingleTransportManager transportManager = new ICETransportManager(SparkManager.getConnection(), stunServer, stunPort);

                // if running on Windows use Direct Sound for better performance
                String locator = Spark.isWindows() ? "dsound://" : "javasound://";

                MultiMediaManager jingleMediaManager = new MultiMediaManager();
                jingleMediaManager.addMediaManager(new JmfMediaManager(locator));
                jingleMediaManager.addMediaManager(new SpeexMediaManager());

                if (System.getProperty("codec") != null) {
                    try {
                        int codec = Integer.parseInt(System.getProperty("codec"));
                        jingleMediaManager.setPreferredPayloadType(jingleMediaManager.getPayloads().get(codec));
                    }
                    catch (NumberFormatException e) {
                        // Do Nothing
                    }
                }

                jingleManager = new JingleManager(SparkManager.getConnection(), transportManager, jingleMediaManager);

                if (transportManager instanceof BridgedTransportManager) {
                    jingleManager.addCreationListener((BridgedTransportManager)transportManager);
                }

                // Add Jingle to discovered items list.
                SparkManager.addFeature(JINGLE_NAMESPACE);

                return true;
            }

            public void finished() {
                addListeners();
            }
        };

        jingleLoadingThread.start();
    }


    /**
     * Adds Jingle and ChatRoom listeners.
     */
    private void addListeners() {
        if (jingleManager == null) {
            Log.error("Unable to resolve Jingle Connection");
            return;
        }

        // Listen in for new incoming Jingle requests.
        jingleManager.addJingleSessionRequestListener(new JingleSessionRequestListener() {
            public void sessionRequested(final JingleSessionRequest request) {
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        incomingJingleSession(request);
                    }
                });
            }
        });

        // If a ChatRoom containing a JingleSession is closed, end the jingle session.
        SparkManager.getChatManager().addChatRoomListener(new ChatRoomListenerAdapter() {
            public void chatRoomClosed(ChatRoom room) {
                if (room instanceof ChatRoomImpl) {
                    final ChatRoomImpl roomImpl = (ChatRoomImpl)room;
                    if (sessions.containsKey(roomImpl.getJID())) {
                        endCall(roomImpl);
                    }
                }
            }
        });
    }


    public Collection<Action> getPhoneActions(final String jid) {
        if (jingleManager == null) {
            return Collections.emptyList();
        }

        Boolean supportsJingle = true;// jingleFeature.get(StringUtils.parseBareAddress(jid));
        if (supportsJingle == null) {
            // Disco for event.
            // Obtain the ServiceDiscoveryManager associated with my XMPPConnection
            ServiceDiscoveryManager discoManager = ServiceDiscoveryManager.getInstanceFor(SparkManager.getConnection());

            String fullJID = PresenceManager.getFullyQualifiedJID(jid);

            // Get the items of a given XMPP entity
            // This example gets the items associated with online catalog service
            DiscoverInfo discoverInfo = null;
            try {
                discoverInfo = discoManager.discoverInfo(fullJID);
            }
            catch (XMPPException e) {
                Log.error(e);
            }

            // Get the discovered items of the queried XMPP entity
            supportsJingle = discoverInfo.containsFeature(JINGLE_NAMESPACE);
            jingleFeature.put(jid, supportsJingle);
        }

        if (!supportsJingle) {
            return Collections.emptyList();
        }

        final List<Action> actions = new ArrayList<Action>();
        Action action = new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                placeCall(jid);
            }
        };

        action.putValue(Action.NAME, "<html><b>Computer To Computer</b></html>");
        action.putValue(Action.SMALL_ICON, SparkRes.getImageIcon(SparkRes.COMPUTER_IMAGE_16x16));
        actions.add(action);
        return actions;
    }

    public void endCall(ChatRoomImpl room) {
        JingleSession session = sessions.get(room.getJID());
        if (session == null) return;
        try {
            session.terminate();
        }
        catch (XMPPException e) {
            Log.error(e);
        }
        sessions.remove(room.getJID());

        // Update state
        stateManager.removeJingleSession(room);

        // Notify state changed.
        SparkManager.getChatManager().notifySparkTabHandlers(room);
    }

    public void placeCall(String jid) {
        jid = SparkManager.getUserManager().getFullJID(jid);

        if (sessions.containsKey(jid)) {
            return;
        }

        ChatRoom room = SparkManager.getChatManager().getChatRoom(StringUtils.parseBareAddress(jid));
        SparkManager.getChatManager().getChatContainer().activateChatRoom(room);

        // Create a new Jingle Call with a full JID
        OutgoingJingleSession session = null;
        try {
            session = jingleManager.createOutgoingJingleSession(jid);
        }
        catch (XMPPException e) {
            Log.error(e);
        }

        // Start the call
        if (session != null) {
            session.addListener(this);
            session.start();
            sessions.put(jid, session);
        }


        TranscriptWindow transcriptWindow = room.getTranscriptWindow();
        StyledDocument doc = (StyledDocument)transcriptWindow.getDocument();
        Style style = doc.addStyle("StyleName", null);

        OutgoingCall outgoingCall = new OutgoingCall();
        outgoingCall.handleOutgoingCall(session, room, jid);
        StyleConstants.setComponent(style, outgoingCall);

        // Insert the image at the end of the text
        try {
            doc.insertString(doc.getLength(), "ignored text", style);
            doc.insertString(doc.getLength(), "\n", null);
        }
        catch (BadLocationException e) {
            Log.error(e);
        }

        // Update state
        stateManager.addJingleSession(room, JingleRoomState.ringing);

        // Notify state change
        SparkManager.getChatManager().notifySparkTabHandlers(room);

        room.scrollToBottom();
    }

    public void shutdown() {
    }

    public boolean canShutDown() {
        return false;
    }

    public void uninstall() {
    }

    public void sessionEstablished(PayloadType payloadType, TransportCandidate transportCandidate, TransportCandidate transportCandidate1, JingleSession jingleSession) {

    }

    public void sessionDeclined(String string, JingleSession jingleSession) {
        removeJingleSession(jingleSession);
    }

    public void sessionRedirected(String string, JingleSession jingleSession) {
        System.out.println(string);
    }

    public void sessionClosed(String string, JingleSession jingleSession) {
        removeJingleSession(jingleSession);
    }

    public void sessionClosedOnError(XMPPException xmppException, JingleSession jingleSession) {
        removeJingleSession(jingleSession);
    }

    private void removeJingleSession(JingleSession jingleSession) {
        try {
            if (sessions.containsValue(jingleSession)) {
                String found = null;
                for (String key : sessions.keySet()) {
                    System.err.println("D:" + key);
                    if (jingleSession.equals(sessions.get(key))) {
                        found = key;
                    }
                }
                System.err.println("REMOVED:" + found);
                if (found != null)
                    sessions.remove(found);
            }

        }
        catch (Exception e) {
            // Do Nothing
        }

        // Remove from room
        String jid = jingleSession.getResponder();
        ChatRoom chatRoom = null;
        try {
            chatRoom = SparkManager.getChatManager().getChatContainer().getChatRoom(StringUtils.parseBareAddress(jid));
            // Update state
            stateManager.removeJingleSession(chatRoom);

            // Notify state changed.
            SparkManager.getChatManager().notifySparkTabHandlers(chatRoom);
        }
        catch (ChatRoomNotFoundException e) {
            // Ignore
        }
    }

    /**
     * Notify user that a new incoming jingle request has been receieved.
     *
     * @param request the <code>JingleSessionRequest</code>.
     */
    private void incomingJingleSession(JingleSessionRequest request) {
        final IncomingCall incomingCall = new IncomingCall(request);
    }


}
