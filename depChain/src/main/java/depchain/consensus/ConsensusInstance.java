package depchain.consensus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import depchain.blockchain.block.Block; // Ensure this is the correct package for Transaction
import depchain.blockchain.Transaction;
import depchain.network.Message; // Ensure this is the correct package for Block
import depchain.network.PerfectLink;
import depchain.utils.Config;
import depchain.utils.Logger;
import depchain.utils.Logger.LogLevel;

public class ConsensusInstance {
    private final int myId;
    private final int leaderId;
    private final List<Integer> allProcessIds;
    private final PerfectLink perfectLink;
    private final int epoch; // In our design, epoch doubles as the consensus instance ID.
    private Block decidedBlock = null;
    private final float quorumSize;
    private Map<Integer, State> stateResponses = new HashMap<>();
    private Map<Integer, TimestampValuePair> writeResponses = new HashMap<>();
    private List<Block> acceptedValues = new ArrayList<>();
    private final int f;
    private final State state = new State();
    private boolean aborted = false;
    private final long maxWaitTime = 5000; // 5 seconds
    private Block blockProposed;

    public ConsensusInstance(int myId, int leaderId, List<Integer> allProcessIds, PerfectLink perfectLink, int epoch,
            int f, Block blockProposed) {

        this.myId = myId;
        this.leaderId = leaderId;
        this.f = f;
        this.allProcessIds = new ArrayList<>(allProcessIds);
        this.perfectLink = perfectLink;
        this.epoch = epoch;
        this.quorumSize = (float) 2 * f + 1;
        this.blockProposed = blockProposed;
    }

