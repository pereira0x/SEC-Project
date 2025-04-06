package depchain.blockchain;

import depchain.account.SmartAccount;
import depchain.blockchain.Transaction.TransactionType;
import depchain.blockchain.block.Block;
import depchain.consensus.ConsensusInstance;
import depchain.consensus.TimestampValuePair;
import depchain.network.Message;
import depchain.network.Message.ReplyType;
import depchain.network.Message.Type;
import depchain.network.PerfectLink;
import depchain.utils.Config;
import depchain.utils.CryptoUtil;
import depchain.utils.EVMUtils;
import depchain.utils.Logger;
import depchain.utils.Logger.LogLevel;
import io.github.cdimascio.dotenv.Dotenv;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class BlockchainMember {
    private final int memberId;
    private final int memberPort;
    private final int leaderId; // Static leader ID.
    private String behavior;
    private final List<Integer> allProcessIds; // All node IDs (no clients).
    private PerfectLink perfectLink;
    private ConcurrentMap<Integer, ConsensusInstance> consensusInstances = new ConcurrentHashMap<>();
    private final int f; // Maximum number of Byzantine faults allowed.
    private int epochNumber = 0;
    private Blockchain blockchain;

    private final List<Transaction> pendingTransactions = new java.util.concurrent.CopyOnWriteArrayList<>();

    private final int transactions_threshold = Dotenv.load().get("TRANSACTIONS_THRESHOLD") != null
            ? Integer.parseInt(Dotenv.load().get("TRANSACTIONS_THRESHOLD"))
            : 2;

    public BlockchainMember(int memberId, int memberPort, int leaderId, int f) {
        this.memberId = memberId;
        this.memberPort = memberPort;
        this.leaderId = leaderId;
        this.allProcessIds = Arrays.asList(1, 2, 3, 4);
        this.f = f;

        Dotenv dotenv = Dotenv.load();
        // Load configuration from config.txt and resources folder.
        String configFilePath = dotenv.get("CONFIG_FILE_PATH");
        String keysFolderPath = dotenv.get("KEYS_FOLDER_PATH");

        if (configFilePath == null || keysFolderPath == null) {
            Logger.log(LogLevel.ERROR, "Environment variables CONFIG_FILE_PATH or KEYS_FOLDER_PATH are not set.");
            return;
        }

        try {
            Config.loadConfiguration(configFilePath, keysFolderPath);
        } catch (Exception e) {
            Logger.log(LogLevel.ERROR, "Failed to load configuration: " + e.getMessage());
            return;
        }
        String behavior = Config.processBehaviors.get(memberId);
        this.behavior = behavior != null ? behavior : "correct";

        try {
            this.blockchain = new Blockchain(this.memberId, Config.getClientPublicKeys());
        } catch (IOException e) {
            Logger.log(LogLevel.ERROR, "Failed to initialize Blockchain: " + e.getMessage());
        }

        PerfectLink pl;
        try {
            pl = new PerfectLink(memberId, memberPort, Config.processAddresses, Config.getPrivateKey(memberId),
                    Config.publicKeys);
        } catch (Exception e) {
            Logger.log(LogLevel.ERROR, "Failed to create PerfectLink: " + e.getMessage());
            return;
        }
        this.perfectLink = pl;

        startMessageHandler();
    }

    public static void main(String[] args) throws Exception {
        // Usage: java BlockchainMember <memberId> <memberPort>
        if (args.length < 2) {
            Logger.log(LogLevel.ERROR, "Usage: BlockchainMember <processId> <port>");
            return;
        }

        int memberId = Integer.parseInt(args[0]);
        int memberPort = Integer.parseInt(args[1]);
        int leaderId = 1; // assume process 1 is leader

        // Assume maximum Byzantine faults f = 1 for 4 processes.
        BlockchainMember blockchainMember = new BlockchainMember(memberId, memberPort, leaderId, 1);
        Logger.log(LogLevel.INFO, "BlockchainMember " + memberId + " started on port " + memberPort);
    }

    // Message handler loop.
    private void startMessageHandler() {
        while (true) {
            try {
                Message msg = perfectLink.deliver(); // waits for new elements to be added to the
                                                     // linked blocking queue

                // If a CLIENT_REQUEST is received and this node is leader,

                switch (this.behavior) {
                    case "ignoreMessages":
                        // Byzantine behavior: ignore all messages
                        Logger.log(LogLevel.WARNING, "Byzantine behavior: dropping message.");
                        continue;
                    default:
                        break;
                }

                new Thread(() -> {
                    // Get the consensus instance for the respective client
                    ConsensusInstance consensusInstance = consensusInstances.get(msg.getClientId());

                    Block decidedBlock = null;
                    if (msg.getType() == Message.Type.CLIENT_REQUEST) {
                        switch (msg.getRequestType()) {
                            case TRANSFER_ISTCOIN:
                            case TRANSFER_DEPCOIN:
                            case ADD_BLACKLIST:
                            case REMOVE_BLACKLIST:
                            case TRANSFER_FROM_IST_COIN:
                            case APPROVE:
                                ConsensusInstanceAndBlock result = verifyTransactionAndAddOrProposeBlock(msg,
                                        consensusInstance, decidedBlock);
                                consensusInstance = result.getConsensusInstance();
                                decidedBlock = result.getDecidedBlock();

                                break;

                            // this cases are empty and without break -> all will be
                            // processReadOperation
                            case GET_DEPCOIN_BALANCE:
                            case GET_ISTCOIN_BALANCE:
                            case ALLOWANCE:
                            case IS_BLACKLISTED:
                                processReadOperation(msg);
                                break;

                            default:

                                Logger.log(LogLevel.ERROR, "Unknown request type: " + msg.getRequestType());
                                break;
                        }

                    } else {
                        // For consensus messages, dispatch to the corresponding consensus
                        // instance.
                        if (consensusInstance == null) {
                            // instantiate a new consensus instance
                            consensusInstance = new ConsensusInstance(memberId, leaderId, allProcessIds, perfectLink,
                                    msg.getEpoch(), f, null, msg.getClientId());
                            consensusInstances.put(msg.getClientId(), consensusInstance);
                        }

                        consensusInstance.processMessage(msg);

                        decidedBlock = consensusInstance.getDecidedBlock();
                    }

                    if (decidedBlock != null) {
                        try {
                            // Block was decided now we have to process the transactions
                            Block processedBlock = processBlockTransactions(decidedBlock);

                            // Append the decided value to the blockchain.
                            this.blockchain.addBlock(processedBlock);
                            Logger.log(LogLevel.WARNING, "Blockchain updated: " + this.blockchain.getHashesChain());

                            consensusInstance = null;
                            consensusInstances.remove(msg.getClientId());
                            synchronized (pendingTransactions) {
                                pendingTransactions.clear();
                            }

                            // Send CLIENT_REPLY to the client.
                            InetSocketAddress clientAddr = Config.processAddresses.get(msg.getClientId());

                            if (clientAddr != null) {
                                // TODO: EPOCH NUMBER MUST BE A NEW ONE
                                // TODO: IMPLEMENT CLIENT IDS PROPERLY

                                // broadcast the reply to all clients
                                for (int clientId : Config.getClientIds()) {
                                    Message reply = new Message.MessageBuilder(Type.CLIENT_REPLY, msg.getEpoch(),
                                            memberId, clientId).setBlock(processedBlock).setReplyType(ReplyType.BLOCK)
                                                    .build();
                                    perfectLink.send(clientId, reply);
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    // Check if the consensus was aborted
                    if (consensusInstance != null && consensusInstance.isAborted()) {
                        Logger.log(LogLevel.ERROR, "Consensus aborted.");
                        consensusInstance = null;
                        consensusInstances.remove(msg.getClientId());
                        synchronized (pendingTransactions) {
                            pendingTransactions.clear();
                        }
                    }

                    // Print blockchain transactions
                    // Logger.log(LogLevel.INFO, "Blockchain transactions: " +
                    // this.blockchain.getMostRecentBlock().getTransactions());
                }).start();
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public ConsensusInstanceAndBlock verifyTransactionAndAddOrProposeBlock(Message msg,
            ConsensusInstance consensusInstance, Block decidedBlock) {
        // Check the signature of the transaction
        try {
            if (!checkTransactionSignature(msg.getTransaction(), msg.getClientId())) {
                Logger.log(LogLevel.ERROR, "Invalid transaction signature.");
                return new ConsensusInstanceAndBlock(consensusInstance, decidedBlock);
            }
        } catch (Exception e) {
            Logger.log(LogLevel.ERROR, "Failed to check transaction signature: " + e.getMessage());
            return new ConsensusInstanceAndBlock(consensusInstance, decidedBlock);
        }

        // Add the transaction to the pending transactions list.
        synchronized (pendingTransactions) {
            pendingTransactions.add(msg.getTransaction());
        }

        if (pendingTransactions.size() >= transactions_threshold) {

            String lastBlockHash = blockchain.getMostRecentBlock().getBlockHash();
            ArrayList<Transaction> transactions = new ArrayList<>(pendingTransactions);
            Block block = new Block.BlockBuilder(transactions, lastBlockHash).build();

            String blockHash = null;
            try {
                blockHash = EVMUtils.generateBlockHash(block);
                block.setBlockHash(blockHash);

            } catch (NoSuchAlgorithmException e) {
                Logger.log(LogLevel.ERROR, "Failed to generate block hash: " + e.getMessage());
                return new ConsensusInstanceAndBlock(consensusInstance, decidedBlock);
            }

            if (consensusInstance != null) {
                consensusInstance.setBlockProposed(block);
            } else {
                consensusInstance = new ConsensusInstance(memberId, leaderId, allProcessIds, perfectLink, epochNumber++,
                        f, block, msg.getClientId());
                consensusInstances.put(msg.getClientId(), consensusInstance);
            }

            if (memberId == leaderId) {
                consensusInstance.setBlockchainMostRecentWrite(new TimestampValuePair(0, block));
                Logger.log(LogLevel.DEBUG, "Waiting for consensus decision...");
                decidedBlock = consensusInstance.decideBlock();
            }

        } else {
            Logger.log(LogLevel.DEBUG, "Added transaction to pending transactions.");
        }

        return new ConsensusInstanceAndBlock(consensusInstance, decidedBlock);
    }

    public static class ConsensusInstanceAndBlock {
        private final ConsensusInstance consensusInstance;
        private final Block decidedBlock;

        public ConsensusInstanceAndBlock(ConsensusInstance consensusInstance, Block decidedBlock) {
            this.consensusInstance = consensusInstance;
            this.decidedBlock = decidedBlock;
        }

        public ConsensusInstance getConsensusInstance() {
            return consensusInstance;
        }

        public Block getDecidedBlock() {
            return decidedBlock;
        }
    }

    public ArrayList<Block> getBlockchain() {
        return this.blockchain.getChain();
    }

    public int getMemberId() {
        return this.memberId;
    }

    public Block processBlockTransactions(Block block) {
        // No need to create a copy, modify the original block directly
        ArrayList<Transaction> updatedTransactions = new ArrayList<>();

        // Process transactions in the block
        for (Transaction transaction : block.getTransactions()) {
            switch (transaction.getType()) {
                case TRANSFER_DEPCOIN:
                case TRANSFER_IST_COIN:
                case APPROVE:
                case TRANSFER_FROM_IST_COIN:
                case REMOVE_BLACKLIST:
                case ADD_BLACKLIST:
                    updatedTransactions.add(processBlockOperation(transaction, transaction.getType()));
                    break;
                default:
                    Logger.log(LogLevel.WARNING, "Unsupported transaction type: " + transaction.getType());
                    break;
            }
        }

        // After processing, set the block's transactions to the updated ones
        block.setTransactions(updatedTransactions);

        return block;
    }

    public Transaction processDepCoinTransfer(Transaction t, String senderAddress, String targetAddress) {

        // cannot send to yourself
        if (senderAddress.equals(targetAddress)) {
            Logger.log(LogLevel.ERROR, "Sender and recipient are the same: " + senderAddress);
            t.setStatus(Transaction.TransactionStatus.REJECTED);
            return t;
        }

        Long amount = t.getAmount();

        // Amount must be positive
        if (amount <= 0) {
            Logger.log(LogLevel.ERROR, "Invalid amount: " + amount);
            t.setStatus(Transaction.TransactionStatus.REJECTED);
            return t;
        }

        // Update sender's balance
        if (blockchain.existsAccount(senderAddress) && blockchain.existsAccount(targetAddress)
                && blockchain.getBalance(senderAddress) >= amount) {

            // Subtract from sender's balance
            Long newSenderBalance = blockchain.getBalance(senderAddress) - amount;
            blockchain.updateAccountBalance(senderAddress, newSenderBalance);

            // Add to recipient's balance
            Long newRecipientBalance = blockchain.getBalance(targetAddress) + amount;
            blockchain.updateAccountBalance(targetAddress, newRecipientBalance);

            // Mark the transaction as confirmed
            Logger.log(LogLevel.INFO, "Transaction processed: " + t);
            t.setStatus(Transaction.TransactionStatus.CONFIRMED);
        } else {
            t.setStatus(Transaction.TransactionStatus.REJECTED);

            // If transaction fails, mark it as rejected
            Logger.log(LogLevel.ERROR, "Transaction failed: " + t.getNonce());

            // print conditions
            if (!blockchain.existsAccount(senderAddress)) {
                Logger.log(LogLevel.ERROR, "Sender not found: " + senderAddress);
            } else if (!blockchain.existsAccount(targetAddress)) {
                Logger.log(LogLevel.ERROR, "Recipient not found: " + targetAddress);
            } else if (blockchain.getBalance(senderAddress) < amount) {
                Logger.log(LogLevel.ERROR, "Insufficient balance for sender: " + senderAddress + " (Balance: "
                        + blockchain.getBalance(senderAddress) + ", Amount: " + amount + ")");
            }
        }

        // Return the updated transaction object
        return t;
    }

    public Transaction processBlockOperation(Transaction t, TransactionType type) {
        // Process transactions in the block
        String senderAddress;
        String targetAddress;
        SmartAccount smartAccount = this.blockchain.getSmartAccount();

        try {
            senderAddress = EVMUtils.getEOAccountAddress(Config.getPublicKey(Integer.parseInt(t.getSender())));
            targetAddress = t.getRecipient();
        } catch (NoSuchAlgorithmException e) {
            Logger.log(LogLevel.ERROR, "Failed to get EOAccountAddress: " + e.getMessage());
            t.setStatus(Transaction.TransactionStatus.REJECTED);
            return t;
        } catch (RuntimeException e) {
            Logger.log(LogLevel.ERROR, "Failed to get public key: " + e.getMessage());
            t.setStatus(Transaction.TransactionStatus.REJECTED);
            return t;
        }

        switch (type) {
            case TRANSFER_DEPCOIN:
                t = processDepCoinTransfer(t, senderAddress, targetAddress);
                break;
            case TRANSFER_IST_COIN:
                // Transfer IST Coin
                BigInteger amount = BigInteger.valueOf(t.getAmount());
                boolean validTransaction = smartAccount.transfer(senderAddress, targetAddress, amount);
                if (validTransaction) {
                    t.setStatus(Transaction.TransactionStatus.CONFIRMED);
                    Logger.log(LogLevel.INFO, "Transaction processed: " + t);
                } else {
                    t.setStatus(Transaction.TransactionStatus.REJECTED);
                    Logger.log(LogLevel.ERROR, "Transaction failed: " + t);
                }

                break;
            case ADD_BLACKLIST:
                // Add to blacklist
                boolean isBlacklisted = smartAccount.addToBlacklist(senderAddress, targetAddress);
                if (isBlacklisted) {
                    t.setStatus(Transaction.TransactionStatus.CONFIRMED);
                    Logger.log(LogLevel.INFO, "Transaction processed: " + t);
                } else {
                    t.setStatus(Transaction.TransactionStatus.REJECTED);
                    Logger.log(LogLevel.ERROR, "Transaction failed: " + t);
                }
                break;
            case APPROVE:
                // Approve transaction
                boolean approved = smartAccount.approve(senderAddress, targetAddress,
                        BigInteger.valueOf(t.getAmount()));
                if (approved) {
                    t.setStatus(Transaction.TransactionStatus.CONFIRMED);
                    Logger.log(LogLevel.INFO, "Transaction processed: " + t);
                } else {
                    t.setStatus(Transaction.TransactionStatus.REJECTED);
                    Logger.log(LogLevel.ERROR, "Transaction failed: " + t);
                }
                break;
            case REMOVE_BLACKLIST:
                // Remove from blacklist
                boolean removed = smartAccount.removeFromBlacklist(senderAddress, targetAddress);
                if (removed) {
                    t.setStatus(Transaction.TransactionStatus.CONFIRMED);
                    Logger.log(LogLevel.INFO, "Transaction processed: " + t);
                } else {
                    t.setStatus(Transaction.TransactionStatus.REJECTED);
                    Logger.log(LogLevel.ERROR, "Transaction failed: " + t);
                }
                break;
            case TRANSFER_FROM_IST_COIN:
                // Transfer from IST Coin
                BigInteger amountFrom = BigInteger.valueOf(t.getAmount());
                String spenderAddress = t.getSpender();
                boolean validTransferFrom = smartAccount.transferFrom(senderAddress, spenderAddress, targetAddress,
                        amountFrom);
                if (validTransferFrom) {
                    t.setStatus(Transaction.TransactionStatus.CONFIRMED);
                    Logger.log(LogLevel.INFO, "Transaction processed: " + t);
                } else {
                    t.setStatus(Transaction.TransactionStatus.REJECTED);
                    Logger.log(LogLevel.ERROR, "Transaction failed: " + t);
                }
                break;
            default:
                Logger.log(LogLevel.ERROR, "Unsupported transaction type: " + type);
                t.setStatus(Transaction.TransactionStatus.REJECTED);
                break;
        }

        return t;
    }

    public void processReadOperation(Message msg) {
        SmartAccount smartAccount = this.blockchain.getSmartAccount();
        Transaction tx = msg.getTransaction();
        String senderAddress;
        String targetAddress;
        String replyValue = "";
        ReplyType replyType = ReplyType.VALUE;

        try {
            senderAddress = EVMUtils.getEOAccountAddress(Config.getPublicKey(Integer.parseInt(tx.getSender())));
            targetAddress = tx.getRecipient();

            switch (msg.getRequestType()) {
                case GET_DEPCOIN_BALANCE:
                    // Get the balance of the sender
                    Long balanceDep = blockchain.getBalance(senderAddress);
                    replyValue = balanceDep.toString();
                    break;
                case GET_ISTCOIN_BALANCE:
                    BigInteger balanceIST = smartAccount.balanceOf(senderAddress, targetAddress);
                    replyValue = balanceIST.toString();
                    break;

                case IS_BLACKLISTED:
                    boolean isBlacklisted = smartAccount.isBlacklisted(senderAddress, targetAddress);
                    replyValue = Boolean.toString(isBlacklisted);
                    break;

                case ALLOWANCE:
                    // Check if the spender is allowed to spend on behalf of the source
                    String spender = tx.getSpender();
                    BigInteger allowance = smartAccount.allowance(senderAddress, targetAddress, spender);
                    replyValue = allowance.toString();
                    break;

                default:
                    Logger.log(LogLevel.ERROR, "Unknown read operation request type: " + msg.getRequestType());
                    return;
            }
        } catch (NoSuchAlgorithmException e) {
            Logger.log(LogLevel.ERROR, "Failed to process read operation: " + e.getMessage());
            return;
        }

        // Send CLIENT_REPLY to the client.
        Message reply = new Message.MessageBuilder(Type.CLIENT_REPLY, msg.getEpoch(), memberId, msg.getClientId())
                .setBlock(null).setReplyType(replyType).setReplyValue(replyValue).build();
        try {
            Logger.log(LogLevel.DEBUG, "Reply: " + reply);
            perfectLink.send(msg.getSenderId(), reply);
        } catch (Exception e) {
            Logger.log(LogLevel.ERROR, "Failed to send reply: " + e.getMessage());
        }
    }

    public boolean checkTransactionSignature(Transaction transaction, int clientId) throws Exception {
        // Check if the transaction signature is valid
        byte[] transactionBytes = transaction.toByteArray();
        byte[] signature = transaction.getSignature();
        PublicKey publicKey = Config.getPublicKey(clientId);

        // System.out.println("------------- signature: " + new
        // ByteArrayWrapper(signature));
        return CryptoUtil.verify(transactionBytes, signature, publicKey);
    }
}
