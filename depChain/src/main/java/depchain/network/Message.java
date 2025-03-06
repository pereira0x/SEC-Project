package depchain.network;

import java.io.Serializable;

public class Message implements Serializable {
    public enum Type {
        READ, STATE, WRITE, ACCEPT, DECIDED, CLIENT_REQUEST, CLIENT_REPLY, ACK,
    }

    public final Type type;
    public final int epoch; // For our design, epoch doubles as the consensus instance ID.
    public final String value; // The candidate value (e.g., the string to append).
    public final int senderId; // The sender's ID (for clients, use a distinct range).
    public final byte[] signature; // Signature over the message content (computed by sender).
    public long nonce; // nonce for the message (computed by sender).

    public Message(Type type, int epoch, String value, int senderId, byte[] signature, long nonce) {
        this.type = type;
        this.epoch = epoch;
        this.value = value;
        this.senderId = senderId;
        this.signature = signature;
        this.nonce = nonce;
    }

    public void setNonce(long nonce) {
        this.nonce = nonce;
    }   

    // Returns a string representation of the content to be signed.
    public String getSignableContent() {
        return type.toString() + "|" + epoch + "|" + value + "|" + senderId + "|" + nonce;
    }
}
