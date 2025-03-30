package depchain.blockchain;

import java.util.*;
import java.util.concurrent.*;

import depchain.consensus.ConsensusInstance;
import depchain.consensus.State;
import depchain.consensus.TimestampValuePair;
import depchain.network.Message;
import depchain.network.PerfectLink;
import depchain.network.Message.Type;
import depchain.utils.Config;

import java.net.InetSocketAddress;
import io.github.cdimascio.dotenv.Dotenv;
import java.io.IOException;
import depchain.utils.Logger;
import depchain.utils.Logger.LogLevel;

public class BlockchainMember {
    private final int memberId;
    private final int memberPort;
    private final int leaderId; // Static leader ID.
    private String behavior;
    private final List<Integer> allProcessIds;
    private PerfectLink perfectLink;
    /* private final ConcurrentMap<Integer, ConsensusInstance> consensusInstances = new ConcurrentHashMap<>(); */
    private ConsensusInstance consensusInstance;
    private final int f; // Maximum number of Byzantine faults allowed.
    private int epochNumber = 0;
    private ArrayList<String> blockchain = new ArrayList<>();

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
           Blockchain blockchain = new Blockchain(this.memberId, Config.getClientPublicKeys());
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
                // then start a consensus instance for the client request.
                switch (this.behavior) {
                    case "ignoreMessages":
                        // Byzantine behavior: ignore all messages.
                        Logger.log(LogLevel.WARNING, "Byzantine behavior: dropping message.");
                        continue;
                    default:
                        break;
                }
                new Thread(() -> {
                    String decidedValue = null;
                    if (msg.getType() == Message.Type.CLIENT_REQUEST) {
                        if (consensusInstance != null) {
                            consensusInstance.setClientRequest(msg.getValue());
                        } else {
                            consensusInstance = new ConsensusInstance(memberId, leaderId, allProcessIds, perfectLink,
                                    epochNumber++, f, msg.getValue(), msg.getSenderId());
                        }

                        if (memberId == leaderId) {
                            consensusInstance.setBlockchainMostRecentWrite(new TimestampValuePair(0, msg.getValue()));
                            Logger.log(LogLevel.DEBUG, "Waiting for consensus decision...");

                            decidedValue = consensusInstance.decide();
                        }
                    } else {
                        // For consensus messages, dispatch to the corresponding consensus instance.
                        if (consensusInstance == null) {
                            // instantiate a new consensus instance
                            consensusInstance = new ConsensusInstance(memberId, leaderId, allProcessIds,
                                    perfectLink, msg.getEpoch(), f, null, msg.getSenderId());
                        }

                        consensusInstance.processMessage(msg);
                        
                        decidedValue = consensusInstance.getDecidedValue();
                    }

                    if (decidedValue != null) {
                        try {
                            // Append the decided value to the blockchain.
                            this.blockchain.add(decidedValue);
                            Logger.log(LogLevel.WARNING, "Blockchain updated: " + this.blockchain);

                            int clientId = consensusInstance.getRequesterId();

                            consensusInstance = null;

                            // Send CLIENT_REPLY to the client.
                            InetSocketAddress clientAddr = Config.processAddresses.get(clientId);

                            
                            if (clientAddr != null) {
                                // TODO: EPOCH NUMBER MUST BE A NEW ONE
                                // TODO: IMPLEMENT CLIENT IDS PROPERLY
                                Message reply = new Message.MessageBuilder(Type.CLIENT_REPLY, msg.getEpoch(),
                                        decidedValue, memberId).build();
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
                    }
                }).start();
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public ArrayList<String> getBlockchain() {
        return this.blockchain;
    }

    public int getMemberId() {
        return this.memberId;
    }
}
