package depchain;

import java.util.*;
import java.util.concurrent.*;
import java.net.InetSocketAddress;

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
    
    public BlockchainMember(int myId, int leaderId, List<Integer> allProcessIds,
                            PerfectLink perfectLink, int f) {
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
                    Message msg = perfectLink.deliver();
                    // If a CLIENT_REQUEST is received and this node is leader,
                    // then start a consensus instance for the client request.
                    if (msg.type == Message.Type.CLIENT_REQUEST) {
                        if (myId == leaderId) {
                            int instanceId = consensusCounter++;
                            ConsensusInstance ci = new ConsensusInstance(myId, leaderId, allProcessIds, perfectLink, instanceId, f);
                            consensusInstances.put(instanceId, ci);
                            ci.propose(msg.value);
                            new Thread(() -> {
                                try {
                                    System.out.println("DEBUG: waiting for decision...");
                                    // String decidedValue = ci.waitForDecision();
                                    String decidedValue = msg.value;
                                    System.out.println("DEBUG: decided value: " + decidedValue);
                                    // Send CLIENT_REPLY to the client.
                                    InetSocketAddress clientAddr = Config.clientAddresses.get(msg.senderId);
                                    if (clientAddr != null) {
                                        Message reply = new Message(Message.Type.CLIENT_REPLY, instanceId, decidedValue, myId, null);
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
                            // ConsensusInstance newCi = new ConsensusInstance(myId, leaderId, allProcessIds, perfectLink, msg.epoch, f);
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
            System.out.println("Node " + myId + " appended value: " + value);
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
