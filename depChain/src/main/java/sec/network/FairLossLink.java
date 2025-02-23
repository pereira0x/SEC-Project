package sec.network;

import java.net.*;
import java.io.*;

public class FairLossLink {

    public FairLossLink() {
    }

    public void send(String destIp, int destPort, String message) {
        System.out.println("Sending message to " + destIp + ":" + destPort + " - " + message);
        try {
            Socket socket = new Socket(destIp, destPort);
            DatagramPacket packet = new DatagramPacket(message.getBytes(), message.length(),
                    InetAddress.getByName(destIp), destPort);
            socket.getOutputStream().write(packet.getData());
            socket.close();
        } catch (IOException e) {
            // try again
        }
    }

    public void receive(int port) {
        try {
            ServerSocket serverSocket = new ServerSocket(port);
            Socket socket = serverSocket.accept();
            byte[] buffer = new byte[1024];
            socket.getInputStream().read(buffer);
            System.out.println("Received message: " + new String(buffer));
            socket.close();
            serverSocket.close();
        } catch (IOException e) {
            // try again
        }
    }

}
