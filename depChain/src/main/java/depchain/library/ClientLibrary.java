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
    private final List<Integer> nodeIds;
    private final int clientId;
    private int nonce = 0;
    private final int f;
    private final long timeout = 10000;
    private int confirmedAppends = 0;

    public ClientLibrary(PerfectLink perfectLink, int leaderId, List<Integer> nodeIds, int clientId, int f) {
        this.perfectLink = perfectLink;
        this.leaderId = leaderId;
        this.nodeIds = nodeIds;
        this.clientId = clientId;
        this.f = f;
    }

    // Append a string to the blockchain.
    public String append(String request) throws Exception {
        // Create a CLIENT_REQUEST message. (Assume the client sets its own ID.)
        Message reqMsg = new Message.MessageBuilder(Message.Type.CLIENT_REQUEST, confirmedAppends, request, clientId, clientId).setNonce(nonce)
                .build();

        broadcast(reqMsg);

        // Start the timer
        long startTime = System.currentTimeMillis();

        // initialize list of replies 
        List<String> replies = new ArrayList<>();


        //clear queue of old messages (old replies)
        perfectLink.clearQueue();
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
                        confirmedAppends++;
                        return request;
                }
            }

        }

        Logger.log(LogLevel.ERROR, "Timeout: No replies received at least f+1 times");
        return null;
    }

    public void broadcast(Message msg) throws Exception {
        for (int nodeId : nodeIds) {
            perfectLink.send(nodeId, msg);
        }
    }
}
