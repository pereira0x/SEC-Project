package depchain.consensus;

import java.sql.Time;
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
    private Map<Integer, TimestampValuePair> writeResponses = new HashMap<>();
    private final int f;
    private final State blockchain;

    public ConsensusInstance(int myId, int leaderId, List<Integer> allProcessIds, PerfectLink perfectLink, int epoch,
            int f, State blockchain) {
        this.myId = myId;
        this.leaderId = leaderId;
        this.f = f;
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
        Message readMsg = new Message(Message.Type.READ, epoch, null, myId, null, -1);
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

    private void broadcastCollected() {
        Message collectedMsg = new Message(Message.Type.COLLECTED, epoch, null, myId, null, -1, null, null, stateResponses);
        for (int pid : allProcessIds) {
            if (pid != leaderId) {
                try {
                    perfectLink.send(pid, collectedMsg);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // Leader broadcasts WRITE message.
    private void broadcastWrite(TimestampValuePair candidate) {
        Message writeMsg = new Message(Message.Type.WRITE, epoch, null, myId, null,
                -1, null, null, null, candidate);

        writeResponses.put(myId, candidate);
        for (int pid : allProcessIds) {
            if (pid != myId) {
                try {
                    perfectLink.send(pid, writeMsg);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
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
        try {
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
                    writeResponses.put(msg.senderId, msg.write);
                    break;
                case COLLECTED:
                    // Upon COLLECTED, update our local state and send an ACCEPT.
                    stateResponses = msg.statesMap;
                    Logger.log(LogLevel.ERROR, "Received COLLECTED message from " + msg.senderId + " with states " + stateResponses);
                    // Look at all states and decide the value you want to write (in write messages )
                    // pick the value to write
                    TimestampValuePair candidate = getValueFromCollected();
                    // Broadcast write
                    broadcastWrite(candidate);
                    // Wait for writes
                    waitForWrites();
                    break;
                default:
                    // Ignore other message types.
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // decide the value to be written based on the states of all processes
    public TimestampValuePair getValueFromCollected() {
        TimestampValuePair tmpVal = null;
        for (State s : stateResponses.values()) {
            TimestampValuePair mostRecentWrite = s.getMostRecentWrite();
            if (tmpVal == null || mostRecentWrite.getTimestamp() > tmpVal.getTimestamp()) {
                tmpVal = mostRecentWrite;
            }
        }

        // check if the tmpVal appears in the writeset of more than f processes
        int count = 0;
        for (State s : stateResponses.values()) {
            if (s.getWriteset().contains(tmpVal)) {
                count++;
            }
        }

        if (count > this.f) {
            Logger.log(LogLevel.INFO, "Decided value: " + tmpVal);
            return tmpVal;
        } else {
            // return the most recent write in the writeset of the leader
            Logger.log(LogLevel.INFO, "Decided value: " + stateResponses.get(leaderId).getMostRecentWrite());
            return stateResponses.get(leaderId).getMostRecentWrite();
        }

    }

    public void waitForStates() throws InterruptedException, ExecutionException {
        // check if a quorum has already been reached
        while ((float) stateResponses.size() < quorumSize) {
            Thread.sleep(250);
            Logger.log(LogLevel.DEBUG, "Still waiting for quorum to be met for write responses...");
            Thread.sleep(250);
        }

        // // Print the state of all processes.
        for (State s : stateResponses.values()) {
            Logger.log(LogLevel.INFO, "State of process " + s);
        }
    }

    public void waitForWrites() throws InterruptedException, ExecutionException {
        // check if a quorum has already been reached
        while ((float) writeResponses.size() < quorumSize) {
            Thread.sleep(250);
            Logger.log(LogLevel.DEBUG, "Still waiting for quorum to be met for write responses...");
            Thread.sleep(250);
        }

        // // Print the state of all processes.
        for (TimestampValuePair s : writeResponses.values()) {
            Logger.log(LogLevel.INFO, "Write of process " + s);
        }
        //print all the writes received
        Logger.log(LogLevel.INFO, "Writes received: " + writeResponses);
    }

    public String decide(String value) {
        try {        
            // Read Phase
            readPhase(value);
            // Wait for states
            Thread.sleep(1000);
            waitForStates();
            Thread.sleep(1000);

            // Broadcast collected
            broadcastCollected();

            Thread.sleep(1000);

            // pick value to write
            TimestampValuePair candidate = getValueFromCollected();

            // Broadcast write
            broadcastWrite(candidate);
            Thread.sleep(1000);
            // Wait for writes
            waitForWrites();
            Thread.sleep(5000);

        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }

        return value;
    }

    public void setBlockchainMostRecentWrite(TimestampValuePair write) {
        blockchain.setMostRecentWrite(write);
    }
}
