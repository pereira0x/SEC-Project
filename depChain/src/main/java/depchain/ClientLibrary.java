package depchain;

import java.net.*;
import java.util.concurrent.*;

public class ClientLibrary {
    private final PerfectLink perfectLink;
    private final int leaderId;
    private final InetSocketAddress leaderAddress;
    private final int clientId;
    
    public ClientLibrary(PerfectLink perfectLink, int leaderId, InetSocketAddress leaderAddress, int clientId) {
        this.perfectLink = perfectLink;
        this.leaderId = leaderId;
        this.leaderAddress = leaderAddress;
        this.clientId = clientId;
    }
    
    // Append a string to the blockchain.
    public String append(String request) throws Exception {
        // Create a CLIENT_REQUEST message. (Assume the client sets its own ID.)
        Message reqMsg = new Message(Message.Type.CLIENT_REQUEST, 0, request, clientId, null);
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
