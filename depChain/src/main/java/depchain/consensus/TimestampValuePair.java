package depchain.consensus;

import java.io.Serializable;

import depchain.blockchain.block.Block;

public class TimestampValuePair implements Serializable {
    private static final long serialVersionUID = 1L;
    public final int timestamp;
    public final Block value;

    public TimestampValuePair(int timestamp, Block value) {
        this.timestamp = timestamp;
        this.value = value;
    }

    public int getTimestamp() {
        return timestamp;
    }

    public Block getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "(" + timestamp + ", " + value + ")";
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TimestampValuePair) {
            TimestampValuePair other = (TimestampValuePair) obj;
            return timestamp == other.timestamp && value.equals(other.value);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return timestamp ^ value.hashCode();
    }

}
