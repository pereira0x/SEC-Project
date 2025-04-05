package depchain.blockchain;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import depchain.blockchain.Transaction.TransactionType;
import depchain.blockchain.block.Block;
import depchain.consensus.ConsensusInstance;
import depchain.consensus.TimestampValuePair;
import depchain.network.Message;
import depchain.network.Message.Type;
import depchain.network.PerfectLink;
import depchain.utils.Config;
import depchain.utils.EVMUtils;
import depchain.utils.Logger;
import depchain.utils.Logger.LogLevel;
import depchain.utils.CryptoUtil;
import depchain.utils.ByteArrayWrapper;

import io.github.cdimascio.dotenv.Dotenv;

public class BlockchainMember {
    private final int memberId;
    private final int memberPort;
    private final int leaderId;                 // Static leader ID.
    private String behavior;
    private final List<Integer> allProcessIds;  // All node IDs (no clients).
    private PerfectLink perfectLink;
    private ConcurrentMap<String, ConsensusInstance> consensusInstances = new ConcurrentHashMap<>();
    private final int f;                        // Maximum number of Byzantine faults allowed.
    private int epochNumber = 0;
    private Blockchain blockchain;

    private final List<Transaction> pendingTransactions = new java.util.concurrent.CopyOnWriteArrayList<>();

    private final int transactions_threshold = Dotenv.load().get("TRANSACTIONS_THRESHOLD") != null ? Integer.parseInt(Dotenv.load().get("TRANSACTIONS_THRESHOLD")) : 2;

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
                Message msg = perfectLink.deliver(); // waits for new elements to be added to the linked blocking
                                                     // queue

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
                    ConsensusInstance consensusInstance = consensusInstances.get(msg.getBlockHash());

                    Block decidedBlock = null;
                    if (msg.getType() == Message.Type.CLIENT_REQUEST) {
                        // Check the signature of the transaction
                        try {
                            if (!checkTransactionSignature(msg.getTransaction(), msg.getSenderId())) {
                                Logger.log(LogLevel.ERROR, "Invalid transaction signature.");
                                return;
                            }
                        } catch (Exception e) {
                            Logger.log(LogLevel.ERROR, "Failed to check transaction signature: " + e.getMessage());
                            return;
                        }

                        // Add the transaction to the pending transactions list.
                        synchronized (pendingTransactions) {
                            pendingTransactions.add(msg.getTransaction());
                        }

                        if(pendingTransactions.size() >= transactions_threshold) {
    
                            String lastBlockHash = blockchain.getMostRecentBlock().getBlockHash();
                            ArrayList<Transaction> transactions = new ArrayList<>(pendingTransactions);
                            Block block = new Block.BlockBuilder(transactions, lastBlockHash)
                                .build();
                           
                            String blockHash = null;
                            try {
                                blockHash = EVMUtils.generateBlockHash(block);
                                block.setBlockHash(blockHash);
                                
                            } catch (NoSuchAlgorithmException e) {
                                Logger.log(LogLevel.ERROR, "Failed to generate block hash: " + e.getMessage());
                                return;
                            }
                            
                            if (consensusInstance != null) {
                                consensusInstance.setBlockProposed(block);
                            } else {
                                consensusInstance = new ConsensusInstance(memberId, leaderId, allProcessIds, perfectLink,
                                        epochNumber++, f, block);
                                consensusInstances.put(blockHash, consensusInstance);
                            }

                            if (memberId == leaderId) {                                
                                    consensusInstance.setBlockchainMostRecentWrite(new TimestampValuePair(0, block));
                                    Logger.log(LogLevel.DEBUG, "Waiting for consensus decision...");
                                    decidedBlock = consensusInstance.decideBlock();
                                }
                                
                        } else {
                            Logger.log(LogLevel.DEBUG, "Added transaction to pending transactions.");
                        }
                    
                    } else {
                        // For consensus messages, dispatch to the corresponding consensus instance.
                        if (consensusInstance == null) {
                            // instantiate a new consensus instance
                            consensusInstance = new ConsensusInstance(memberId, leaderId, allProcessIds,
                                    perfectLink, msg.getEpoch(), f, msg.getBlock());
                            consensusInstances.put(msg.getBlockHash(), consensusInstance);
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
                            consensusInstances.remove(msg.getBlockHash());
                            synchronized (pendingTransactions) {
                                pendingTransactions.clear();
                            }

                            // Send CLIENT_REPLY to all the clients
                            for (Transaction tx : processedBlock.getTransactions()) {
                                Message reply = new Message.MessageBuilder(Type.CLIENT_REPLY, msg.getEpoch(),
                                                                    memberId)
                                        .setTransaction(tx)
                                        .build();
                                for (int clientId : Config.getClientIds())
                                    perfectLink.send(clientId, reply);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    // Check if the consensus was aborted
                    if (consensusInstance != null && consensusInstance.isAborted()) {
                        Logger.log(LogLevel.ERROR, "Consensus aborted.");
                        consensusInstance = null;
                        consensusInstances.remove(msg.getBlockHash());
                        synchronized (pendingTransactions) {
                            pendingTransactions.clear();
                        }
                    }

                    // Print blockchain transactions
                    // Logger.log(LogLevel.INFO, "Blockchain transactions: " + this.blockchain.getMostRecentBlock().getTransactions());
                }).start();
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                e.printStackTrace();
            }
            
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
            if (transaction.getType() == TransactionType.TRANSFER_DEPCOIN) {
                updatedTransactions.add(processDepCoinTransaction(transaction, block));
            }
    
