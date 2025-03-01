package depchain.network;

import java.net.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.security.PrivateKey;
import java.security.PublicKey;
import depchain.utils.CryptoUtil;
import depchain.utils.Config;

public class PerfectLink {
    public static final int MAX_WORKERS = 50;
    private final int myId;
    private final DatagramSocket socket;
    private final ConcurrentMap<Integer, InetSocketAddress> processAddresses;
    private final PrivateKey myPrivateKey;
    private final ConcurrentMap<Integer, PublicKey> publicKeys;
    // list for the nonces of acks to be received
    private final List<byte[]> ackQueue = new ArrayList<>();
    // list for the nonces of sent messages
    private final List<byte[]> sentQueue = new ArrayList<>();

    private final ExecutorService workerPool; // Worker pool
    private final BlockingQueue<Message> deliveredQueue = new LinkedBlockingQueue<>();

    public PerfectLink(int myId, int port, ConcurrentMap<Integer, InetSocketAddress> processAddresses,
            PrivateKey myPrivateKey, ConcurrentMap<Integer, PublicKey> publicKeys) throws Exception {
        this.myId = myId;
        this.socket = new DatagramSocket(port);
        this.processAddresses = processAddresses;
        this.myPrivateKey = myPrivateKey;
        this.publicKeys = publicKeys;
        this.workerPool = Executors.newFixedThreadPool(MAX_WORKERS); // Worker pool

        // Start listener
        new Thread(this::startListener, "PerfectLink-Listener").start();
    }

    private void startListener() {
        byte[] buffer = new byte[8192];
        while (!socket.isClosed()) {
            System.out.println("DEBUG: Received message");
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(packet);
                System.out.println("Number of acks in ackQueue: " + ackQueue.size());
                System.out.println("ACK queue content: ");
                for (byte[] n : ackQueue) {
                    System.out.println(Arrays.toString(n));
                }
                System.out.println("Number of sent in sentQueue: " + sentQueue.size());
                workerPool.submit(() -> processMessage(packet)); // Submit work to worker pool
            } catch (SocketException e) {
                if (socket.isClosed())
                    break;
                System.err.println("Socket error: " + e.getMessage());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void processMessage(DatagramPacket packet) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(packet.getData(), packet.getOffset(),
                packet.getLength());
                ObjectInputStream ois = new ObjectInputStream(bis)) {
            // Thread.sleep(5000);
            Message msg = (Message) ois.readObject();
            System.out.println("DEBUG: Received message from " + msg.senderId + " of type " + msg.type);

            PublicKey senderKey = publicKeys.get(msg.senderId);
            // verify the signature of the message
            if (senderKey != null && CryptoUtil.verify(msg.getSignableContent().getBytes(), msg.signature, senderKey)) {
                switch (msg.type) {
                    case CLIENT_REQUEST:
                        System.out.println("CLIENT_REQUEST RECEIVED with nonce: " + Arrays.toString(msg.nonce));
                         //deliveredQueue.offer(msg);
                        // Send ACK to sender
                        Message firstAckMsg = new Message(Message.Type.ACK, msg.epoch, msg.value, myId, null,
                                msg.nonce);
                        ackQueue.add(msg.nonce);

                        send(msg.senderId, firstAckMsg);


                        break;
                    case ACK:
                        System.out.println("ACK RECEIVED content");
                        System.out.println(Arrays.toString(msg.nonce));
                        System.out.println(msg.nonce);
                        System.out.println("ACK queue content: ");
                        for (byte[] n : ackQueue) {
                            System.out.println(Arrays.toString(n));
                            System.out.println(n);
                        }
                        // last ack of "handshake" received
                        // if ack Queue non empty means we were waiting for the second ack
                        if (!ackQueue.isEmpty()) {
                            // remove from ackQueue the message that has the same nonce
                            for (byte[] n : ackQueue) {
                                if (Arrays.equals(n, msg.nonce)) {
                                    ackQueue.remove(n);
                                    // Thread.sleep(5000);
                                    break;
                                }
                            }
                        }

                        // first ack of the "handshake" received
                        else {
                            Message secondAckMsg = msg;

                            // remove from sent the message that has the same nonce
                            for (byte[] n : sentQueue) {
                                if (Arrays.equals(n, msg.nonce)) {
                                    sentQueue.remove(n);
                                    break;
                                }
                            }

                            send(msg.senderId, secondAckMsg);
                            //deliveredQueue.offer(msg);

                        }
                        break;
                    default:
                        System.err.println("Unknown message type: " + msg.type);
                        break;
                }
            } else {
                System.err.println("Message signature verification failed for sender: " + msg.senderId);
            }

            /*
             * Message msg = (Message) ois.readObject();
             * System.out.println("DEBUG: Received message from " + msg.senderId +
             * " of type " + msg.type);
             * 
             * PublicKey senderKey = publicKeys.get(msg.senderId);
             * if (senderKey != null &&
             * CryptoUtil.verify(msg.getSignableContent().getBytes(), msg.signature,
             * senderKey)) {
             * deliveredQueue.offer(msg);
             * 
             * // Send ACK to sender
             * Message ackMsg = new Message(Message.Type.ACK, msg.epoch, msg.value, myId,
             * null, msg.nonce);
             * send(msg.senderId, ackMsg);
             * 
             * 
             * } else {
             * System.err.println("Message signature verification failed for sender: " +
             * msg.senderId);
             * }
             */
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void send(int destId, Message msg) throws Exception {
        InetSocketAddress address = processAddresses.getOrDefault(destId, Config.clientAddresses.get(destId));
        if (address == null) {
            throw new Exception("Unknown destination: " + destId);
        }

        Message signedMsg = msg;
        if (msg.signature == null) {
            byte[] sig = CryptoUtil.sign(msg.getSignableContent().getBytes(), myPrivateKey);
            signedMsg = new Message(msg.type, msg.epoch, msg.value, msg.senderId, sig, msg.nonce);
        }

        if (msg.type != Message.Type.ACK) {
            sentQueue.add(signedMsg.nonce);
        }

        // TODO: TIMEOUT to send message again NOT IMPLEMENTED

        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(bos))) {
            oos.writeObject(signedMsg);
            oos.flush();
            byte[] data = bos.toByteArray();
            DatagramPacket packet = new DatagramPacket(data, data.length, address);

            System.out.println("DEBUG: Sending message to " + destId + " of type " + msg.type);
            System.out.println("DEBUG: Message nonce: " + Arrays.toString(msg.nonce));
            socket.send(packet);
        }
    }

    public Message deliver() throws InterruptedException {
        return deliveredQueue.take();
    }

    public void close() {
        socket.close();
        // close worker pool - kill all threads
        workerPool.shutdownNow();
    }
}
