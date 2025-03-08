package depchain.consensus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import depchain.network.Message;
import depchain.network.PerfectLink;

import depchain.utils.Logger;
import depchain.utils.Logger.LogLevel;

public class ConsensusInstance {
    private final int myId;
    private final int leaderId;
    private final List<Integer> allProcessIds;
    private final PerfectLink perfectLink;
    private final int epoch; // In our design, epoch doubles as the consensus instance ID.
    private volatile String localValue = null;
    private volatile int localTimestamp = 0; // For simplicity, use epoch as timestamp.
    private final int quorumSize; // e.g., quorum = floor((N + f) / 2) + 1.
    private Map<Integer, Message> stateResponses = new HashMap<>();
    private static List<String> blockchain = new ArrayList<>();

    public ConsensusInstance(int myId, int leaderId, List<Integer> allProcessIds, PerfectLink perfectLink, int epoch,
            int f, List<String> blockchain) {
        this.myId = myId;
        this.leaderId = leaderId;
        this.allProcessIds = new ArrayList<>(allProcessIds);
        this.perfectLink = perfectLink;
        this.epoch = epoch;
        this.quorumSize = ((allProcessIds.size() + f) / 2);
        this.blockchain = blockchain;
    }

    // Called by the leader (or by a process that initiates consensus) to set the proposal.
    public void propose(String v) {
        // Set our local value if not already set.
        if (localValue == null) {
            localValue = v;
            localTimestamp = epoch; // Simplification: using epoch as timestamp.
        }
        if (myId == leaderId) {
            broadcastRead();
        }
    }

    // Leader sends READ messages to all.
    private void broadcastRead() {
        //TODO: CHANGE THIS NONCE
        Message readMsg = new Message(Message.Type.READ, epoch, "", leaderId, null, -1);
        // Start by appending the leader's own state.
        stateResponses.put(leaderId, readMsg);
        for (int pid : allProcessIds) {
            if (pid != leaderId) {
                try {
                    perfectLink.send(pid, readMsg);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        // Determine candidate value: choose the one with highest “timestamp” if any are set.
        // String candidate = localValue;
        // int maxTimestamp = localTimestamp;
        // for (Message m : stateResponses.values()) {
        //     if (m.value != null && !m.value.isEmpty()) {
        //         // In our simplified case, we use the epoch field as the timestamp.
        //         if (m.epoch > maxTimestamp) {
        //             candidate = m.value;
        //             maxTimestamp = m.epoch;
        //         }
        //     }
        // }
        // broadcastWrite(candidate);
    }

    // Leader broadcasts WRITE message.
    private void broadcastWrite(String candidate) {
        Message writeMsg = new Message(Message.Type.WRITE, epoch, candidate, leaderId, null,
                -1);
        for (int pid : allProcessIds) {
            if (pid != leaderId) {
                try {
                    perfectLink.send(pid, writeMsg);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        // Wait for ACCEPT messages.
        new Thread(() -> {
            int acceptCount = 0;
            while (acceptCount < quorumSize) {
                try {
                    Message msg = perfectLink.deliver();
                    if (msg.type == Message.Type.ACCEPT && msg.epoch == epoch && candidate.equals(msg.value)) {
                        acceptCount++;
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            broadcastDecided(candidate);
        }).start();
    }

    // Leader broadcasts DECIDED message.
    private void broadcastDecided(String candidate) {
        Message decidedMsg = new Message(Message.Type.DECIDED, epoch, candidate, leaderId, null, -1);
        for (int pid : allProcessIds) {
            if (pid != leaderId) {
                try {
                    perfectLink.send(pid, decidedMsg);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // This method is invoked (by any process) when a message is delivered.
    public void processMessage(Message msg) {
        if (msg.epoch != epoch)
            return;
        switch (msg.type) {
            case READ:
                // get the state of the blockchain of the process' last appended value
                Message stateMsg = new Message(Message.Type.STATE, epoch, blockchain, myId,
                        null, -1, null);
                try {
                    perfectLink.send(msg.senderId, stateMsg);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            case STATE:
                stateResponses.put(msg.senderId, msg);
                break;
            case WRITE:
                // Upon WRITE, update our local state and send an ACCEPT.
                localValue = msg.value;
                localTimestamp = epoch;
                Message acceptMsg = new Message(Message.Type.ACCEPT, epoch, msg.value, myId, null, -1);
                try {
                    perfectLink.send(leaderId, acceptMsg);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            default:
                // Ignore other message types.
                break;
        }
    }

    // private void decide(String candidate) {
    //     if (!decisionFuture.isDone()) {
    //         decisionFuture.complete(candidate);
    //     }
    // }

    public String waitForDecision() throws InterruptedException, ExecutionException {
        // check if a quorum has already been reached
        while (stateResponses.size() < quorumSize) {
            Thread.sleep(1000);
            Logger.log(LogLevel.DEBUG, "Still waiting for quorum to be met...");
        }

        // For now, just print the states of the blockchain of all processes.
        for (Message m : stateResponses.values()) {
            Logger.log(LogLevel.INFO, "State of process " + m.senderId + ": " + m.state);
        }

        return localValue;
    }
}
