package depchain.blockchain;

import java.util.*;
import java.util.concurrent.*;

import depchain.consensus.ConsensusInstance;
import depchain.network.Message;
import depchain.network.PerfectLink;
import depchain.network.Message.Type;
import depchain.utils.Config;

import java.net.InetSocketAddress;
import io.github.cdimascio.dotenv.Dotenv;
import depchain.utils.Logger;
import depchain.utils.Logger.LogLevel;

public class BlockchainMember {
    private final int memberId;
    private final int memberPort;
    private final int leaderId; // Static leader ID.
    private final List<Integer> allProcessIds;
    private PerfectLink perfectLink;
    private final List<String> blockchain; // In-memory blockchain.
    private final ConcurrentMap<Integer, ConsensusInstance> consensusInstances = new ConcurrentHashMap<>();
    private final int f; // Maximum number of Byzantine faults allowed.
    private final ExecutorService consensusExecutor = Executors.newSingleThreadExecutor();
    private int consensusCounter = 0;

    public BlockchainMember(int memberId, int memberPort, int leaderId, int f) {
        this.memberId = memberId;
        this.memberPort = memberPort;
        this.leaderId = leaderId;
        this.allProcessIds = Arrays.asList(1, 2, 3, 4);
        this.blockchain = new ArrayList<>();
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
        new Thread(() -> {
            while (true) {
                try {
                    Message msg = perfectLink.deliver(); // waits for new elements to be added to the linked blocking
                                                         // queue
                    // If a CLIENT_REQUEST is received and this node is leader,
                    // then start a consensus instance for the client request.
                    if (msg.type == Message.Type.CLIENT_REQUEST) {
                        if (memberId == leaderId) {
                            int instanceId = consensusCounter++;
                            ConsensusInstance ci = new ConsensusInstance(memberId, leaderId, allProcessIds, perfectLink,
                                    instanceId, f);
                            consensusInstances.put(instanceId, ci);
                            ci.propose(msg.value);
                            new Thread(() -> {
                                try {
                                    Logger.log(LogLevel.DEBUG, "Waiting for decision...");
                                    // String decidedValue = ci.waitForDecision();
                                    String decidedValue = msg.value;
                                    Logger.log(LogLevel.DEBUG, "Decided value: " + decidedValue);
                                    // Send CLIENT_REPLY to the client.
                                    InetSocketAddress clientAddr = Config.clientAddresses.get(msg.senderId);
                                    if (clientAddr != null) {
                                        Message reply = new Message(Message.Type.CLIENT_REPLY, instanceId, decidedValue,
                                                memberId, null, msg.nonce);
                                        perfectLink.send(msg.senderId, reply);
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }).start();
                        }
                    } else {
                        // For consensus messages, dispatch to the corresponding consensus instance.
                        ConsensusInstance ci = consensusInstances.get(msg.epoch);
                        if (ci != null) {
                            ci.processMessage(msg);
                            if (msg.type == Message.Type.DECIDED) {
                                upcallDecided(msg.value);
                            }
                        } else {
                            // consensusCounter = msg.epoch;
                            // ConsensusInstance newCi = new ConsensusInstance(myId, leaderId, allProcessIds,
                            // perfectLink, msg.epoch, f);
                            // consensusInstances.put(msg.epoch, newCi);
                            // newCi.processMessage(msg);
                        }
                    }
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    // Upcall: when a decision is reached, append the decided value to our blockchain.
    private void upcallDecided(String value) {
        synchronized (blockchain) {
            blockchain.add(value);
            Logger.log(LogLevel.INFO, "Node " + memberId + " appended value: " + value);
        }
    }

    // Method for externally starting a consensus instance (if needed).
    public Future<String> startConsensus(String clientRequest) {
        int instanceId = consensusCounter++;
        ConsensusInstance ci = new ConsensusInstance(memberId, leaderId, allProcessIds, perfectLink, instanceId, f);
        consensusInstances.put(instanceId, ci);
        if (memberId == leaderId) {
            ci.propose(clientRequest);
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                return ci.waitForDecision();
            } catch (Exception e) {
                return null;
            }
        });
    }

    public List<String> getBlockchain() {
        synchronized (blockchain) {
            return new ArrayList<>(blockchain);
        }
    }
}