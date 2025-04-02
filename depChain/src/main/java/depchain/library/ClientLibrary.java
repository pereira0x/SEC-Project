package depchain.library;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.web3j.crypto.WalletFile.Crypto;

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

    // Map to store waiting transactions and their notifier objects
    private final Map<Long, Object> pendingTransactions = new ConcurrentHashMap<>();

    public ClientLibrary(PerfectLink perfectLink, int leaderId, List<Integer> nodeIds, int clientId, int f) {
        this.perfectLink = perfectLink;
        this.leaderId = leaderId;
        this.nodeIds = nodeIds;
        this.clientId = clientId;
        this.f = f;

        // Start the listening thread
        new Thread(this::listenForReplies).start();
    }

    private void listenForReplies() {
        while (true) {
            try {
                Message reply = perfectLink.deliver();
                if (reply.getType() == Message.Type.CLIENT_REPLY) {
                    System.out.println("Received reply: " + reply);
                    Block appendedBlock = reply.getBlock();
                    if (appendedBlock != null) {
                        for (Transaction tx : appendedBlock.getTransactions()) {
                            Long txNonce = tx.getNonce();
                            if (pendingTransactions.containsKey(txNonce)) {
                                synchronized (pendingTransactions.get(txNonce)) {
                                    pendingTransactions.get(txNonce).notify();
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Logger.log(LogLevel.ERROR, "Error in listening thread: " + e.getMessage());
            }
        }
    }

    public String transferDepcoin(int recipientId, Long amount) throws Exception {
        // Create the transaction
        Transaction transaction = new Transaction.TransactionBuilder()
                .setSender(String.valueOf(this.clientId))
                .setRecipient(String.valueOf(recipientId))
                .setAmount(amount)
                .setNonce(CryptoUtil.generateNonce())
                .setType(Transaction.TransactionType.TRANSFER_DEPCOIN)
                .setStatus(Transaction.TransactionStatus.PENDING)
                .build();

        // Create message
        Message reqMsg = new Message.MessageBuilder(Message.Type.CLIENT_REQUEST, confirmedTransfers, clientId, clientId)
                .setTransaction(transaction)
                .setNonce(nonce)
                .build();

        // Register the transaction as pending
        Object lock = new Object();
        pendingTransactions.put(transaction.getNonce(), lock);

        // Send the transaction
        broadcast(reqMsg);

        // Schedule a separate thread to handle confirmations
        new Thread(() -> {
            waitforTransactionConfirmation(transaction, lock);
        }).start();
        // nonce must be updated after sending the transaction and not after receiving
        // the confirmation, since we want a client to be able to send multiple
        // transactions in a single block
        nonce++;
        return "Transaction Sent: " + transaction.getNonce();
    }

    public void broadcast(Message msg) throws Exception {
        for (int nodeId : nodeIds) {
            perfectLink.send(nodeId, msg);
        }
    }

    public void waitforTransactionConfirmation(Transaction transaction, Object lock) {
        int requiredConfirmations = f + 1;
        int confirmations = 0;
    
        synchronized (lock) {
            try {
                long startTime = System.currentTimeMillis();
                while (confirmations < requiredConfirmations) {
                    long remainingTime = timeout - (System.currentTimeMillis() - startTime);
                    if (remainingTime <= 0) {
                        break; // Exit if timeout is reached
                    }
                    
                    lock.wait(remainingTime);
    
                    // Check if this was a real confirmation (i.e., a real notify)
                    if (pendingTransactions.containsKey(transaction.getNonce())) {
                        confirmations++;
                    }
                }
            } catch (InterruptedException e) {
                Logger.log(LogLevel.ERROR, "Confirmation thread interrupted: " + e.getMessage());
            }
        }
    
        pendingTransactions.remove(transaction.getNonce());
    
        if (confirmations < requiredConfirmations) {
            Logger.log(LogLevel.ERROR, "Transaction " + transaction.getNonce() + " not confirmed in time.");
            return;
        }

        
        Logger.log(LogLevel.INFO, "Transaction Confirmed: " + transaction.getNonce());
    }
}
