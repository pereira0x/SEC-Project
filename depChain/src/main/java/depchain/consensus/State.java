package depchain.consensus;

import java.io.Serializable;
import java.util.ArrayList;

import depchain.blockchain.block.Block;

public class State implements Serializable {

    // {(ts, val) , writeset}
    private static final long serialVersionUID = 1L;
    public TimestampValuePair mostRecentWrite;
    public final ArrayList<TimestampValuePair> writeset;

    public State() {
        this.mostRecentWrite = new TimestampValuePair(0, new Block());
        this.writeset = new ArrayList<>();
    }

    public State(TimestampValuePair mostRecentWrite, ArrayList<TimestampValuePair> writeset) {
        this.mostRecentWrite = mostRecentWrite;
        this.writeset = writeset;
    }

    public TimestampValuePair getMostRecentWrite() {
        return mostRecentWrite;
    }

    public ArrayList<TimestampValuePair> getWriteset() {
        return writeset;
    }

    public void addToWriteSet(TimestampValuePair write) {
        writeset.add(write);
    }

    public void setMostRecentWrite(TimestampValuePair write) {
        this.mostRecentWrite = write;
    }

    public ArrayList<Block> getBlockchain() {
        ArrayList<Block> blockchain = new ArrayList<>();
        for (TimestampValuePair write : writeset) {
            blockchain.add(write.getValue());
        }
        return blockchain;
    }

    @Override
    public String toString() {
        return "(" + mostRecentWrite + ", " + writeset + ")";
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof State) {
            State other = (State) obj;
            return mostRecentWrite.equals(other.mostRecentWrite) && writeset.equals(other.writeset);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return mostRecentWrite.hashCode() + writeset.hashCode();
    }
}
