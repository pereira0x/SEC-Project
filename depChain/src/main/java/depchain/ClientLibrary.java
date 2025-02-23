package depchain;

import java.net.*;
import java.util.concurrent.*;

public class ClientLibrary {
    private final PerfectLink perfectLink;
    private final int leaderId;
    private final InetSocketAddress leaderAddress;
    
    public ClientLibrary(PerfectLink perfectLink, int leaderId, InetSocketAddress leaderAddress) {
        this.perfectLink = perfectLink;
        this.leaderId = leaderId;
        this.leaderAddress = leaderAddress;
    }
    
    // Append a string to the blockchain.
    public String append(String request) throws Exception {
        // Create a CLIENT_REQUEST message. (Assume the client sets its own ID.)
        Message reqMsg = new Message(Message.Type.CLIENT_REQUEST, 0, request, ClientIdGenerator.getNextClientId(), null);
        perfectLink.send(leaderId, reqMsg);
        // Wait for a CLIENT_REPLY.
        while (true) {
            Message reply = perfectLink.deliver();
            if (reply.type == Message.Type.CLIENT_REPLY) {
                return reply.value;
            }
        }
    }
}
