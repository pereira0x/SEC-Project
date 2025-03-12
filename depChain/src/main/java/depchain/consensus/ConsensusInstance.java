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
    private List<String> acceptedValues = new ArrayList<>();
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

    // Leader broadcasts ACCEPT message.
    private void broadcastAccept(String candidate) {
        acceptedValues.add(candidate);
        Message acceptMsg = new Message(Message.Type.ACCEPT, epoch, candidate, myId, null, -1);
        for (int pid : allProcessIds) {
            if (pid != myId) {
                try {
                    perfectLink.send(pid, acceptMsg);
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
                    Thread.sleep(1000);
                    broadcastWrite(candidate);
                    Thread.sleep(1000);
                    // Wait for writes
                    String valueToWrite = waitForWrites();
                    Thread.sleep(1000);
                    // Broadcast ACCEPT
                    broadcastAccept(valueToWrite);
                    Thread.sleep(1000);
                    // Wait for accepts
                    String valueToAppend = waitForAccepts();
                    break;
                case ACCEPT:
                    // Upon ACCEPT, update our local state.
                    acceptedValues.add(msg.value);
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

    public String waitForWrites() throws InterruptedException, ExecutionException {
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

        Map <String, Integer> count = new HashMap<>();
        for (TimestampValuePair s : writeResponses.values()) {
            if (count.containsKey(s.getValue())) {
                count.put(s.getValue(), count.get(s.getValue()) + 1);
            } else {
                count.put(s.getValue(), 1);
            }
        }

        String valueToWrite = null;
        int max = 0;
        for (Map.Entry<String, Integer> entry : count.entrySet()) {
            if (entry.getValue() > max) {
                max = entry.getValue();
                valueToWrite = entry.getKey();
            }
        }

        Logger.log(LogLevel.INFO, "Value to write: " + valueToWrite);
        return valueToWrite;
    }

    public String waitForAccepts() throws InterruptedException, ExecutionException {
        // check if a quorum has already been reached
        while ((float) acceptedValues.size() < quorumSize) {
            Thread.sleep(250);
            Logger.log(LogLevel.DEBUG, "Still waiting for quorum to be met for accept responses...");
            Thread.sleep(250);
        }

        // // Print the state of all processes.
        for (String s : acceptedValues) {
            Logger.log(LogLevel.INFO, "Accept of process " + s);
        }

        Map <String, Integer> count = new HashMap<>();
        for (String s : acceptedValues) {
            if (count.containsKey(s)) {
                count.put(s, count.get(s) + 1);
            } else {
                count.put(s, 1);
            }
        }

        String valueToAppend = null;
        int max = 0;
        for (Map.Entry<String, Integer> entry : count.entrySet()) {
            if (entry.getValue() > max) {
                max = entry.getValue();
                valueToAppend = entry.getKey();
            }
        }

        Logger.log(LogLevel.INFO, "Value to append to blockchain " + valueToAppend);
        return valueToAppend;
    }

    public String decide(String value) {
        try {        
            // Read Phase
            readPhase(value);
            // Wait for states
            waitForStates();

            // Broadcast collected
            broadcastCollected();

            Thread.sleep(2000);

            // pick value to write
            TimestampValuePair candidate = getValueFromCollected();

            // Broadcast write
            broadcastWrite(candidate);
            Thread.sleep(2000);
            // Wait for writes
            String valueToWrite = waitForWrites();

            Thread.sleep(1000);
            // Broadcast ACCEPT
            broadcastAccept(valueToWrite);
            Thread.sleep(1000);
            // Wait for accepts
            String valueToAppend = waitForAccepts();

            return valueToAppend;
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            return null;
        }

    }

    public void setBlockchainMostRecentWrite(TimestampValuePair write) {
        blockchain.setMostRecentWrite(write);
    }
}
