package depchain.network;

import java.io.Serializable;

import depchain.utils.ByteArrayWrapper;
import depchain.consensus.State;
import depchain.consensus.TimestampValuePair;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

public class Message implements Serializable {
    public enum Type {
        READ, STATE, COLLECTED, WRITE, ACCEPT, CLIENT_REQUEST, CLIENT_REPLY, ACK, START_SESSION, ACK_SESSION
    }

    private final Type type;
    private final int epoch; // For our design, epoch doubles as the consensus instance ID.
    private String value; // The value (e.g., the string to append).
    private final int senderId; // The sender's ID (for clients, use a distinct range).
    private final byte[] signature; // Signature over the message content (computed by sender).
    private int nonce; // nonce for the message (computed by sender).
    
    // Optional fields
    private ByteArrayWrapper sessionKey; // session key for the message (computed by sender).
    private final State state;
    private final Map<Integer, State> statesMap;
    private final TimestampValuePair write; 

    private Message(MessageBuilder builder) {
        this.type = builder.type;
        this.epoch = builder.epoch;
        this.value = builder.value;
        this.senderId = builder.senderId;
        this.signature = builder.signature;
        this.state = builder.state;
        this.statesMap = builder.statesMap;
        this.write = builder.write;
        this.sessionKey = builder.sessionKey;
        this.nonce = builder.nonce;
    }

    public Type getType() {
        return type;
    }

    public int getEpoch() {
        return epoch;
    }

    public String getValue() {
        return value;
    }

    public int getSenderId() {
        return senderId;
    }

    public byte[] getSignature() {
        return signature;
    }

    public State getState() {
        return state;
    }

    public Map<Integer, State> getStatesMap() {
        return statesMap;
    }

    public TimestampValuePair getWrite() {
        return write;
    }

    public ByteArrayWrapper getSessionKey() {
        return sessionKey;
    }

    public int getNonce() {
        return nonce;
    }

    public void setNonce(int nonce) {
        this.nonce = nonce;
    }

    // Returns a string representation of the content to be signed.
    public String getSignableContent() {

        String content = "";
        content += type.toString();
        content += epoch;
        content += value;
        content += senderId;
        content += nonce;
        if (sessionKey != null) {
            content += sessionKey.getData();
        }

        return content;
    }

    public static class MessageBuilder {
        private final Type type;
        private final int epoch;
        private final String value;
        private final int senderId;
        private byte[] signature;
        private ByteArrayWrapper sessionKey;
        private State state;
        private Map<Integer, State> statesMap;
        private TimestampValuePair write;
        private int nonce;

        public MessageBuilder(Type type, int epoch, String value, int senderId) {
            this.type = type;
            this.epoch = epoch;
            this.value = value;
            this.senderId = senderId;
            this.signature = null;
            this.sessionKey = null;
        }

       public MessageBuilder setNonce(int nonce) {
            this.nonce = nonce;
            return this;
        }

        public MessageBuilder setSessionKey(ByteArrayWrapper sessionKey) {
            this.sessionKey = sessionKey;
            return this;
        }

        public MessageBuilder setState(State state) {
            this.state = state;
            return this;
        }

        public MessageBuilder setStatesMap(Map<Integer, State> statesMap) {
            this.statesMap = statesMap;
            return this;
        }

        public MessageBuilder setWrite(TimestampValuePair write) {
            this.write = write;
            return this;
        }

        public MessageBuilder setSignature(byte[] signature) {
            this.signature = signature;
            return this;
        }

        public Message build() {
            return new Message(this);
        }
    }

}