            // TODO: Add other transaction types here if needed
            
        }
    
        // After processing, set the block's transactions to the updated ones
        block.setTransactions(updatedTransactions);
    
        return block;
    }
    
    public Transaction processDepCoinTransaction(Transaction t, Block block) {
        // Process transactions in the block
        String sender;
        String recipient;
        
        try {
            sender = EVMUtils.getEOAccountAddress(Config.getPublicKey(Integer.parseInt(t.getSender())));
            recipient = EVMUtils.getEOAccountAddress(Config.getPublicKey(Integer.parseInt(t.getRecipient())));
        } catch (NoSuchAlgorithmException e) {
            Logger.log(LogLevel.ERROR, "Failed to get EOAccountAddress: " + e.getMessage());
            t.setStatus(Transaction.TransactionStatus.REJECTED);
            return t;
        } catch (RuntimeException e) {
            Logger.log(LogLevel.ERROR, "Failed to get public key: " + e.getMessage());
            t.setStatus(Transaction.TransactionStatus.REJECTED);
            return t;
        }

        // cannot send to yourself
        if (sender.equals(recipient)) {
            Logger.log(LogLevel.ERROR, "Sender and recipient are the same: " + sender);
            t.setStatus(Transaction.TransactionStatus.REJECTED);
            return t;
        }

        Long amount = t.getAmount();    
    
        // Update sender's balance
        if (blockchain.existsAccount(sender) && blockchain.existsAccount(recipient) &&
            blockchain.getBalance(sender) >= amount) {
            
            // Subtract from sender's balance
            Long newSenderBalance = blockchain.getBalance(sender) - amount;
            blockchain.updateAccountBalance(sender, newSenderBalance);
    
            // Add to recipient's balance
            Long newRecipientBalance = blockchain.getBalance(recipient) + amount;
            blockchain.updateAccountBalance(recipient, newRecipientBalance);
    
            // Mark the transaction as confirmed
            Logger.log(LogLevel.INFO, "Transaction processed: " + t);
            t.setStatus(Transaction.TransactionStatus.CONFIRMED);
        } else {
            t.setStatus(Transaction.TransactionStatus.REJECTED);

            // If transaction fails, mark it as rejected
            Logger.log(LogLevel.ERROR, "Transaction failed: " + t.getNonce());

            // print conditions
            if (!blockchain.existsAccount(sender)) {
                Logger.log(LogLevel.ERROR, "Sender not found: " + sender);
            } else if (!blockchain.existsAccount(recipient)) {
                Logger.log(LogLevel.ERROR, "Recipient not found: " + recipient);
            } else if (blockchain.getBalance(sender) < amount) {
                Logger.log(LogLevel.ERROR, "Insufficient balance for sender: " + sender +
                            " (Balance: " + blockchain.getBalance(sender) + ", Amount: " + amount + ")");
            }
        }
    
        // Return the updated transaction object
        return t;
    }

    public boolean checkTransactionSignature(Transaction transaction, int clientId) throws Exception {
        // Check if the transaction signature is valid
        byte[] transactionBytes = transaction.toByteArray();
        byte[] signature = transaction.getSignature();
        PublicKey publicKey = Config.getPublicKey(clientId);

        // System.out.println("------------- signature: " + new ByteArrayWrapper(signature));
        return CryptoUtil.verify(transactionBytes, signature, publicKey);
    }    
}
