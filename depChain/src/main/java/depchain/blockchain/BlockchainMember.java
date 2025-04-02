package depchain.blockchain;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import depchain.blockchain.block.Block;
import depchain.blockchain.block.BlockState;
import depchain.consensus.ConsensusInstance;
import depchain.consensus.TimestampValuePair;
import depchain.network.Message;
import depchain.network.Message.Type;
import depchain.network.PerfectLink;
import depchain.utils.Config;
import depchain.utils.EVMUtils;
import depchain.utils.Logger;
import depchain.utils.Logger.LogLevel;
import io.github.cdimascio.dotenv.Dotenv; // Ensure EVMUtils is imported if it exists in your project

public class BlockchainMember {
    private final int memberId;
    private final int memberPort;
    private final int leaderId;                 // Static leader ID.
    private String behavior;
    private final List<Integer> allProcessIds;  // All node IDs (no clients).
    private PerfectLink perfectLink;
    private ConcurrentMap<Integer, ConsensusInstance> consensusInstances = new ConcurrentHashMap<>();
    private final int f;                        // Maximum number of Byzantine faults allowed.
    private int epochNumber = 0;
    private Blockchain blockchain;

    private final List<Transaction> pendingTransactions = new java.util.concurrent.CopyOnWriteArrayList<>();

    private final int transactions_treshold = Dotenv.load().get("TRANSACTIONS_TRESHOLD") != null ? Integer.parseInt(Dotenv.load().get("TRANSACTIONS_TRESHOLD")) : 2;

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
                    ConsensusInstance consensusInstance = consensusInstances.get(msg.getClientId());

                    /* String decidedBlock = null; */
                    Block decidedBlock = null;
                    if (msg.getType() == Message.Type.CLIENT_REQUEST) {
                        
                        // Add the transaction to the pending transactions list.
                        synchronized (pendingTransactions) {
                            pendingTransactions.add(msg.getTransaction());
                        }

                        if(pendingTransactions.size() >= transactions_treshold) {
    
                            String lastBlockHash = blockchain.getMostRecentBlock().getBlockHash();
                            BlockState state = blockchain.getMostRecentBlock().getBlockState();
                            ArrayList<Transaction> transactions = new ArrayList<>(pendingTransactions);
                            Block block = new Block.BlockBuilder(transactions, lastBlockHash)
                                .setBlockState(state)
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
                                        epochNumber++, f, block, msg.getClientId());
                                consensusInstances.put(msg.getClientId(), consensusInstance);
                            }

                            if (memberId == leaderId) {

                                
                                    consensusInstance.setBlockchainMostRecentWrite(new TimestampValuePair(0, block));
                                    Logger.log(LogLevel.DEBUG, "Waiting for consensus decision...");
                                    decidedBlock = consensusInstance.decideBlock();
                                }
                                
                        }

                        else {
                            Logger.log(LogLevel.DEBUG, "Added transaction to pending transactions.");
                        }
                    
                    } else {
                        // For consensus messages, dispatch to the corresponding consensus instance.
                        if (consensusInstance == null) {
                            // instantiate a new consensus instance
                            consensusInstance = new ConsensusInstance(memberId, leaderId, allProcessIds,
                                    perfectLink, msg.getEpoch(), f, null, msg.getClientId());
                            consensusInstances.put(msg.getClientId(), consensusInstance);
                        }

                        consensusInstance.processMessage(msg);
                        
                        decidedBlock = consensusInstance.getDecidedBlock();
                    }

                    if (decidedBlock != null) {
                        try {
                            // Append the decided value to the blockchain.
                            this.blockchain.addBlock(decidedBlock);
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
                                     memberId, clientId).setBlock(decidedBlock).build();
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
}
