package depchain.network;

import java.io.Serializable;
import java.util.Map;

import depchain.blockchain.Transaction;
import depchain.blockchain.block.Block;
import depchain.consensus.State;
import depchain.consensus.TimestampValuePair;
import depchain.utils.ByteArrayWrapper;

public class Message implements Serializable {
    public enum Type {
        READ, STATE, COLLECTED, WRITE, ACCEPT, CLIENT_REQUEST, CLIENT_REPLY, ACK, START_SESSION, ACK_SESSION
    }

    private final Type type;
    private final int epoch; // For our design, epoch doubles as the consensus instance ID.
    private final int senderId; // The sender's ID (for clients, use a distinct range).
    private final int clientId; // All messages relate to a client request in some way, apart from session and acks.
    private final byte[] signature; // Signature over the message content (computed by sender).
    private int nonce; // nonce for the message (computed by sender).
    private Transaction transaction;
    private Block block;

    // Optional fields
    private ByteArrayWrapper sessionKey; // session key for the message (computed by sender).
    private final State state;
    private final Map<Integer, State> statesMap;
    private final TimestampValuePair write;

    private Message(MessageBuilder builder) {
        this.type = builder.type;
        this.epoch = builder.epoch;
        this.senderId = builder.senderId;
        this.clientId = builder.clientId;
        this.signature = builder.signature;
        this.state = builder.state;
        this.statesMap = builder.statesMap;
        this.write = builder.write;
        this.sessionKey = builder.sessionKey;
        this.nonce = builder.nonce;
        this.transaction = builder.transaction;
        this.block = builder.block;
    }

    public Type getType() {
        return type;
    }

    public int getEpoch() {
        return epoch;
    }

    public int getSenderId() {
        return senderId;
    }

    public int getClientId() {
        return clientId;
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

    public Transaction getTransaction() {
        return transaction;
    }

    public Block getBlock() {
        return block;
    }

    public String getBlockHash() {
        if (block != null) {
            return block.getBlockHash();
        }
        return "";
    }

    // Returns a string representation of the content to be signed.
    public String getSignableContent() {

        String content = "";
        content += type.toString();
        content += epoch;
        content += senderId;
        content += clientId;
        content += nonce;
        if (sessionKey != null) {
            content += sessionKey.getData();
        }
        return content;
    }

    public static class MessageBuilder {
        private final Type type;
        private final int epoch;
        private final int senderId;
        private int clientId;
        private byte[] signature;
        private ByteArrayWrapper sessionKey;
        private State state;
        private Map<Integer, State> statesMap;
        private TimestampValuePair write;
        private int nonce;
        private Transaction transaction;
        private Block block;

        public MessageBuilder(Type type, int epoch, int senderId) {
            this.type = type;
            this.epoch = epoch;
            this.senderId = senderId;
            this.signature = null;
            this.sessionKey = null;
            this.nonce = -1;
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

        public MessageBuilder setTransaction(Transaction transaction) {
            this.transaction = transaction;
            return this;
        }

        public MessageBuilder setBlock(Block block) {
            this.block = block;
            return this;
        }

        public Message build() {
            return new Message(this);
        }
    }

}
