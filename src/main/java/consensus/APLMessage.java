package model;

import java.io.Serializable;
import java.util.Arrays;

public class APLMessage implements Serializable {
    private final int senderId;
    private final long sequenceNumber;
    private final MessageType type;
    private final byte[] payload;
    private final byte[] signature;

    public enum MessageType {
        PROPOSE, PREPARE, COMMIT, ACK, DECIDE // TODO: Figure this out
    }

    public APLMessage(int senderId, long sequenceNumber, MessageType type, byte[] payload, byte[] signature) {
        this.senderId = senderId;
        this.sequenceNumber = sequenceNumber;
        this.type = type;
        this.payload = payload;
        this.signature = signature;
    }

    // Getters
    public int getSenderId() { return senderId; }
    public long getSequenceNumber() { return sequenceNumber; }
    public MessageType getType() { return type; }
    public byte[] getPayload() { return payload; }
    public byte[] getSignature() { return signature; }

    @Override
    public String toString() {
        return String.format("APLMessage[sender=%d, seq=%d, type=%s]", senderId, sequenceNumber, type);
    }
}
