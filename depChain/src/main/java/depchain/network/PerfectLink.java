package depchain.network;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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

    // Map to track sent messages by nonce
    // private final ConcurrentMap<Integer, Boolean> sentQueue = new
    // ConcurrentHashMap<>();

    /*
     * // Map to track session initiation messages by nonce private final ConcurrentMap<int, Integer> initSessionQueue =
     * new ConcurrentHashMap<>();
     */

    // Global map for session initiation resends
    private final ConcurrentMap<Integer, ScheduledFuture<?>> resendTasks = new ConcurrentHashMap<>();

    // Map to store resend tasks
    private final ConcurrentMap<Integer, ConcurrentMap<Integer, ScheduledFuture<?>>> sessionResendTasks = new ConcurrentHashMap<>();

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
        }

        else {
            // Server initiate sessions with other server of lower ID
            for (int i = 1; i < myId; i++) {
                Logger.log(LogLevel.INFO, "Process " + myId + " starting session with process " + i);
                startSession(i);
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
        Message msg = new Message(Message.Type.START_SESSION, -1, null, myId, null, nonce);

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
        byte[] buffer = new byte[8192];
        while (!socket.isClosed()) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(packet);
                // Logger.log(LogLevel.DEBUG, "Received packet from " +
                // packet.getSocketAddress());
                listenerWorkerPool.submit(() -> processMessage(packet));
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
            Message msg = (Message) ois.readObject();
            Logger.log(LogLevel.DEBUG,
                    "Received message of type " + msg.type + " from " + msg.senderId + " with nonce " + msg.nonce);

            PublicKey senderKey = publicKeys.get(msg.senderId);
            Session session = sessions.get(msg.senderId);

            // In a real implementation, you would verify the signature here
            if (senderKey != null) {
                switch (msg.type) {

                    case ACK:
                        // Regular ACK for another message type
                        if (session != null && session.getSentCounter() == msg.nonce) {
                            deliveredQueue.offer(msg);
                            session.incrementSentCounter();

                            // Cancel the resend task
                            ScheduledFuture<?> task = sessionResendTasks
                                    .getOrDefault(msg.senderId, new ConcurrentHashMap<>()).remove(msg.nonce);

                            if (task != null) {
                                task.cancel(false);
                            }
                        }
                        break;

                    case ACK_SESSION:
                        if (activeSessionMap.containsKey(msg.senderId) && !activeSessionMap.get(msg.senderId)) {

                            // decrypt session key with private key
                            SecretKey sessionKey = CryptoUtil.decryptSecretKey(msg.sessionKey.getData(), myPrivateKey);
                            Session newSession = new Session(msg.senderId, processAddresses.get(msg.senderId),
                                    sessionKey);
                            sessions.put(msg.senderId, newSession);

                            activeSessionMap.put(msg.senderId, true);
                            ScheduledFuture<?> task = resendTasks.remove(msg.nonce);
                            if (task != null)
                                task.cancel(false);
                        }
                        break;

                    case START_SESSION:
                        InetSocketAddress address = processAddresses.getOrDefault(msg.senderId,
                                Config.clientAddresses.get(msg.senderId));

                        // Create a new session with the requester if we don't have one already
                        if (!activeSessionMap.containsKey(msg.senderId) || !activeSessionMap.get(msg.senderId)) {

                            Session newSession = new Session(msg.senderId, address);
                            sessions.put(msg.senderId, newSession);

                            activeSessionMap.put(msg.senderId, true);

                            Logger.log(LogLevel.INFO, "Session established with process " + msg.senderId);
                        }

                        // encrypt session key with public key of sender
                        byte[] encryptedSessionKey = CryptoUtil
                                .encryptSecretKey(sessions.get(msg.senderId).getSessionKey(), senderKey);
                        ByteArrayWrapper encryptedSessionKeyWrapper = new ByteArrayWrapper(encryptedSessionKey);

                        // send ACK
                        Message ackMsgSession = new Message(Message.Type.ACK_SESSION, -1, myId, null, msg.nonce,
                                encryptedSessionKeyWrapper);
                        send(msg.senderId, ackMsgSession);

                        break;

                    default:

                        // Check authenticity of the message
                        if (!CryptoUtil.checkHMACHmacSHA256(msg.getSignableContent().getBytes(), msg.signature,
                                sessions.get(msg.senderId).getSessionKey())) {
                            Logger.log(LogLevel.ERROR,
                                    "Signature verification failed for message from " + msg.senderId);
                            return;
                        }

                        // Send ACK to sender
                        Message ackMsg = new Message(Message.Type.ACK, msg.epoch, msg.value, myId, null, msg.nonce);
                        send(msg.senderId, ackMsg);

                        // Process the message if we haven't seen it before
                        if (session != null && session.getAckCounter() == msg.nonce) {
                            deliveredQueue.offer(msg);
                            session.incrementAckCounter();
                        }
                        break;
                }

                // print sessions
                for (Session s : sessions.values()) {
                    Logger.log(LogLevel.DEBUG, s.toString());
                }

            } else {
                Logger.log(LogLevel.ERROR, "Unknown sender: " + msg.senderId);
            }
        } catch (Exception e) {
            Logger.log(LogLevel.ERROR, "Exception: " + e.toString());
            e.printStackTrace();
        }
    }

    public void send(int destId, Message msg) throws Exception {
        InetSocketAddress address = processAddresses.getOrDefault(destId, Config.clientAddresses.get(destId));

        if (msg.type != Message.Type.START_SESSION && msg.type != Message.Type.ACK_SESSION) {
            while (!activeSessionMap.getOrDefault(destId, false)) {
                Logger.log(LogLevel.INFO, "Waiting for session with process " + destId + " to be established...");
                Thread.sleep(2000);
            }
        }

        if (address == null) {
            throw new Exception("Unknown destination: " + destId);
        }

        senderWorkerPool.submit(() -> {
            Message signedMsg = msg;
            // Sign message
            byte[] sig = null;
            try {
                // START SESSION messages (and ACKs) are signed differently
                if (msg.type == Message.Type.START_SESSION || msg.type == Message.Type.ACK_SESSION) {
                    // DO NOTHING
                }
                // Other messages are signed with the session key
                else {
                    sessions.get(destId).getSessionKey();
                    SecretKey sessionKey = sessions.get(destId).getSessionKey();
                    sig = CryptoUtil.getHMACHmacSHA256(msg.getSignableContent().getBytes(), sessionKey);

                }

            } catch (Exception e) {
                Logger.log(LogLevel.ERROR, "Failed to sign message: " + e.toString());
                return;
            }

            // Use the appropriate constructor based on whether session key is present
            if (msg.sessionKey != null) {
                signedMsg = new Message(msg.type, msg.epoch, msg.senderId, sig, msg.nonce, msg.sessionKey);
            } else {
                signedMsg = new Message(msg.type, msg.epoch, msg.value, msg.senderId, sig, msg.nonce);
            }

            try {
                Logger.log(LogLevel.DEBUG, "Sending message to " + destId + " of type " + msg.type);
                sendMessage(address, signedMsg);

                // Don't schedule resends for ACK messages
                if (msg.type != Message.Type.ACK && msg.type != Message.Type.ACK_SESSION) {

                    scheduleResend(destId, signedMsg);
                }
            } catch (Exception e) {
                Logger.log(LogLevel.ERROR, "Failed to send message: " + e.toString());
            }
        });
    }

    private void sendMessage(InetSocketAddress address, Message msg) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(bos))) {
            oos.writeObject(msg);
            oos.flush();
            byte[] data = bos.toByteArray();
            DatagramPacket packet = new DatagramPacket(data, data.length, address);

            socket.send(packet);
        }
    }

    private void scheduleResend(int destId, Message msg) {
        if (msg.type == Message.Type.START_SESSION) {
            // Use global resendTasks for session initiation
            ScheduledFuture<?> future = senderWorkerPool.scheduleAtFixedRate(() -> {
                if (!activeSessionMap.getOrDefault(destId, false)) {
                    try {
                        Logger.log(LogLevel.DEBUG, "Resending session initiation to " + destId);
                        sendMessage(processAddresses.get(destId), msg);
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
                    if (msg.nonce >= session.getSentCounter()) {
                        Logger.log(LogLevel.DEBUG, "Resending message to " + destId + " of type " + msg.type);
                        sendMessage(processAddresses.get(destId), msg);
                    } else {
                        // Logger.log(LogLevel.DEBUG, "Message acknowledged, stopping resends for " + msg.nonce);
                        ScheduledFuture<?> task = sessionTasks.remove(msg.nonce);
                        if (task != null)
                            task.cancel(false);
                    }
                } catch (Exception e) {
                    Logger.log(LogLevel.ERROR, "Failed to resend message: " + e.toString());
                }
            }, 2L, 2L, TimeUnit.SECONDS);

            sessionTasks.put(msg.nonce, future);
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
