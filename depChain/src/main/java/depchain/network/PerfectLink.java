package depchain.network;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.EOFException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.crypto.SecretKey;

import depchain.utils.ByteArrayWrapper;
import java.util.List;
import depchain.utils.Config;
import depchain.utils.CryptoUtil;
import depchain.utils.Logger;
import depchain.utils.Logger.LogLevel;

public class PerfectLink {
    public static final int MAX_WORKERS = 50;
    private final int myId;
    private final DatagramSocket socket;
    private final ConcurrentMap<Integer, InetSocketAddress> processAddresses;
    private final PrivateKey myPrivateKey;
    private final ConcurrentMap<Integer, PublicKey> publicKeys;

    private final ConcurrentMap<Integer, Session> sessions = new ConcurrentHashMap<>();

    // Map to track session status with process IDs
    private final ConcurrentMap<Integer, Boolean> activeSessionMap = new ConcurrentHashMap<>();

    // Global map for session initiation resends
    private final ConcurrentMap<Integer, ScheduledFuture<?>> resendTasks = new ConcurrentHashMap<>();

    // Map to store resend tasks
    private final ConcurrentMap<Integer, ConcurrentMap<Integer, ScheduledFuture<?>>> sessionResendTasks = new ConcurrentHashMap<>();

    private final Object lock = new Object();

    private final ExecutorService listenerWorkerPool;
    private final ScheduledExecutorService senderWorkerPool;
    private final BlockingQueue<Message> deliveredQueue = new LinkedBlockingQueue<>();

    public PerfectLink(int myId, int port, ConcurrentMap<Integer, InetSocketAddress> processAddresses,
            PrivateKey myPrivateKey, ConcurrentMap<Integer, PublicKey> publicKeys) throws Exception {
        this.myId = myId;
        this.socket = new DatagramSocket(port);
        this.processAddresses = processAddresses;
        this.myPrivateKey = myPrivateKey;
        this.publicKeys = publicKeys;
        this.listenerWorkerPool = Executors.newFixedThreadPool(MAX_WORKERS);
        this.senderWorkerPool = Executors.newScheduledThreadPool(MAX_WORKERS);

        // Start listener
        new Thread(this::startListener, "PerfectLink-Listener").start();

        // Clients only connect to Leader (which is 1)
        if (myId >= 5) {
            Logger.log(LogLevel.INFO, "Process 5 starting session with process 1");
            startSession(1);

            // wait session with leader to be established
            // TOD CHANGE LEADER 1
            while (!activeSessionMap.getOrDefault(1, false)) {
                Logger.log(LogLevel.INFO, "Waiting for session with process 1 to be established...");
                Thread.sleep(500);
            }
        }

        else {
            // Server initiate sessions with other server of lower ID
            for (int i = 1; i < myId; i++) {
                Logger.log(LogLevel.INFO, "Process " + myId + " starting session with process " + i);
                startSession(i);
            }

            // wait for all sessions to be established
            // LEADER CHANGE THIS TODO
            if (myId == 1) {
                while (activeSessionMap.size() < processAddresses.size()) {
                    Logger.log(LogLevel.INFO, "Waiting for all sessions to be established...");
                    Thread.sleep(500);
                }
            } else {
                while (activeSessionMap.size() < processAddresses.size() - 1) {
                    Logger.log(LogLevel.INFO, "Waiting for all sessions to be established...");
                    Thread.sleep(500);
                }
            }
        }
    }

    public boolean hasActiveSession(int processId) {
        return activeSessionMap.getOrDefault(processId, false);
    }

    public void startSession(int destId) {
        InetSocketAddress address = processAddresses.getOrDefault(destId, Config.clientAddresses.get(destId));
        if (address == null) {
            Logger.log(LogLevel.ERROR, "Unknown destination: " + destId);
            return;
        }

        if (activeSessionMap.getOrDefault(destId, false)) {
            Logger.log(LogLevel.INFO, "Session with process " + destId + " is already active.");
            return;
        }

        int nonce = CryptoUtil.generateNonce();
        /* Message msg = new Message(Message.Type.START_SESSION, -1, null, myId, null, nonce); */
        Message msg = new Message.MessageBuilder(Message.Type.START_SESSION, -1, null, myId).setNonce(nonce)
                .build();

        try {
            activeSessionMap.put(destId, false); // Mark session as initiating
            send(destId, msg);
            scheduleResend(destId, msg);
        } catch (Exception e) {
            Logger.log(LogLevel.ERROR, "Failed to start session with process " + destId + ": " + e.toString());
            activeSessionMap.remove(destId);
        }
    }

