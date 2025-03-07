package depchain.network;

import java.io.Serializable;

import depchain.utils.ByteArrayWrapper;

public class Message implements Serializable {
    public enum Type {
        READ, STATE, WRITE, ACCEPT, DECIDED, CLIENT_REQUEST, CLIENT_REPLY, ACK, START_SESSION,
    }

    public final Type type;
    public final int epoch; // For our design, epoch doubles as the consensus instance ID.
    public final String value; // The candidate value (e.g., the string to append).
    public final int senderId; // The sender's ID (for clients, use a distinct range).
    public final byte[] signature; // Signature over the message content (computed by sender).
    public long nonce; // nonce for the message (computed by sender).
    public ByteArrayWrapper sessionKey; // session key for the message (computed by sender).

    public Message(Type type, int epoch, String value, int senderId, byte[] signature, long nonce) {
        this.type = type;
        this.epoch = epoch;
        this.value = value;
        this.senderId = senderId;
        this.signature = signature;
        this.nonce = nonce;
    }

    public Message(Type type, int epoch, int senderId, byte[] signature, long nonce, ByteArrayWrapper sessionKey) {
        this.type = type;
        this.epoch = epoch;
        this.value = ""; // Initialize value with an empty string or any default value
        this.senderId = senderId;
        this.signature = signature;
        this.nonce = nonce;
        this.sessionKey = sessionKey;
    }

    public void setNonce(long nonce) {
        this.nonce = nonce;
    }   

    // Returns a string representation of the content to be signed.
    public String getSignableContent() {

        // only put non null values
        String content = "";
        if (type != null) {
            content += type.toString();
        }
        if (epoch != 0) {
            content += epoch;
        }
        if (value != null) {
            content += value;
        }
        if (senderId != 0) {
            content += senderId;
        }
        if (nonce != 0) {
            content += nonce;
        }
        if (sessionKey != null) {
            content += sessionKey.getData();
        }
        return content;
    }
}
