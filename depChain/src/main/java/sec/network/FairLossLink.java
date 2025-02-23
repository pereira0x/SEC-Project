package sec.network;

import java.net.*;
import java.io.*;

public class FairLossLink {

    public FairLossLink() {
    }

    public void send(String destIp, int destPort, String message) {
        System.out.println("Sending message to " + destIp + ":" + destPort + " - " + message);
            /* try {
                Socket socket = new Socket(destIp, destPort);
                DatagramPacket packet = new DatagramPacket(this.message.getBytes(), this.message.length(),
                        InetAddress.getByName(destIp), destPort);
                socket.getOutputStream().write(packet.getData());
                socket.close();
            } catch (IOException e) {
                // try again
            } */
    
    }

}
