package depchain.consensus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.HashMap;
import java.util.Map;

import depchain.network.Message;
import depchain.network.PerfectLink;

import depchain.utils.Logger;
import depchain.utils.Logger.LogLevel;

import depchain.consensus.State;

public class ConsensusInstance {
    private final int myId;
    private final int leaderId;
    private final List<Integer> allProcessIds;
    private final PerfectLink perfectLink;
    private final int epoch; // In our design, epoch doubles as the consensus instance ID.
    private volatile String localValue = null;
    private volatile int localTimestamp = 0; // For simplicity, use epoch as timestamp.
    private final float quorumSize; // e.g., quorum = floor((N + f) / 2).
    private Map<Integer, State> stateResponses = new HashMap<>();
    private final State blockchain;

    public ConsensusInstance(int myId, int leaderId, List<Integer> allProcessIds, PerfectLink perfectLink, int epoch,
            int f, State blockchain) {
        this.myId = myId;
        this.leaderId = leaderId;
        this.allProcessIds = new ArrayList<>(allProcessIds);
        this.perfectLink = perfectLink;
        this.epoch = epoch;
        this.quorumSize = ((float) ( allProcessIds.size() + f) / 2);
        this.blockchain = blockchain;
    }

    // Called by the leader (or by a process that initiates consensus) to set the proposal.
    public void readPhase(String v) {
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
        Message readMsg = new Message(Message.Type.READ, epoch, localValue, myId, null, -1);
        // produce a STATE message just to add to the list
        
        // Start by appending the leader's own state.
        stateResponses.put(leaderId, blockchain);
        for (int pid : allProcessIds) {
            if (pid != leaderId) {
                try {
                    perfectLink.send(pid, readMsg);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
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
            float acceptCount = 0;
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
        if (msg.epoch != epoch){
            Logger.log(LogLevel.DEBUG, "Received message for a different consensus instance...");
            return;
        }
        switch (msg.type) {
            case READ:

                //System.out.println("BLOCKCHAIN: " + blockchain);
                // get the state of the blockchain of the process
                // and for now return the msg value itself as the process choice
                
                Message stateMsg = new Message(Message.Type.STATE, epoch, msg.value, myId,
                        null, -1, null , blockchain);
                try {
                    //System.out.println("Sending state message to " + msg.senderId + " with statte " + stateMsg.state);
                    perfectLink.send(msg.senderId, stateMsg);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            case STATE:

                stateResponses.put(msg.senderId, msg.state);
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


    public String waitForStates() throws InterruptedException, ExecutionException {
        // check if a quorum has already been reached
        while ((float) stateResponses.size() < quorumSize) {
            Thread.sleep(250);
            Logger.log(LogLevel.DEBUG, "Still waiting for quorum to be met...");
            Thread.sleep(250);
        }

        // // Print the state of all processes.
        for (State s : stateResponses.values()) {
            Logger.log(LogLevel.INFO, "State of process " + s);
        }

        // 

        // for (Message m : stateResponses.values()) {
        //     Logger.log(LogLevel.INFO, "State of process " + m.senderId + ": " + m.state + " (choice: " + m.value + ")");
        // }

        // // Determine candidate value using a dictionary
        // // 1. Check the most voted answer
        // // 2. If there is no such value, choose the leaders own local value
        // Map<String, Integer> votes = new HashMap<>();

        // for (Message m : stateResponses.values()) {
        //     if (m.value != null && !m.value.isEmpty()) {
        //         votes.put(m.value, votes.getOrDefault(m.value, 0) + 1);
        //     }
        // }

        // // Determine the candidate with the most votes
        // String candidate = null;
        // int maxVotes = 0;
        // boolean tie = false;

        // for (Map.Entry<String, Integer> entry : votes.entrySet()) {
        //     if (entry.getValue() > maxVotes) {
        //         maxVotes = entry.getValue();
        //         candidate = entry.getKey();
        //         tie = false;
        //     } else if (entry.getValue() == maxVotes) {
        //         tie = true;
        //     }
        // }

        // // If there's a tie or no votes, use the leader's local value
        // if (tie) {
        //     candidate = localValue;
        // }

        return "Hello";
    }
}
