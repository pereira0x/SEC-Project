package depchain.library;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import depchain.blockchain.Transaction;
import depchain.blockchain.block.Block;
import depchain.network.Message;
import depchain.network.PerfectLink;
import depchain.utils.CryptoUtil;
import depchain.utils.Logger;
import depchain.utils.Logger.LogLevel;
import depchain.utils.CryptoUtil;
import depchain.utils.ByteArrayWrapper;

public class ClientLibrary {
    private final PerfectLink perfectLink;
    private final int leaderId;
    private final List<Integer> nodeIds;
    private final int clientId;
    private int nonce = 0;
    private final int f;
    private int confirmedTransfers = 0;

    // Map to store waiting transactions and their notifier objects
    private final Map<Long, PendingTransactionStatus> pendingTransactions = new ConcurrentHashMap<>();

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
                    Transaction tr = reply.getTransaction();
                    if (tr != null) {
                        Long txNonce = tr.getNonce();
                        Logger.log(LogLevel.DEBUG, "Received reply for transaction: " + txNonce);
                        if (pendingTransactions.containsKey(txNonce)) {
                            PendingTransactionStatus pendingTx = pendingTransactions.get(txNonce);
                            synchronized (pendingTx.lock) {
                                pendingTx.status.add(tr.getStatus());
                                pendingTx.lock.notify();
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

        // Convert the transaction to bytes and sign it
        byte[] transactionBytes = transaction.toByteArray();
        byte[] signature = CryptoUtil.sign(transactionBytes, perfectLink.getPrivateKey());
        ByteArrayWrapper sig = new ByteArrayWrapper(signature);
        transaction.setSignature(sig);
        // transaction.setSignature(new ByteArrayWrapper(new byte[0]));
        
        // Create message
        Message reqMsg = new Message.MessageBuilder(Message.Type.CLIENT_REQUEST, confirmedTransfers, clientId)
                .setTransaction(transaction)
                .setNonce(nonce)
                .build();

        // Register the transaction as pending
        PendingTransactionStatus pendingStatus = new PendingTransactionStatus();
        pendingTransactions.put(transaction.getNonce(), pendingStatus);

        // Send the transaction
        broadcast(reqMsg);

        // Start a separate thread to wait for confirmation
        new Thread(() -> {
            waitforTransactionConfirmation(transaction, pendingStatus);
        }).start();

        // nonce must be updated after sending the transaction
        nonce++;
        return "Transaction Sent: " + transaction.getNonce();
    }

    public void broadcast(Message msg) throws Exception {
        for (int nodeId : nodeIds) {
            perfectLink.send(nodeId, msg);
        }
    }

    public void waitforTransactionConfirmation(Transaction transaction, PendingTransactionStatus pendingStatus) {
        final int requiredResponses = f + 1;
        int replies = 0;

        synchronized (pendingStatus.lock) {
            try {
                while (replies < 3*f + 1) {
                    // TODO: ADD A TIMEOUT ??? Do we want that? No
                    pendingStatus.lock.wait();
                    replies++;

                    // Check if this was a real confirmation
                    if (pendingTransactions.containsKey(transaction.getNonce())) {
                        // check if there are f+1 equal statuses
                        int confirmedCount = 0;
                        int rejectedCount = 0;
                        for (Transaction.TransactionStatus status : pendingStatus.status) {
                            if (status == Transaction.TransactionStatus.CONFIRMED) {
                                confirmedCount++;
                            } else if (status == Transaction.TransactionStatus.REJECTED) {
                                rejectedCount++;
                            }
                        }
                        
                        if (confirmedCount >= requiredResponses) {
                            transaction.setStatus(Transaction.TransactionStatus.CONFIRMED);
                            break;
                        } else if (rejectedCount >= requiredResponses) {
                            transaction.setStatus(Transaction.TransactionStatus.REJECTED);
                            break;
                        }
                    }
                }
            } catch (InterruptedException e) {
                Logger.log(LogLevel.WARNING, "Confirmation thread interrupted: " + e.getMessage());
            }
        }

        // Remove transaction from pending
        pendingTransactions.remove(transaction.getNonce());

        // Log the final status
        Logger.log(LogLevel.INFO, "Transaction " + transaction.getNonce() + " final status: " + transaction.getStatus());
    }

    // Class to store pending transaction status
    private static class PendingTransactionStatus {
        private final Object lock = new Object();
        private List<Transaction.TransactionStatus> status = new ArrayList<>();
    }
}
