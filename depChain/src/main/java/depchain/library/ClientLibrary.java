package depchain.library;

import depchain.blockchain.Transaction;
import depchain.blockchain.block.Block;
import depchain.network.Message;
import depchain.network.PerfectLink;
import depchain.utils.ByteArrayWrapper;
import depchain.utils.CryptoUtil;
import depchain.utils.Logger;
import depchain.utils.Logger.LogLevel;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ClientLibrary {
    private final PerfectLink perfectLink;
    private final List<Integer> nodeIds;
    private final int clientId;
    private final AtomicInteger nonce = new AtomicInteger(0);
    private final int f;
    private final long timeout = 60000;

    // Thread-safe storage for pending transactions
    private final Map<Long, PendingTransactionStatus> pendingTransactions = new ConcurrentHashMap<>();

    // Thread-safe handling of read requests
    private final Object pendingReadRequestLock = new Object();
    private volatile String readValue;

    public ClientLibrary(PerfectLink perfectLink, List<Integer> nodeIds, int clientId, int f) {
        this.perfectLink = perfectLink;
        this.nodeIds = nodeIds;
        this.clientId = clientId;
        this.f = f;

        new Thread(this::listenForReplies).start();
    }

    private void listenForReplies() {
        while (true) {
            try {
                Message reply = perfectLink.deliver();
                if (reply.getType() != Message.Type.CLIENT_REPLY) {
                    continue;
                }

                handleReplyMessage(reply);
            } catch (Exception e) {
                Logger.log(LogLevel.ERROR, "Error in listening thread: " + e.getMessage());
            }
        }
    }

    private void handleReplyMessage(Message reply) {
        if (reply.getReplyType() == Message.ReplyType.BLOCK) {
            handleBlockReply(reply);
        } else if (reply.getReplyType() == Message.ReplyType.VALUE) {
            handleValueReply(reply);
        }
    }

    private void handleBlockReply(Message reply) {
        Logger.log(LogLevel.DEBUG, "Received block reply");
        Block appendedBlock = reply.getBlock();

        if (appendedBlock == null) {
            return;
        }

        for (Transaction tx : appendedBlock.getTransactions()) {
            Long txNonce = tx.getNonce();
            PendingTransactionStatus pendingTx = pendingTransactions.get(txNonce);

            if (pendingTx != null) {
                synchronized (pendingTx.lock) {
                    Logger.log(LogLevel.DEBUG,
                            "Received transaction status for nonce: " + txNonce);
                    pendingTx.status.add(tx.getStatus());
                    pendingTx.lock.notify();
                }
            }
        }
    }

    private void handleValueReply(Message reply) {
        this.readValue = reply.getReplyValue();
        synchronized (pendingReadRequestLock) {
            pendingReadRequestLock.notify();
        }
    }

    // Transaction operations
    public String addToBlackList(String targetAddress) throws Exception {
        return performBlockOperation(Transaction.TransactionType.ADD_BLACKLIST, Message.RequestType.ADD_BLACKLIST, 0L,
                targetAddress);
    }

    public String removeFromBlackList(String targetAddress) throws Exception {
        return performBlockOperation(Transaction.TransactionType.REMOVE_BLACKLIST, Message.RequestType.REMOVE_BLACKLIST,
                0L, targetAddress);
    }

    public String approve(String targetAddress, Long amount) throws Exception {
        return performBlockOperation(Transaction.TransactionType.APPROVE, Message.RequestType.APPROVE, amount,
                targetAddress);
    }

    public String transferDepcoin(String targetAddress, Long amount) throws Exception {
        return performBlockOperation(Transaction.TransactionType.TRANSFER_DEPCOIN, Message.RequestType.TRANSFER_DEPCOIN,
                amount, targetAddress);
    }

    public String transferISTCoin(String targetAddress, Long amount) throws Exception {
        return performBlockOperation(Transaction.TransactionType.TRANSFER_IST_COIN,
                Message.RequestType.TRANSFER_ISTCOIN, amount, targetAddress);
    }

    public String transferFromISTCoin(String spenderAddress, String targetAddress, Long amount) throws Exception {
        return performBlockOperation(Transaction.TransactionType.TRANSFER_FROM_IST_COIN,
                Message.RequestType.TRANSFER_FROM_IST_COIN, amount, targetAddress, spenderAddress);
    }

    private String performBlockOperation(Transaction.TransactionType transactionType, Message.RequestType requestType,
            Long amount, String... args) throws Exception {

        Transaction transaction = buildTransaction(transactionType, amount, args);
        Message reqMsg = buildRequestMessage(requestType, transaction);

        PendingTransactionStatus pendingStatus = new PendingTransactionStatus();
        pendingTransactions.put(transaction.getNonce(), pendingStatus);

        broadcast(reqMsg);
        startConfirmationThread(transaction, pendingStatus);

        nonce.incrementAndGet();
        return "Transaction Sent: " + transaction.getNonce();
    }

    // Read operations
    public String getDepCoinBalance(String targetAddress) throws Exception {
        return performReadOperation(Transaction.TransactionType.GET_DEPCOIN_BALANCE,
                Message.RequestType.GET_DEPCOIN_BALANCE, targetAddress);
    }

    public String isBlacklisted(String targetAddress) throws Exception {
        return performReadOperation(Transaction.TransactionType.IS_BLACKLISTED, Message.RequestType.IS_BLACKLISTED,
                targetAddress);
    }

    public String getISTCoinBalance(String targetAddress) throws Exception {
        return performReadOperation(Transaction.TransactionType.GET_ISTCOIN_BALANCE,
                Message.RequestType.GET_ISTCOIN_BALANCE, targetAddress);
    }

    public String allowance(String source, String spender) throws Exception {
        return performReadOperation(Transaction.TransactionType.ALLOWANCE, Message.RequestType.ALLOWANCE, source,
                spender);
    }

    private String performReadOperation(Transaction.TransactionType transactionType, Message.RequestType requestType,
            String... args) throws Exception {

        Transaction transaction = buildTransaction(transactionType, 0L, args);
        Message reqMsg = buildRequestMessage(requestType, transaction);

        PendingTransactionStatus pendingStatus = new PendingTransactionStatus();
        pendingTransactions.put(transaction.getNonce(), pendingStatus);

        broadcast(reqMsg);
        nonce.incrementAndGet();

        return waitForReadResponse();
    }

    private Transaction buildTransaction(Transaction.TransactionType transactionType, Long amount, String... args)
            throws Exception {

        String targetAddress = args[0];
        String spenderAddress = args.length > 1 ? args[1] : null;

        return new Transaction.TransactionBuilder().setSender(String.valueOf(this.clientId))
                .setRecipient(String.valueOf(targetAddress)).setAmount(amount).setNonce(CryptoUtil.generateNonce())
                .setType(transactionType).setSpender(spenderAddress).setStatus(Transaction.TransactionStatus.PENDING)
                .build();
    }

    private Message buildRequestMessage(Message.RequestType requestType, Transaction transaction) throws Exception {

        byte[] transactionBytes = transaction.toByteArray();
        byte[] signature = CryptoUtil.sign(transactionBytes, perfectLink.getPrivateKey());
        transaction.setSignature(new ByteArrayWrapper(signature));

        return new Message.MessageBuilder(Message.Type.CLIENT_REQUEST, 0, clientId)
                .setTransaction(transaction).setRequestType(requestType).setNonce(nonce.get()).build();
    }

    private String waitForReadResponse() {
        synchronized (pendingReadRequestLock) {
            try {
                pendingReadRequestLock.wait(timeout);
                return readValue;
            } catch (InterruptedException e) {
                Logger.log(LogLevel.ERROR, "Error waiting for reply: " + e.getMessage());
                return null;
            }
        }
    }

    private void startConfirmationThread(Transaction transaction, PendingTransactionStatus pendingStatus) {
        new Thread(() -> {
            waitForTransactionConfirmation(transaction, pendingStatus);
        }).start();
    }

    public void broadcast(Message msg) throws Exception {
        for (int nodeId : nodeIds) {
            perfectLink.send(nodeId, msg);
        }
    }

    private void waitForTransactionConfirmation(Transaction transaction, PendingTransactionStatus pendingStatus) {
        int requiredResponses = f + 1;
        int replies = 0;

        synchronized (pendingStatus.lock) {
            try {
                while (replies < 2*f + 1) {
                    pendingStatus.lock.wait();

                    if (pendingTransactions.containsKey(transaction.getNonce())) {
                        replies++;
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
                Logger.log(LogLevel.ERROR, "Confirmation thread interrupted: " + e.getMessage());
            }
        }

        pendingTransactions.remove(transaction.getNonce());

        Logger.log(LogLevel.INFO,
                "Transaction " + transaction.getNonce() + " final status: " + transaction.getStatus());
    }

    private static class PendingTransactionStatus {
        private final Object lock = new Object();
        private List<Transaction.TransactionStatus> status = new ArrayList<>();
    }
}
