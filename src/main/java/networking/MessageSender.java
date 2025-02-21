package networking;

import model.APLMessage;
import java.net.*;
import java.util.concurrent.*;

public class MessageSender implements Runnable {
    private final DatagramSocket socket;
    private final BlockingQueue<APLMessage> sendQueue = new LinkedBlockingQueue<>();
    private volatile boolean running = true;

    public MessageSender(DatagramSocket socket) {
        this.socket = socket;
    }

    public void sendMessage(APLMessage message, InetAddress dest, int port) {
        sendQueue.add(message);
    }

    @Override
    public void run() {
        try {
            while (running) {
                APLMessage message = sendQueue.take();
                byte[] data = ByteUtils.serialize(message);
                DatagramPacket packet = new DatagramPacket(data, data.length, message.getDestAddress(), message.getDestPort());
                socket.send(packet);
            }
        } catch (InterruptedException | IOException e) {
            if (running) System.err.println("MessageSender error: " + e.getMessage());
        }
    }

    public void stop() {
        running = false;
    }
}