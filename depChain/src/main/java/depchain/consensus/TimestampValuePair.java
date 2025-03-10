package depchain.consensus;

import java.io.Serializable;

public class TimestampValuePair implements Serializable {
    private static final long serialVersionUID = 1L;
    public final int timestamp;
    public final String value;

    public TimestampValuePair(int timestamp, String value) {
        this.timestamp = timestamp;
        this.value = value;
    }

    public int getTimestamp() {
        return timestamp;
    }

    public String getValue() {
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
