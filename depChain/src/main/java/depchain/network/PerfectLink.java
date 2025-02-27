package depchain.network;

import java.net.*;
import java.io.*;
import java.util.concurrent.*;
import java.security.PrivateKey;
import java.security.PublicKey;
import depchain.utils.CryptoUtil;
import depchain.utils.Config;

public class PerfectLink {
    public static final int MAX_WORKERS = 30;
    private final int myId;
    private final DatagramSocket socket;
    private final ConcurrentMap<Integer, InetSocketAddress> processAddresses;
    private final PrivateKey myPrivateKey;
    private final ConcurrentMap<Integer, PublicKey> publicKeys;

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
        byte[] buffer = new byte[4096];
        while (!socket.isClosed()) {
            System.out.println("DEBUG:Received message");
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(packet);
                workerPool.submit(() -> processMessage(packet)); // Submit work to worker pool
            } catch (SocketException e) {
                if (socket.isClosed()) break;
                System.err.println("Socket error: " + e.getMessage());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void processMessage(DatagramPacket packet) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(packet.getData(), packet.getOffset(), packet.getLength());
             ObjectInputStream ois = new ObjectInputStream(bis)) {

            Message msg = (Message) ois.readObject();
            System.out.println("DEBUG: Received message from " + msg.senderId + " of type " + msg.type);

            PublicKey senderKey = publicKeys.get(msg.senderId);
            if (senderKey != null && CryptoUtil.verify(msg.getSignableContent().getBytes(), msg.signature, senderKey)) {
                deliveredQueue.offer(msg);
            } else {
                System.err.println("Message signature verification failed for sender: " + msg.senderId);
            }
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

        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(bos))) {
            oos.writeObject(signedMsg);
            oos.flush();
            byte[] data = bos.toByteArray();
            DatagramPacket packet = new DatagramPacket(data, data.length, address);

            System.out.println("DEBUG: Sending message to " + destId + " of type " + msg.type);
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
