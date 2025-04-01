package depchain.library;

import java.util.ArrayList;
import java.util.List;

import depchain.blockchain.Transaction;
import depchain.blockchain.block.Block;
import depchain.network.Message;
import depchain.network.PerfectLink;
import depchain.utils.CryptoUtil;
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
    private int confirmedTransfers = 0;

    public ClientLibrary(PerfectLink perfectLink, int leaderId, List<Integer> nodeIds, int clientId, int f) {
        this.perfectLink = perfectLink;
        this.leaderId = leaderId;
        this.nodeIds = nodeIds;
        this.clientId = clientId;
        this.f = f;
    }

    // Append a string to the blockchain.
/*     public String append(String request) throws Exception {
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
        
                Logger.log(LogLevel.ERROR, "Timeout: No valid replies received at least f+1 times");
                return null;
            }

        }

        Logger.log(LogLevel.ERROR, "Timeout: No replies received at least f+1 times");
        return null;
    } */

    public void broadcast(Message msg) throws Exception {
        for (int nodeId : nodeIds) {
            perfectLink.send(nodeId, msg);
        }
    }

    public String transferDepcoin(int recipientId, Long amount) throws Exception {
        // Create a CLIENT_REQUEST message. (Assume the client sets its own ID.)
        Transaction transaction = new Transaction.TransactionBuilder()
                .setSender(String.valueOf(this.clientId))
                .setRecipient(String.valueOf(recipientId))
                .setAmount(amount)
                .setNonce(CryptoUtil.generateNonce())
                .setType(Transaction.TransactionType.TRANSFER_DEPCOIN)
                .build();
        Message reqMsg = new Message.MessageBuilder(Message.Type.CLIENT_REQUEST, confirmedTransfers, clientId, clientId)
                                    .setTransaction(transaction)
                                    .setNonce(nonce)
                .build();

        broadcast(reqMsg);

        // Start the timer
        long startTime = System.currentTimeMillis();

        // initialize list of replies 
        List<Block> replies = new ArrayList<>();
        perfectLink.clearQueue();

        // Wait for f+1 equal CLIENT_REPLY messages.
        while (System.currentTimeMillis() - startTime < timeout) {
            Message reply = perfectLink.deliver();
            if (reply.getType() == Message.Type.CLIENT_REPLY) {
                Logger.log(LogLevel.INFO, "Received reply from " + reply.getSenderId() + ": " + reply.getBlock());
                
                replies.add(reply.getBlock());
                // Check if there are f+1 equal replies and that are the same as request
                if (replies.size() >= f + 1) {
                    int count = 0;
                    for (int i = 0; i < replies.size(); i++)
                        if (replies.get(i).equals(transaction.toString()))
                            count++;
                    if (count >= f + 1)
                        nonce++;
                        confirmedTransfers++;
                        return transaction.toString();
                }
            }

        }
        return null;
    }
}
