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
import depchain.utils.Config;
import depchain.utils.Logger;
import depchain.utils.Logger.LogLevel;

import depchain.consensus.State;

public class ConsensusInstance {
    private final int myId;
    private final int leaderId;
    private final List<Integer> allProcessIds;
    private final PerfectLink perfectLink;
    private final int epoch; // In our design, epoch doubles as the consensus instance ID.
    private String decidedValue = null;
    private final float quorumSize;
    private Map<Integer, State> stateResponses = new HashMap<>();
    private Map<Integer, TimestampValuePair> writeResponses = new HashMap<>();
    private List<String> acceptedValues = new ArrayList<>();
    private final int f;
    private final State state = new State();
    private boolean aborted = false;
    private final long maxWaitTime = 5000; // 5 seconds

    public ConsensusInstance(int myId, int leaderId, List<Integer> allProcessIds, PerfectLink perfectLink, int epoch,
            int f) {
        this.myId = myId;
        this.leaderId = leaderId;
        this.f = f;
        this.allProcessIds = new ArrayList<>(allProcessIds);
        this.perfectLink = perfectLink;
        this.epoch = epoch;
        this.quorumSize = (float) 2 * f + 1;
    }

    // Leader sends READ messages to all.
    private void broadcastRead() {
        Message readMsg = new Message.MessageBuilder(Message.Type.READ, epoch, null, myId).build();

        // Start by appending the leader's own state.
        stateResponses.put(leaderId, state);
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
        Message collectedMsg = new Message.MessageBuilder(Message.Type.COLLECTED, epoch, null, myId)
                .setStatesMap(stateResponses).build();
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

    private void broadcastWrite(TimestampValuePair candidate) {
        Message writeMsg = new Message.MessageBuilder(Message.Type.WRITE, epoch, null, myId).setWrite(candidate)
                .build();

        // append to the writeset of my state the candidate
        state.addToWriteSet(candidate);

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

    private void broadcastAccept(String candidate) {
        // update my state's most recent write
        state.setMostRecentWrite(new TimestampValuePair(epoch, candidate));

        acceptedValues.add(candidate);
        Message acceptMsg = new Message.MessageBuilder(Message.Type.ACCEPT, epoch, candidate, myId).build();
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
            switch (msg.getType()) {
                case READ:

                    Message stateMsg = new Message.MessageBuilder(Message.Type.STATE, epoch, msg.getValue(), myId)
                            .setState(state).build();
                    switch (Config.processBehaviors.get(this.myId)) {
                        case "byzantineState":
                            // Send a state message with a random state.
                            State currentStateCopy = state;
                            currentStateCopy.setMostRecentWrite(new TimestampValuePair(1, "Byzantine"));
                            currentStateCopy.addToWriteSet(new TimestampValuePair(1, "Byzantine"));
                            stateMsg = new Message.MessageBuilder(Message.Type.STATE, epoch, msg.getValue(), myId)
                                    .setState(currentStateCopy).build();
                            Logger.log(LogLevel.WARNING, "Byzantine state sent: " + currentStateCopy);
                            break;
                        case "impersonate":
                            // Send a state message impersonating another process - signature check should
                            // fail
                            int otherProcessId = myId == 3 ? 2 : 3;
                            stateMsg = new Message.MessageBuilder(Message.Type.STATE, epoch, msg.getValue(),
                                    otherProcessId).setState(state).build();
                            Logger.log(LogLevel.WARNING, "Invalid signature sent: " + stateMsg);
                            break;
                        case "spam":
                            State currentStateCopySpam = state;
                            currentStateCopySpam.setMostRecentWrite(new TimestampValuePair(1, "Spam"));
                            currentStateCopySpam.addToWriteSet(new TimestampValuePair(1, "Spam"));

                            stateMsg = new Message.MessageBuilder(Message.Type.STATE, epoch, msg.getValue(), myId)
                                    .setState(currentStateCopySpam).build();
                            Logger.log(LogLevel.WARNING, "Spam state sent, 100 times: " + currentStateCopySpam);
                            for (int i = 0; i < 100; i++) {
                                try {
                                    perfectLink.send(msg.getSenderId(), stateMsg);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                            break;
                        default:
                            break;
                    }
                    try {
                        perfectLink.send(msg.getSenderId(), stateMsg);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
                case STATE:
                    stateResponses.put(msg.getSenderId(), msg.getState());
                    break;
                case WRITE:
                    writeResponses.put(msg.getSenderId(), msg.getWrite());
                    break;
                case COLLECTED:
                    // Upon COLLECTED, update our local state and send an ACCEPT.
                    stateResponses = msg.getStatesMap();

                    Logger.log(LogLevel.INFO, "Received COLLECTED: " + stateResponses);

                    // Look at all states and decide the value you want to write (in write messages
                    // )
                    // pick the value to write
                    TimestampValuePair candidate = getValueFromCollected();

                    // Broadcast write
                    switch (Config.processBehaviors.get(this.myId)) {
                        case "spam":
                            for (int i = 0; i < 10; i++) {
                                broadcastWrite(candidate);
                            }
                            break;

                        default:
                            break;
                    }

                    broadcastWrite(candidate);

                    // Wait for writes
                    String valueToWrite = waitForWrites(candidate.getValue());

                    // If the value to write is null, then abort
                    if (valueToWrite == null) {
                        this.aborted = true;
                        break;
                    }

                    // Broadcast ACCEPT
                    switch (Config.processBehaviors.get(this.myId)) {
                        case "spam":
                            for (int i = 0; i < 10; i++) {
                                broadcastAccept(valueToWrite);
                            }
                            break;

                        default:
                            break;
                    }
                    broadcastAccept(valueToWrite);

                    // Wait for accepts
                    String valueToAppend = waitForAccepts(candidate.getValue());

                    // If the value to append is null, then abort
                    if (valueToAppend == null) {
                        this.aborted = true;
                        break;
                    }

                    this.decidedValue = valueToAppend;
                    break;
                case ACCEPT:
                    // Upon ACCEPT, update our local state.
                    acceptedValues.add(msg.getValue());
                    break;
                default:
                    break;
            }
        } catch (

        Exception e) {
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

        // check if the tmpVal appears in the write set of more than f processes
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

    public boolean waitForStates() throws InterruptedException, ExecutionException {
        // Start a counter to keep track of the time
        long startTime = System.currentTimeMillis();
        // check if a quorum has already been reached
        while ((float) stateResponses.size() < quorumSize) {
            Thread.sleep(250);
            Logger.log(LogLevel.DEBUG, "Still waiting for quorum of state responses...");
            Thread.sleep(250);

            // Check if the time has exceeded the maximum wait time
            if (System.currentTimeMillis() - startTime > this.maxWaitTime) {
                Logger.log(LogLevel.ERROR, "Max wait time exceeded");
                this.aborted = true;
                return false;
            }
        }

        // print all the states received
        Logger.log(LogLevel.INFO, "States received: " + stateResponses);

        return true;
    }

    public String waitForWrites(String candidate) throws InterruptedException, ExecutionException {
        // Start a counter to keep track of the time
        long startTime = System.currentTimeMillis();
        // check if a quorum has already been reached
        while ((float) writeResponses.size() < quorumSize) {
            Thread.sleep(250);
            Logger.log(LogLevel.DEBUG, "Still waiting for quorum of write responses...");
            Thread.sleep(250);

            // Check if the time has exceeded the maximum wait time
            if (System.currentTimeMillis() - startTime > this.maxWaitTime) {
                Logger.log(LogLevel.ERROR, "Max wait time exceeded");
                this.aborted = true;
                return null;
            }
        }

        // print all the writes received
        Logger.log(LogLevel.INFO, "Writes received: " + writeResponses);

        // Now we proceed to decide the value to write
        Map<String, Integer> count = new HashMap<>();
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

        if ((max < (f + 1)) || (valueToWrite != null && !valueToWrite.equals(candidate))) {
            Logger.log(LogLevel.ERROR,
                    "Value to write is not the same as the candidate or does not have more than f+1");
            return null;
        }

        Logger.log(LogLevel.INFO, "Value to write: " + valueToWrite);
        return valueToWrite;
    }

    public String waitForAccepts(String candidate) throws InterruptedException, ExecutionException {
        // Start a counter to keep track of the time
        long startTime = System.currentTimeMillis();
        // check if a quorum has already been reached
        while ((float) acceptedValues.size() < quorumSize) {
            Thread.sleep(250);
            Logger.log(LogLevel.DEBUG, "Still waiting for quorum to be met for accept responses...");
            Thread.sleep(250);

            // Check if the time has exceeded the maximum wait time
            if (System.currentTimeMillis() - startTime > this.maxWaitTime) {
                Logger.log(LogLevel.ERROR, "Max wait time exceeded");
                this.aborted = true;
                return null;
            }
        }

        Map<String, Integer> count = new HashMap<>();
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

        if ((max < f + 1) || (valueToAppend != null && !valueToAppend.equals(candidate))) {
            Logger.log(LogLevel.ERROR,
                    "Value to write is not the same as the candidate or does not have more than f+1");
            return null;
        }

        Logger.log(LogLevel.INFO, "Value to append to blockchain " + valueToAppend);
        return valueToAppend;
    }

    public String decide() {
        try {
            // Read Phase
            broadcastRead();
            // Wait for states
            // Check if it was aborted
            if (!waitForStates()) {
                return null;
            }

            // Broadcast collected
            broadcastCollected();

            // Wait for writes
            Thread.sleep(2000);

            // pick value to write
            TimestampValuePair candidate = getValueFromCollected();

            // Broadcast write
            broadcastWrite(candidate);
            // Wait for writes
            String valueToWrite = waitForWrites(candidate.getValue());

            // If the value to write is null, then abort
            if (valueToWrite == null) {
                this.aborted = true;
                return null;
            }

            // Broadcast ACCEPT
            broadcastAccept(valueToWrite);

            // Wait for accepts
            String valueToAppend = waitForAccepts(candidate.getValue());

            // If the value to append is null, then abort
            if (valueToAppend == null) {
                this.aborted = true;
                return null;
            }

            this.decidedValue = valueToAppend;

            return valueToAppend;
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            return null;
        }
    }

    public String getDecidedValue() {
        return decidedValue;
    }

    public boolean isAborted() {
        return aborted;
    }

    public void setBlockchainMostRecentWrite(TimestampValuePair write) {
        state.setMostRecentWrite(write);
    }
}
