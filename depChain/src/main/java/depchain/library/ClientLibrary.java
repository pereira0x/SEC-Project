package depchain.library;

import java.net.InetSocketAddress;

import depchain.network.Message;
import depchain.network.PerfectLink;

public class ClientLibrary {
    private final PerfectLink perfectLink;
    private final int leaderId;
    private final InetSocketAddress leaderAddress;
    private final int clientId;
    private int nonce = 0;

    public ClientLibrary(PerfectLink perfectLink, int leaderId, InetSocketAddress leaderAddress, int clientId) {
        this.perfectLink = perfectLink;
        this.leaderId = leaderId;
        this.leaderAddress = leaderAddress;
        this.clientId = clientId;
    }

    // Append a string to the blockchain.
    public String append(String request) throws Exception {
        // Create a CLIENT_REQUEST message. (Assume the client sets its own ID.)
        Message reqMsg = new Message.MessageBuilder(Message.Type.CLIENT_REQUEST, 0, request, clientId)
            .setNonce(nonce)
            .build();
        perfectLink.send(leaderId, reqMsg);
        // Wait for a CLIENT_REPLY OR ACK
        while (true) {
            Message reply = perfectLink.deliver();
            if (reply.getType() == Message.Type.CLIENT_REPLY) {
                nonce++;
                return reply.getValue();
            }
        }
    }
}
