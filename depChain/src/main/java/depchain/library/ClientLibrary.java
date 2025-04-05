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
    private final long timeout = 60000;

    // Map to store waiting transactions and their notifier objects
    private final Map<Long, PendingTransactionStatus> pendingTransactions = new ConcurrentHashMap<>();


    // I want to have a pendingReqadRequest that stores 1 request at a time
    // Single pending read request
    private final Object pendingReadRequestLock = new Object();
    private String readValue;

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
                    if(reply.getReplyType() == Message.ReplyType.BLOCK) {

                        System.out.println("Received reply: " + reply);
                        Block appendedBlock = reply.getBlock();
                        if (appendedBlock != null) {
                            for (Transaction tx : appendedBlock.getTransactions()) {
                                Long txNonce = tx.getNonce();
                                if (pendingTransactions.containsKey(txNonce)) {
                                    PendingTransactionStatus pendingTx = pendingTransactions.get(txNonce);
                                    synchronized (pendingTx.lock) {
                                        pendingTx.status = tx.getStatus(); // Update status
                                        pendingTx.lock.notify();
                                    }
                                }
                            }
                        }
                    }
                    else if (reply.getReplyType() == Message.ReplyType.VALUE) {
                        this.readValue = reply.getReplyValue();
                        synchronized (pendingReadRequestLock) {
                            pendingReadRequestLock.notify(); // Notify the waiting thread
                        }
                       

                    }
                }
            } catch (Exception e) {
                Logger.log(LogLevel.ERROR, "Error in listening thread: " + e.getMessage());
            }
        }
    }

    public String addToBlackList(String targetAddress) throws Exception {
      return performBlockOperation(Transaction.TransactionType.ADD_BLACKLIST,
                                   Message.RequestType.ADD_BLACKLIST, 0L,
                                   targetAddress);
    }

    public String transferDepcoin(String targetAddress, Long amount) throws Exception {
      return performBlockOperation(Transaction.TransactionType.TRANSFER_DEPCOIN, Message.RequestType.TRANSFER_DEPCOIN, amount, targetAddress);
    }

    public String transferISTCoin(String targetAddress, Long amount) throws Exception {
      return performBlockOperation(Transaction.TransactionType.TRANSFER_IST_COIN, Message.RequestType.TRANSFER_ISTCOIN, amount, targetAddress);
    }

    public String performBlockOperation(Transaction.TransactionType transactionType,
                             Message.RequestType requestType, Long amount, String... args)
            throws Exception {
        String targetAddress = args[0];
        Transaction transaction =
                new Transaction.TransactionBuilder()
                        .setSender(String.valueOf(this.clientId))
                        .setRecipient(String.valueOf(targetAddress))
                        .setAmount(amount)
                        .setNonce(CryptoUtil.generateNonce())
                        .setType(transactionType)
                        .setStatus(Transaction.TransactionStatus.PENDING)
                        .build();
        // Convert the transaction to bytes and sign it
        byte[] transactionBytes = transaction.toByteArray();
        byte[] signature =
                CryptoUtil.sign(transactionBytes, perfectLink.getPrivateKey());
        ByteArrayWrapper sig = new ByteArrayWrapper(signature);
        transaction.setSignature(sig);
        // Create message
        Message reqMsg = new Message
                .MessageBuilder(Message.Type.CLIENT_REQUEST, 0,
                clientId, clientId)
                .setTransaction(transaction)
                .setRequestType(requestType)
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

      public String getDepCoinBalance(String targetAddress) throws Exception {
        return performReadOperation(Transaction.TransactionType.GET_DEPCOIN_BALANCE, Message.RequestType.GET_DEPCOIN_BALANCE, targetAddress);        
    }

    public String isBlacklisted(String targetAddress) throws Exception {
      return performReadOperation(
          Transaction.TransactionType.IS_BLACKLISTED,
          Message.RequestType.IS_BLACKLISTED, targetAddress);
    }

    public String getISTCoinBalance(String targetAddress) throws Exception {
      return performReadOperation(Transaction.TransactionType.GET_ISTCOIN_BALANCE, Message.RequestType.GET_ISTCOIN_BALANCE, targetAddress);
    }

    public String allowance(String source, String spender) throws Exception {
        return performReadOperation(Transaction.TransactionType.ALLOWANCE, Message.RequestType.ALLOWANCE, source, spender);
    }


    public String
        performReadOperation(Transaction.TransactionType transactionType,
                             Message.RequestType requestType, String... args)
            throws Exception {
      String targetAddress = args[0];
      String spender = null;
      if (args.length > 1) {
        spender = args[1];
      }
      Transaction transaction =
          new Transaction.TransactionBuilder()
              .setSender(String.valueOf(this.clientId))
              .setRecipient(String.valueOf(targetAddress))
              .setAmount(0L)
              .setNonce(CryptoUtil.generateNonce())
              .setType(transactionType)
              .setStatus(Transaction.TransactionStatus.PENDING)
              .setSpender(spender) // null if not needed
              .build();
      // Convert the transaction to bytes and sign it
      byte[] transactionBytes = transaction.toByteArray();
      byte[] signature =
          CryptoUtil.sign(transactionBytes, perfectLink.getPrivateKey());
      ByteArrayWrapper sig = new ByteArrayWrapper(signature);
      transaction.setSignature(sig);
      // Create message
      Message reqMsg = new Message
                           .MessageBuilder(Message.Type.CLIENT_REQUEST, 0,
                                           clientId, clientId)
                           .setTransaction(transaction)
                           .setRequestType(requestType)
                           .setNonce(nonce)
                           .build();
      // Register the transaction as pending
      PendingTransactionStatus pendingStatus = new PendingTransactionStatus();
      pendingTransactions.put(transaction.getNonce(), pendingStatus);
      // Send the transaction
      broadcast(reqMsg);
      // Wait for the reply
      nonce++;
      synchronized (pendingReadRequestLock) {
        try {
          pendingReadRequestLock.wait(timeout);

        } catch (InterruptedException e) {
          this.readValue = null; // Reset readValue on interruption
          Logger.log(LogLevel.ERROR,
                     "Error waiting for reply: " + e.getMessage());
        }
      }
      return readValue;
    }

    public void broadcast(Message msg) throws Exception {
        for (int nodeId : nodeIds) {
            perfectLink.send(nodeId, msg);
        }
    }

    public void waitforTransactionConfirmation(Transaction transaction, PendingTransactionStatus pendingStatus) {
        int requiredConfirmations = f + 1;
        int confirmations = 0;

        synchronized (pendingStatus.lock) {
            try {
                long startTime = System.currentTimeMillis();
                while (confirmations < requiredConfirmations) {
                    long remainingTime = timeout - (System.currentTimeMillis() - startTime);
                    if (remainingTime <= 0) {
                        break; // Exit if timeout is reached
                    }

                    // TODO: ADD A TIMEOUT ??? Do we want that?
                    pendingStatus.lock.wait();

                    // Check if this was a real confirmation
                    if (pendingTransactions.containsKey(transaction.getNonce())) {
                        confirmations++;
                    }
                }
            } catch (InterruptedException e) {
                Logger.log(LogLevel.ERROR, "Confirmation thread interrupted: " + e.getMessage());
            }
        }

        // Remove transaction from pending
        pendingTransactions.remove(transaction.getNonce());

        if (confirmations < requiredConfirmations) {
            Logger.log(LogLevel.ERROR, "Transaction " + transaction.getNonce() + " not confirmed in time.");
            return;
        }

        // Log the final status
        Logger.log(LogLevel.INFO, "Transaction " + transaction.getNonce() + " final status: " + pendingStatus.status);
    }

    // Class to store pending transaction status
    private static class PendingTransactionStatus {
        private final Object lock = new Object();
        private Transaction.TransactionStatus status = Transaction.TransactionStatus.PENDING;
    }
    }
