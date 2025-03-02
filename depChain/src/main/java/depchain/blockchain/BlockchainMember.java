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
    private final int myId;
    private final int leaderId; // Static leader ID.
    private final List<Integer> allProcessIds;
    private final PerfectLink perfectLink;
    private final List<String> blockchain; // In-memory blockchain.
    private final ConcurrentMap<Integer, ConsensusInstance> consensusInstances = new ConcurrentHashMap<>();
    private final int f; // Maximum number of Byzantine faults allowed.
    private final ExecutorService consensusExecutor = Executors.newSingleThreadExecutor();
    private int consensusCounter = 0;

    public static void main(String[] args) throws Exception {
        // Usage: java BlockchainMember <processId> <port>
        if (args.length < 2) {
            Logger.log(LogLevel.ERROR, "Usage: BlockchainMember <processId> <port>");
            return;
        }

        Dotenv dotenv = Dotenv.load();
        int processId = Integer.parseInt(args[0]);
        int port = Integer.parseInt(args[1]);
        int leaderId = 1; // assume process 1 is leader
        List<Integer> allProcessIds = Arrays.asList(1, 2, 3, 4);
        
        // Load configuration from config.txt and resources folder.
        String configFilePath = dotenv.get("CONFIG_FILE_PATH");
        String keysFolderPath = dotenv.get("KEYS_FOLDER_PATH");

        if (configFilePath == null || keysFolderPath == null) {
            Logger.log(LogLevel.ERROR, "Environment variables CONFIG_FILE_PATH or KEYS_FOLDER_PATH are not set.");
            return;
        }

        // Load configuration from environment variables
        Config.loadConfiguration(configFilePath, keysFolderPath);
        Logger.log(LogLevel.DEBUG, "Configuration loaded from paths: " + configFilePath + ", " + keysFolderPath);

        // Create PerfectLink instance.
        PerfectLink pl = new PerfectLink(processId, port, Config.processAddresses, Config.getPrivateKey(processId),
                Config.publicKeys);
                
        // Assume maximum Byzantine faults f = 1 for 4 processes.
        BlockchainMember bm = new BlockchainMember(processId, leaderId, allProcessIds, pl, 1);
        Logger.log(LogLevel.INFO, "BlockchainMember " + processId + " started on port " + port);
        // The server runs indefinitely.
    }

    public BlockchainMember(int myId, int leaderId, List<Integer> allProcessIds, PerfectLink perfectLink, int f) {
        this.myId = myId;
        this.leaderId = leaderId;
        this.allProcessIds = allProcessIds;
        this.perfectLink = perfectLink;
        this.blockchain = new ArrayList<>();
        this.f = f;
        startMessageHandler();
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
                        if (myId == leaderId) {
                            int instanceId = consensusCounter++;
                            ConsensusInstance ci = new ConsensusInstance(myId, leaderId, allProcessIds, perfectLink,
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
                                                myId, null, msg.nonce);
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
            Logger.log(LogLevel.INFO, "Node " + myId + " appended value: " + value);
        }
    }

    // Method for externally starting a consensus instance (if needed).
    public Future<String> startConsensus(String clientRequest) {
        int instanceId = consensusCounter++;
        ConsensusInstance ci = new ConsensusInstance(myId, leaderId, allProcessIds, perfectLink, instanceId, f);
        consensusInstances.put(instanceId, ci);
        if (myId == leaderId) {
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