    // Leader sends READ messages to all.
    private void broadcastRead() {
        Message readMsg = new Message.MessageBuilder(Message.Type.READ, epoch, myId).setBlock(blockProposed).build();

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
        Message collectedMsg = new Message.MessageBuilder(Message.Type.COLLECTED, epoch, myId)
                .setStatesMap(stateResponses).setBlock(blockProposed).build();
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
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        Message writeMsg = new Message.MessageBuilder(Message.Type.WRITE, epoch, myId)
                .setWrite(candidate)
                .setBlock(blockProposed)
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

    private void broadcastAccept(Block candidate) {
        // update my state's most recent write
        state.setMostRecentWrite(new TimestampValuePair(epoch, candidate));

        acceptedValues.add(candidate);
        Message acceptMsg = new Message.MessageBuilder(Message.Type.ACCEPT, epoch, myId)
                                        .setBlock(candidate)
                                        .build();
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

                    Message stateMsg = new Message.MessageBuilder(Message.Type.STATE, epoch, myId)
                            .setState(state).setBlock(msg.getBlock()).build();
                    switch (Config.processBehaviors.get(this.myId)) {
                        case "byzantineState":
                            // Send a state message with a random state.
                            State currentStateCopy = state;
                            // TODO: empty block instead of hardcoded string "Byzantine"
                            currentStateCopy.setMostRecentWrite(new TimestampValuePair(1, new Block()));
                            currentStateCopy.addToWriteSet(new TimestampValuePair(1, new Block()));
                            stateMsg = new Message.MessageBuilder(Message.Type.STATE, epoch, myId)
                                    .setState(currentStateCopy).setBlock(msg.getBlock()).build();
                            Logger.log(LogLevel.WARNING, "Byzantine state sent: " + currentStateCopy);
                            break;
                        case "impersonate":
                            // Send a state message impersonating another process - signature check should
                            // fail
                            int otherProcessId = myId == 3 ? 2 : 3;
                            stateMsg = new Message.MessageBuilder(Message.Type.STATE, epoch,
                                    otherProcessId).setState(state).setBlock(msg.getBlock()).build();
                            Logger.log(LogLevel.WARNING, "Invalid signature sent: " + stateMsg);
                            break;
                        case "spam":
                            State currentStateCopySpam = state;
                            // TODO: empty block instead of hardcoded string "Spam"
                            currentStateCopySpam.setMostRecentWrite(new TimestampValuePair(1, new Block()));
                            currentStateCopySpam.addToWriteSet(new TimestampValuePair(1, new Block()));

                            stateMsg = new Message.MessageBuilder(Message.Type.STATE, epoch, myId)
                                    .setState(currentStateCopySpam).setBlock(msg.getBlock()).build();
                            Logger.log(LogLevel.WARNING, "Spam state sent, 10 times: " + currentStateCopySpam);
                            for (int i = 0; i < 10; i++) {
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

                    /* Logger.log(LogLevel.INFO, "Received COLLECTED: " + stateResponses); */

                    // Look at all states and decide the value you want to write (in write messages
                    // )
                    // pick the value to write
                    TimestampValuePair candidate = getValueFromCollected();

                    if (candidate == null) {
                        this.aborted = true;
                        break;
                    }

                    // Broadcast write
                    switch (Config.processBehaviors.get(this.myId)) {
                        case "spam":
                            Logger.log(LogLevel.WARNING, "Spam write sent, 10 times: " + candidate);
                            for (int i = 0; i < 0; i++) {
                                broadcastWrite(candidate);
                            }
                            break;

                        default:
                            break;
                    }

                    broadcastWrite(candidate);

                    // Wait for writes
                    Block blockToWrite = waitForWrites(candidate.getValue());

                    // If the value to write is null, then abort
                    if (blockToWrite == null) {
                        this.aborted = true;
                        break;
                    }

                    // Broadcast ACCEPT
                    switch (Config.processBehaviors.get(this.myId)) {
                        case "spam":
                            Logger.log(LogLevel.WARNING, "Spam accept sent, 10 times: " + blockToWrite);
                            for (int i = 0; i < 10; i++) {
                                broadcastAccept(blockToWrite);
                            }
                            break;

                        default:
                            break;
                    }
                    broadcastAccept(blockToWrite);

                    // Wait for accepts
                    Block blockToAccept = waitForAccepts(candidate.getValue());

                    // If the value to append is null, then abort
                    if (blockToAccept == null) {
                        this.aborted = true;
                        break;
                    }

                    this.decidedBlock = blockToAccept;
                    break;
                case ACCEPT:
                    // Upon ACCEPT, update our local state.
                    acceptedValues.add(msg.getBlock());
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
        switch (Config.processBehaviors.get(this.myId)) {
            case "byzantineLeader":
                // Byzantine leader tries to favor client 7 by making the proposedBlock consist
                // of 2 transactions from client 5 to 7 of 5000
                for (Transaction t : blockProposed.getTransactions()) {
                    t.setSender("5");
                    t.setRecipient("7");
                    t.setAmount(5000);
                }

                return new TimestampValuePair(epoch, blockProposed);
            default:
                break;
        }

        TimestampValuePair tmpVal = null;
        Map<TimestampValuePair, Integer> count = new HashMap<>();
        for (State s : stateResponses.values()) {
            TimestampValuePair mostRecentWrite = s.getMostRecentWrite();
            // System.out.println("mostrecentwrite: " + mostRecentWrite);

           // check if the most recent write is null - the member has not seen any writes
           if(mostRecentWrite.getValue().getBlockHash() == null){
                continue;
            }

           if(!count.containsKey(mostRecentWrite)) {
                count.put(mostRecentWrite, 0);
            }

           // [ws1: 0 ws2: 0 ws3: 0]
        }
        // System.out.println("count: " + count);
        // check if the tmpVal appears in the write set of more than f processes
       for (Map.Entry<TimestampValuePair, Integer> entry : count.entrySet()) {
            int updatedValue = entry.getValue();
            for (State s : stateResponses.values()) {
                if (s.getWriteset().contains(entry.getKey())) {
                    updatedValue++;
                }
            }
            count.put(entry.getKey(), updatedValue);
        }

         // we do this to prevent a Byzantine member from being able to send
         // a state with a mostRecentWrite with a timestamp abnormally high
         // preventing the real mostRecentWrite from being chosen
        int max = 0;
        for (Map.Entry<TimestampValuePair, Integer> entry : count.entrySet()) {
            if (entry.getValue() > max) {
                max = entry.getValue();
                tmpVal = entry.getKey();
            }
        }
        // System.out.println("max: " + max);
        // System.out.println("tmpVal: " + tmpVal);
        if (max <= this.f) {
            tmpVal = stateResponses.get(leaderId).getMostRecentWrite();
        }

        if (!tmpVal.getValue().equals(this.blockProposed)) {
            Logger.log(LogLevel.ERROR, "Decided value is not the client request: " + tmpVal.getValue() + " != " + this.blockProposed);
            return null;
        }

        /* Logger.log(LogLevel.INFO, "Decided value: " + tmpVal); */
        return tmpVal;
    }

    public boolean waitForStates() throws InterruptedException, ExecutionException {
        // Start a counter to keep track of the time
        long startTime = System.currentTimeMillis();
        // check if a quorum has already been reached
        while ((float) stateResponses.size() < quorumSize) {
            Thread.sleep(250);
            /* Logger.log(LogLevel.DEBUG, "Still waiting for quorum of state responses..."); */
            Thread.sleep(250);

            // Check if the time has exceeded the maximum wait time
            if (System.currentTimeMillis() - startTime > this.maxWaitTime) {
                Logger.log(LogLevel.ERROR, "Max wait time exceeded");
                this.aborted = true;
                return false;
            }
        }

        // print all the states received
       /*  Logger.log(LogLevel.INFO, "States received: " + stateResponses); */

        return true;
    }

    public Block waitForWrites(Block candidate) throws InterruptedException, ExecutionException {
        // Start a counter to keep track of the time
        long startTime = System.currentTimeMillis();
        Block blockToWrite = null;

        // check if a quorum has already been reached
        int numWrites;
        do {
            numWrites = 0;
            Thread.sleep(500);

            // Now we proceed to decide the value to write
            Map<Block, Integer> count = new HashMap<>();
            for (TimestampValuePair b : writeResponses.values()) {
                Block value = b.getValue();
                if (count.containsKey(value)) {
                    count.put(value, count.get(value) + 1);
                } else {
                    count.put(value, 1);
                }
                numWrites++;
            }

            int max = 0;
            for (Map.Entry<Block, Integer> entry : count.entrySet()) {
                if (entry.getValue() > max) {
                    max = entry.getValue();
                    blockToWrite = entry.getKey();
                }
            }

            if ((max >= (2*f + 1)) && blockToWrite.equals(candidate))
                break;

            // Check if the time has exceeded the maximum wait time
            if (System.currentTimeMillis() - startTime > this.maxWaitTime) {
                Logger.log(LogLevel.ERROR, "Max wait time exceeded");
                this.aborted = true;
                return null;
            }

            blockToWrite = null;
            /* Logger.log(LogLevel.DEBUG, "Still waiting for write responses..."); */
        } while (numWrites < 3*f+1);

        // Print all the writes received
        /* Logger.log(LogLevel.DEBUG, "Writes received: " + writeResponses);

        Logger.log(LogLevel.DEBUG, "Value to write: " + blockToWrite); */
        return blockToWrite;
    }

    public Block waitForAccepts(Block candidate) throws InterruptedException, ExecutionException {
        // Start a counter to keep track of the time
        long startTime = System.currentTimeMillis();
        Block blockToAppend = null;

        // check if a quorum has already been reached
        int numAccepts;
        do {
            numAccepts = 0;
            Thread.sleep(500);

            // Now we proceed to decide the value to append
            Map<Block, Integer> count = new HashMap<>();
            for (Block s : acceptedValues) {
                if (count.containsKey(s)) {
                    count.put(s, count.get(s) + 1);
                } else {
                    count.put(s, 1);
                }
                numAccepts++;
            }

            int max = 0;
            for (Map.Entry<Block, Integer> entry : count.entrySet()) {
                if (entry.getValue() > max) {
                    max = entry.getValue();
                    blockToAppend = entry.getKey();
                }
            }

            if ((max >= (2*f + 1)) && blockToAppend.equals(candidate))
                break;

            // Check if the time has exceeded the maximum wait time
            if (System.currentTimeMillis() - startTime > this.maxWaitTime) {
                Logger.log(LogLevel.ERROR, "Max wait time exceeded");
                this.aborted = true;
                return null;
            }

            blockToAppend = null;
           /*  Logger.log(LogLevel.DEBUG, "Still waiting for accept responses..."); */
        } while (numAccepts < 3*f+1);

        // Print all the writes received
        /* Logger.log(LogLevel.INFO, "Accepts received: " + acceptedValues);

        Logger.log(LogLevel.INFO, "Value to append: " + blockToAppend); */
        return blockToAppend;
    }

    public Block decideBlock() {
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

            // pick value to write
            TimestampValuePair candidate = getValueFromCollected();

            // Broadcast write
            broadcastWrite(candidate);
            // Wait for writes
            Block blockToWrite = waitForWrites(candidate.getValue());

            // If the value to write is null, then abort
            if (blockToWrite == null) {
                this.aborted = true;
                return null;
            }

            // Broadcast ACCEPT
            broadcastAccept(blockToWrite);

            // Wait for accepts
            Block blockToAppend = waitForAccepts(candidate.getValue());

            // If the value to append is null, then abort
            if (blockToAppend == null) {
                this.aborted = true;
                return null;
            }

            this.decidedBlock = blockToAppend;

            return blockToAppend;
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            return null;
        }
    }

    public Block getDecidedBlock() {
        return decidedBlock;
    }

    public boolean isAborted() {
        return aborted;
    }

    public void setBlockchainMostRecentWrite(TimestampValuePair write) {
        state.setMostRecentWrite(write);
    }

    public void setBlockProposed(Block blockProposed) {
        this.blockProposed = blockProposed;
    }
}
