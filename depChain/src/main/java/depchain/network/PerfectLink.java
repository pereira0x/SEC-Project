package depchain.network;

import java.net.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.List;
import java.util.ArrayList;
import java.security.PrivateKey;
import java.security.PublicKey;
import depchain.utils.Logger;
import depchain.utils.Logger.LogLevel;
import depchain.utils.CryptoUtil;
import depchain.utils.Config;
import depchain.utils.ByteArrayWrapper;

public class PerfectLink {
    public static final int MAX_WORKERS = 50;
    private final int myId;
    private final DatagramSocket socket;
    private final ConcurrentMap<Integer, InetSocketAddress> processAddresses;
    private final PrivateKey myPrivateKey;
    private final ConcurrentMap<Integer, PublicKey> publicKeys;
    // list for the nonces of acks sent
    private final List<byte[]> ackQueue = new ArrayList<>();
    // list for the nonces of sent messages using ByteArrayWrapper
    private final ConcurrentMap<ByteArrayWrapper, Boolean> sentQueue = new ConcurrentHashMap<>();
    // Map to store resend tasks
    private final ConcurrentMap<ByteArrayWrapper, ScheduledFuture<?>> resendTasks = new ConcurrentHashMap<>();

    private final ExecutorService listenerWorkerPool; // Worker pool
    private final ScheduledExecutorService senderWorkerPool; // Worker pool
    private final BlockingQueue<Message> deliveredQueue = new LinkedBlockingQueue<>();

    public PerfectLink(int myId, int port, ConcurrentMap<Integer, InetSocketAddress> processAddresses,
            PrivateKey myPrivateKey, ConcurrentMap<Integer, PublicKey> publicKeys) throws Exception {
        this.myId = myId;
        this.socket = new DatagramSocket(port);
        this.processAddresses = processAddresses;
        this.myPrivateKey = myPrivateKey;
        this.publicKeys = publicKeys;
        this.listenerWorkerPool = Executors.newFixedThreadPool(MAX_WORKERS); // Worker pool
        this.senderWorkerPool = Executors.newScheduledThreadPool(MAX_WORKERS); // Worker pool

        // Start listener
        new Thread(this::startListener, "PerfectLink-Listener").start();
    }

    private void startListener() {
        byte[] buffer = new byte[8192];
        while (!socket.isClosed()) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(packet);
                Logger.log(LogLevel.DEBUG, "Received packet from " + packet.getSocketAddress());

                listenerWorkerPool.submit(() -> processMessage(packet)); // Submit work to worker pool
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
            Logger.log(LogLevel.DEBUG, "Received message of type " + msg.type + " from " + msg.senderId);

            PublicKey senderKey = publicKeys.get(msg.senderId);
            // verify the signature of the message
            if (senderKey != null && CryptoUtil.verify(msg.getSignableContent().getBytes(), msg.signature, senderKey)) {
                switch (msg.type) {
                    case CLIENT_REQUEST:
                        // Send ACK to sender
                        Message ackMsg = new Message(Message.Type.ACK, msg.epoch, msg.value, myId, null, msg.nonce);
                        ackQueue.add(msg.nonce);
                        send(msg.senderId, ackMsg);
                        // deliveredQueue.offer(msg); // TODO: decide with consensus
                        break;

                    case ACK:
                        ByteArrayWrapper nonceWrapper = new ByteArrayWrapper(msg.nonce);

                        if (sentQueue.containsKey(nonceWrapper)) {
                            sentQueue.put(nonceWrapper, true);

                            // Cancel the resend task
                            ScheduledFuture<?> task = resendTasks.remove(nonceWrapper);
                            if (task != null) {
                                task.cancel(false);
                            }
                        }

                        deliveredQueue.offer(msg);
                        break;

                    default:
                        Logger.log(LogLevel.ERROR, "Unknown message type: " + msg.type);
                        break;
                }
            } else {
                Logger.log(LogLevel.ERROR, "Signature verification failed for message from " + msg.senderId);
            }
        } catch (Exception e) {
            Logger.log(LogLevel.ERROR, "Exception: " + e.toString());
        }
    }

    public void send(int destId, Message msg) throws Exception {
        InetSocketAddress address = processAddresses.getOrDefault(destId, Config.clientAddresses.get(destId));
        if (address == null) {
            throw new Exception("Unknown destination: " + destId);
        }

        senderWorkerPool.submit(() -> {
            Message signedMsg = msg;
            if (msg.signature == null) {
                // sign message
                byte[] sig;
                try {
                    sig = CryptoUtil.sign(msg.getSignableContent().getBytes(), myPrivateKey);
                } catch (Exception e) {
                    Logger.log(LogLevel.ERROR, "Failed to sign message: " + e.toString());
                    return;
                }
                signedMsg = new Message(msg.type, msg.epoch, msg.value, msg.senderId, sig, msg.nonce);
            }

            if (msg.type != Message.Type.ACK) {
                ByteArrayWrapper nonceWrapper = new ByteArrayWrapper(msg.nonce);
                sentQueue.put(nonceWrapper, false);
            }

            try {
                sendMessage(address, signedMsg);
                if (msg.type != Message.Type.ACK) {
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
        ByteArrayWrapper nonceWrapper = new ByteArrayWrapper(msg.nonce);

        ScheduledFuture<?> future = senderWorkerPool.scheduleAtFixedRate(() -> {
            try {
                Boolean ackReceived = sentQueue.get(nonceWrapper);
                if (ackReceived == null || !ackReceived) {
                    Logger.log(LogLevel.DEBUG, "Resending message to " + destId + " of type " + msg.type);
                    InetSocketAddress address = processAddresses.getOrDefault(destId,
                            Config.clientAddresses.get(destId));
                    if (address != null) {
                        sendMessage(address, msg);
                    } else {
                        Logger.log(LogLevel.ERROR, "Unknown destination: " + destId);
                        // Cancel the task if we can't send
                        ScheduledFuture<?> task = resendTasks.remove(nonceWrapper);
                        if (task != null) {
                            task.cancel(false);
                        }
                        sentQueue.remove(nonceWrapper);
                    }
                } else {
                    Logger.log(Logger.LogLevel.DEBUG, "ACK received for message to " + destId + " of type " + msg.type);
                    // Cancel the scheduled task
                    ScheduledFuture<?> task = resendTasks.remove(nonceWrapper);
                    if (task != null) {
                        task.cancel(false);
                    }
                    // Remove from sentQueue to prevent memory leaks
                    sentQueue.remove(nonceWrapper);
                }
            } catch (Exception e) {
                Logger.log(LogLevel.ERROR, "Failed to resend message: " + e.toString());
            }
        }, 5L, 5L, TimeUnit.SECONDS);

        // Store the future so we can cancel it later
        resendTasks.put(nonceWrapper, future);
    }

    public Message deliver() throws InterruptedException {
        return deliveredQueue.take();
    }

    public void close() {
        // Cancel all scheduled tasks
        for (ScheduledFuture<?> task : resendTasks.values()) {
            if (task != null) {
                task.cancel(false);
            }
        }

        resendTasks.clear();
        sentQueue.clear();

        // Shutdown executor services
        senderWorkerPool.shutdownNow();
        listenerWorkerPool.shutdownNow();

        socket.close();
    }
}
