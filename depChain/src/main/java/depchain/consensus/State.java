package depchain.consensus;

import java.util.ArrayList;

import depchain.consensus.TimestampValuePair;
import java.io.Serializable;

public class State implements Serializable {

    // {(ts, val) , writeset}
    private static final long serialVersionUID = 1L;
    public TimestampValuePair mostRecentWrite;
    public final ArrayList<TimestampValuePair> writeset;

    public State() {
        this.mostRecentWrite = new TimestampValuePair(0, "");
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

    public void add(TimestampValuePair write) {
        writeset.add(write);
    }

    public void setMostRecentWrite(TimestampValuePair write) {
        this.mostRecentWrite = write;
    }

    public ArrayList<String> getBlockchain() {
        ArrayList<String> blockchain = new ArrayList<>();
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