    private void startListener() {
        while (!socket.isClosed()) {
            try {
                // Create a new buffer for each packet
                byte[] buffer = new byte[8192];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                socket.receive(packet);

                // Create a copy of the packet data before submitting to worker pool
                final byte[] packetData = new byte[packet.getLength()];
                System.arraycopy(packet.getData(), packet.getOffset(), packetData, 0, packet.getLength());
                final InetSocketAddress senderAddress = (InetSocketAddress) packet.getSocketAddress();

                listenerWorkerPool.submit(() -> {
                    DatagramPacket workerPacket = new DatagramPacket(packetData, packetData.length, senderAddress);
                    processMessage(workerPacket);
                });
            } catch (SocketException e) {
                if (socket.isClosed())
                    break;
                Logger.log(LogLevel.ERROR, "Socket exception: " + e.toString());
            } catch (Exception e) {
                Logger.log(LogLevel.ERROR, "Exception: " + e.toString());
            }
        }
    }

    private void processMessage(DatagramPacket packet) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(packet.getData(), packet.getOffset(),
                packet.getLength());
                ObjectInputStream ois = new ObjectInputStream(bis)) {

            // TODO: Deal with EOFException
            Message msg = (Message) ois.readObject();

            Logger.log(LogLevel.DEBUG,
                    "Received message of type " + msg.getType() + " from " + msg.getSenderId() + " with nonce " + msg.getNonce());

            PublicKey senderKey = publicKeys.get(msg.getSenderId());
            Session session = sessions.get(msg.getSenderId());
            
            System.out.println("RECEIVED MESSAGE FROM " + msg.getSenderId() + " OF TYPE " + msg.getType() + " WITH NONCE " + msg.getNonce());
            // In a real implementation, you would verify the signature here
            if (senderKey != null) {
                switch (msg.getType()) {

                    case ACK:
                        // Regular ACK for another message type
                        if (session != null && session.getSentCounter() == msg.getNonce()) {
                            session.incrementSentCounter();

                            // Cancel the resend task
                            ScheduledFuture<?> task = sessionResendTasks
                                    .getOrDefault(msg.getSenderId(), new ConcurrentHashMap<>()).remove(msg.getNonce());

                            if (task != null) {
                                task.cancel(false);
                            }
                        }
                        break;

                    case ACK_SESSION:
                        if (activeSessionMap.containsKey(msg.getSenderId()) && !activeSessionMap.get(msg.getSenderId())) {

                            System.out.println("ACK_SESSION received from " + msg.getSenderId());
                            System.out.println("Session key: " + msg.getSessionKey());
                            // decrypt session key with private key
                            SecretKey sessionKey = CryptoUtil.decryptSecretKey(msg.getSessionKey().getData(), myPrivateKey);
                            Session newSession = new Session(msg.getSenderId(), processAddresses.get(msg.getSenderId()),
                                    sessionKey);
                            sessions.put(msg.getSenderId(), newSession);
                            Logger.log(LogLevel.INFO, "MY ID " + myId + " Session established with process "
                                    + msg.getSenderId() + " session: " + newSession.toString());

                            activeSessionMap.put(msg.getSenderId(), true);
                            ScheduledFuture<?> task = resendTasks.remove(msg.getNonce());
                            if (task != null)
                                task.cancel(false);
                        }
                        break;

                    case START_SESSION:
                        InetSocketAddress address = processAddresses.getOrDefault(msg.getSenderId(),
                                Config.clientAddresses.get(msg.getSenderId()));

                        // Create a new session with the requester if we don't have one already
                        if (!activeSessionMap.containsKey(msg.getSenderId()) || !activeSessionMap.get(msg.getSenderId())) {

                            Session newSession = new Session(msg.getSenderId(), address);
                            sessions.put(msg.getSenderId(), newSession);

                            activeSessionMap.put(msg.getSenderId(), true);

                            Logger.log(LogLevel.INFO, "MY ID " + myId + " Session established with process "
                                    + msg.getSenderId() + " session: " + newSession.toString());
                        }

                        // encrypt session key with public key of sender
                        byte[] encryptedSessionKey = CryptoUtil
                                .encryptSecretKey(sessions.get(msg.getSenderId()).getSessionKey(), senderKey);
                        ByteArrayWrapper encryptedSessionKeyWrapper = new ByteArrayWrapper(encryptedSessionKey);

                        // send ACK
                        /* Message ackMsgSession = new Message(Message.Type.ACK_SESSION, -1, "", myId, null, msg.getNonce(),
                                encryptedSessionKeyWrapper); */

                        Message ackMsgSession = new Message.MessageBuilder(Message.Type.ACK_SESSION, -1, "", myId)
                                .setNonce(msg.getNonce()).setSessionKey(encryptedSessionKeyWrapper).build();
                        send(msg.getSenderId(), ackMsgSession);

                        break;

                    default:
                        // Wait for session to be established before processing further messages
                        try {
                            while (sessions.get(msg.getSenderId()) == null) {
                                // Logger.log(LogLevel.INFO, "Waiting for session with process " + msg.getSenderId() + " to
                                // be established...");
                                Thread.sleep(500);
                            }
                        } catch (InterruptedException e) {
                            Logger.log(LogLevel.ERROR, "Interrupted while waiting for session: " + e.toString());
                        }

                        // Check authenticity of the message
                        // TODO: sometimes this fails and I suspect it's due to concurrent access <- assess this
                        if (!CryptoUtil.checkHMACHmacSHA256(msg.getSignableContent().getBytes(), msg.getSignature(),
                                sessions.get(msg.getSenderId()).getSessionKey())) {
                            Logger.log(LogLevel.ERROR,
                                    "Signature verification failed for message from " + msg.getSenderId());
                            return;
                        }

                        // Send ACK to sender
                        /* Message ackMsg = new Message(Message.Type.ACK, msg.getEpoch(), msg.getValue(), myId, null, msg.getNonce());
                         */
                        Message ackMsg = new Message.MessageBuilder(Message.Type.ACK, msg.getEpoch(), msg.getValue(), myId)
                                .setNonce(msg.getNonce()).build();
                        send(msg.getSenderId(), ackMsg);

                        if(msg.getType() == Message.Type.COLLECTED){
                            System.out.println("COLLECTED MESSAGE RECEIVED");
                            System.out.println("STATE: " + msg.getState());
                            System.out.println("STATES MAP: " + msg.getStatesMap());
                            System.out.println("NONCE: " + msg.getNonce());
                            
                            System.out.println("SESSION: " + sessions.get(msg.getSenderId()));
                            System.out.println("SESSION ACK COUNTER: " + sessions.get(msg.getSenderId()).getAckCounter());
                        }

                        // Process the message if we haven't seen it before
                        if (session != null && session.getAckCounter() <= msg.getNonce()) {
                            if(msg.getType() == Message.Type.COLLECTED){
                                System.out.println("SENDING COLLECTED MESSAGE TO DELIVERED QUEUE");
                            }
                            deliveredQueue.put(msg);
                            session.incrementAckCounter();
                        }
                        break;
                }

            } else {
                Logger.log(LogLevel.ERROR, "Unknown sender: " + msg.getSenderId());
            }
        } catch (EOFException eof) { // TODO: Deal with EOFException
            // Logger.log(LogLevel.ERROR, "EOF reached");
        } catch (Exception e) {
            Logger.log(LogLevel.ERROR, "Exception: " + e.toString());
            e.printStackTrace();
        }
    }

    public void send(int destId, Message msg) throws Exception {
        InetSocketAddress address = processAddresses.getOrDefault(destId, Config.clientAddresses.get(destId));

        if (address == null) {
            throw new Exception("Unknown destination: " + destId);
        }

        // Wait for session to be established before sending messages
        try {
            if (msg.getType() != Message.Type.START_SESSION && msg.getType() != Message.Type.ACK_SESSION) {
                while (!activeSessionMap.getOrDefault(destId, false)) {
                    // Logger.log(LogLevel.INFO, "Waiting for session with process " + destId + " to be
                    // established...");
                    Thread.sleep(500);
                }
            }
        } catch (InterruptedException e) {
            Logger.log(LogLevel.ERROR, "Interrupted while waiting for session: " + e.toString());
        }

        senderWorkerPool.submit(() -> {
            Message signedMsg = msg;
            // Sign message
            byte[] sig = null;

            Session session = sessions.get(destId);

            // Check if the nonce was set accordingly
            if (msg.getNonce() == -1) {
                msg.setNonce(session.getSentCounter());
            }

            try {
                // START SESSION messages (and ACKs) are signed differently
                if (msg.getType() == Message.Type.START_SESSION || msg.getType() == Message.Type.ACK_SESSION) {
                    // DO NOTHING
                }
                // Other messages are signed with the session key
                else {
                    SecretKey sessionKey = session.getSessionKey();
                    sig = CryptoUtil.getHMACHmacSHA256(msg.getSignableContent().getBytes(), sessionKey);
                }
            } catch (Exception e) {
                Logger.log(LogLevel.ERROR, "Failed to sign message: " + e.toString());
                return;
            }

            // Use the appropriate constructor based on whether session key is present
            Session s = sessions.get(destId);
            SecretKey sessionKey = s.getSessionKey();
            if (sessionKey != null) {
                if (msg.getType() == Message.Type.STATE) {
                    /* signedMsg = new Message(msg.getType(), msg.getEpoch(), msg.getValue(), msg.getSenderId(), sig, msg.getNonce(), null,
                            msg.getState()); */
                    signedMsg = new Message.MessageBuilder(msg.getType(), msg.getEpoch(), msg.getValue(), msg.getSenderId())
                            .setSignature(sig).setNonce(msg.getNonce()).setState(msg.getState()).build();

                } else if (msg.getType() == Message.Type.ACK_SESSION) {
                    /* signedMsg = new Message(msg.getType(), msg.getEpoch(), msg.getValue(), msg.getSenderId(), sig, msg.getNonce(),
                            msg.getSenderId()); */
                    signedMsg = new Message.MessageBuilder(msg.getType(), msg.getEpoch(), msg.getValue(), msg.getSenderId())
                            .setSignature(sig).setNonce(msg.getNonce()).setSessionKey(msg.getSessionKey()).build();
                            
                } else if (msg.getType() == Message.Type.COLLECTED) {
                    /* signedMsg = new Message(msg.getType(), msg.getEpoch(), msg.getValue(), msg.getSenderId(), sig, msg.getNonce(),
                            msg.getSenderId(), msg.getState(), msg.statesMap); */
                    signedMsg = new Message.MessageBuilder(msg.getType(), msg.getEpoch(), msg.getValue(), msg.getSenderId())
                            .setSignature(sig).setNonce(msg.getNonce()).setState(msg.getState()).setStatesMap(msg.getStatesMap()).build();

                } else if (msg.getType() == Message.Type.WRITE) {
                    /* signedMsg = new Message(msg.getType(), msg.getEpoch(), msg.getValue(), msg.getSenderId(), sig, msg.getNonce(),
                            msg.getSenderId(), msg.state, msg.statesMap, msg.write); */

                    signedMsg = new Message.MessageBuilder(msg.getType(), msg.getEpoch(), msg.getValue(), msg.getSenderId())
                            .setSignature(sig).setNonce(msg.getNonce()).setState(msg.getState()).setStatesMap(msg.getStatesMap())
                            .setWrite(msg.getWrite()).build();
                } else {
                    /* signedMsg = new Message(msg.getType(), msg.getEpoch(), msg.getValue(), msg.getSenderId(), sig, msg.getNonce());
                     * 
                     */
                    signedMsg = new Message.MessageBuilder(msg.getType(), msg.getEpoch(), msg.getValue(), msg.getSenderId())
                            .setSignature(sig).setNonce(msg.getNonce()).build();
                }
            } else {
                /* signedMsg = new Message(msg.getType(), msg.getEpoch(), msg.getValue(), msg.getSenderId(), sig, msg.getNonce()); */
                signedMsg = new Message.MessageBuilder(msg.getType(), msg.getEpoch(), msg.getValue(), msg.getSenderId())
                        .setSignature(sig).setNonce(msg.getNonce()).build();
            }

            System.out.println("SENDING MESSAGE TO " + destId + " OF TYPE " + msg.getType() + " WITH NONCE " + msg.getNonce());
            try {
                Logger.log(LogLevel.DEBUG,
                        "Sending message to " + destId + " of type " + msg.getType() + " with nonce " + msg.getNonce());
                // if not ack, then send as true
                if (msg.getType() != Message.Type.ACK && msg.getType() != Message.Type.ACK_SESSION
                        && msg.getType() != Message.Type.START_SESSION) {
                    sendMessage(address, signedMsg, true, destId);
                } else {
                    sendMessage(address, signedMsg, false, destId);
                }

                // Don't schedule resends for ACK messages
                if (msg.getType() != Message.Type.ACK && msg.getType() != Message.Type.ACK_SESSION) {
                    scheduleResend(destId, signedMsg);
                }
            } catch (Exception e) {
                Logger.log(LogLevel.ERROR, "Failed to send message: " + e.toString());
            }
        });
    }

    private void sendMessage(InetSocketAddress address, Message msg, boolean firstSend, int destId) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(bos))) {
            oos.writeObject(msg);
            oos.flush();
            byte[] data = bos.toByteArray();
            DatagramPacket packet = new DatagramPacket(data, data.length, address);
            socket.send(packet);

            if (firstSend) {
                // get session
                Session session = sessions.get(destId);
                session.incrementSentCounter();
            }
        }
    }

    private void scheduleResend(int destId, Message msg) {
        if (msg.getType() == Message.Type.START_SESSION) {
            // Use global resendTasks for session initiation
            ScheduledFuture<?> future = senderWorkerPool.scheduleAtFixedRate(() -> {
                if (!activeSessionMap.getOrDefault(destId, false)) {
                    try {
                        Logger.log(LogLevel.DEBUG, "Resending session initiation to " + destId);
                        sendMessage(processAddresses.get(destId), msg, false, destId);
                    } catch (Exception e) {
                        Logger.log(LogLevel.ERROR, "Failed to resend session message: " + e.toString());
                    }
                } else {
                    // Cancel once the session is established
                    ScheduledFuture<?> task = resendTasks.remove(destId);
                    if (task != null)
                        task.cancel(false);
                }
            }, 2L, 2L, TimeUnit.SECONDS);

            resendTasks.put(destId, future);
        } else {
            // Use session-specific map for regular message resends
            sessionResendTasks.putIfAbsent(destId, new ConcurrentHashMap<>());
            ConcurrentMap<Integer, ScheduledFuture<?>> sessionTasks = sessionResendTasks.get(destId);

            ScheduledFuture<?> future = senderWorkerPool.scheduleAtFixedRate(() -> {
                Session session = sessions.get(destId);
                if (session == null)
                    return;

                try {
                    if (msg.getNonce() >= session.getSentCounter()) {
                        Logger.log(LogLevel.DEBUG,
                                "Resending message to " + destId + " of type " + msg.getType() + " with nonce " + msg.getNonce());
                        sendMessage(processAddresses.get(destId), msg, false, destId);
                    } else {
                        // Logger.log(LogLevel.DEBUG, "Message acknowledged, stopping resends for " + msg.getNonce());
                        ScheduledFuture<?> task = sessionTasks.remove(msg.getNonce());
                        if (task != null)
                            task.cancel(false);
                    }
                } catch (Exception e) {
                    Logger.log(LogLevel.ERROR, "Failed to resend message: " + e.toString());
                }
            }, 4L, 2L, TimeUnit.SECONDS);

            sessionTasks.put(msg.getNonce(), future);
        }
    }

    public Message deliver() throws InterruptedException {
        return deliveredQueue.take();
    }

    public void close() {
        for (ScheduledFuture<?> task : resendTasks.values()) {
            if (task != null)
                task.cancel(false);
        }
        resendTasks.clear();

        for (ConcurrentMap<Integer, ScheduledFuture<?>> sessionTasks : sessionResendTasks.values()) {
            for (ScheduledFuture<?> task : sessionTasks.values()) {
                if (task != null)
                    task.cancel(false);
            }
        }
        sessionResendTasks.clear();
        activeSessionMap.clear();

        senderWorkerPool.shutdownNow();
        listenerWorkerPool.shutdownNow();
        socket.close();
    }

}
