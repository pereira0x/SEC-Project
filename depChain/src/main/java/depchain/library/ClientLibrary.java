package depchain.library;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import depchain.network.Message;
import depchain.network.PerfectLink;
import depchain.utils.Logger;
import depchain.utils.Logger.LogLevel;

public class ClientLibrary {
    private final PerfectLink perfectLink;
    private final int leaderId;
    private final InetSocketAddress leaderAddress;
    private final int clientId;
    private int nonce = 0;
    private final int f;
    private final long timeout = 6000;
    private int sentMessages = 0;

    public ClientLibrary(PerfectLink perfectLink, int leaderId, InetSocketAddress leaderAddress, int clientId, int f) {
        this.perfectLink = perfectLink;
        this.leaderId = leaderId;
        this.leaderAddress = leaderAddress;
        this.clientId = clientId;
        this.f = f;
    }

    // Append a string to the blockchain.
    public String append(String request) throws Exception {
        // Create a CLIENT_REQUEST message. (Assume the client sets its own ID.)
        Message reqMsg = new Message.MessageBuilder(Message.Type.CLIENT_REQUEST, sentMessages, request, clientId).setNonce(nonce)
                .build();
        perfectLink.send(leaderId, reqMsg);

        // Start the timer
        long startTime = System.currentTimeMillis();

        // initialize list of replies 
        List<String> replies = new ArrayList<>();

        // Wait for f+1 equal CLIENT_REPLY messages.
        while (System.currentTimeMillis() - startTime < timeout) {
            Message reply = perfectLink.deliver();
            if (reply.getType() == Message.Type.CLIENT_REPLY) {
                Logger.log(LogLevel.INFO, "Received reply from " + reply.getSenderId() + ": " + reply.getValue());
                
                replies.add(reply.getValue());
                // Check if there are f+1 equal replies and that are the same as request
                if (replies.size() >= f + 1) {
                    int count = 0;
                    for (int i = 0; i < replies.size(); i++)
                        if (replies.get(i).equals(request))
                            count++;
                    if (count >= f + 1)
                        nonce++;
                        sentMessages++;
                        return request;
                }
            }

        }

        Logger.log(LogLevel.ERROR, "Timeout: No replies received at least f+1 times");
        return null;
    }
}
