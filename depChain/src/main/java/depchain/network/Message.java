package depchain.network;

import java.io.Serializable;

import depchain.utils.ByteArrayWrapper;
import java.util.List;
import java.util.ArrayList;

public class Message implements Serializable {
    public enum Type {
        READ, STATE, WRITE, ACCEPT, DECIDED, CLIENT_REQUEST, CLIENT_REPLY, ACK, START_SESSION, ACK_SESSION
    }

    public final Type type;
    public final int epoch; // For our design, epoch doubles as the consensus instance ID.
    public String value = ""; // The candidate value (e.g., the string to append).
    public final int senderId; // The sender's ID (for clients, use a distinct range).
    public final byte[] signature; // Signature over the message content (computed by sender).
    public int nonce; // nonce for the message (computed by sender).
    public ByteArrayWrapper sessionKey; // session key for the message (computed by sender).
    public final ArrayList state;

    public Message(Type type, int epoch, String value, int senderId, byte[] signature, int nonce) {
        this(type, epoch, value, senderId, signature, nonce, null, new ArrayList<>());
    }

    public Message(Type type, int epoch, String value, int senderId, byte[] signature, int nonce, ByteArrayWrapper sessionKey) {
        this(type, epoch, value, senderId, signature, nonce, sessionKey, new ArrayList<>());
    }
    
    // For STATE messages
    public Message(Type type, int epoch, String value, int senderId, byte[] signature, int nonce, ByteArrayWrapper sessionKey, ArrayList state) {
        this.type = type;
        this.epoch = epoch;
        this.value = value;
        this.senderId = senderId;
        this.signature = signature;
        this.nonce = nonce;
        this.sessionKey = sessionKey;
        this.state = state;
    }

    public void setNonce(int nonce) {
        this.nonce = nonce;
    }

    // Returns a string representation of the content to be signed.
    public String getSignableContent() {

        // only put non null values
        String content = "";
        content += type.toString();
        content += epoch;
        content += value;
        content += senderId;
        content += nonce;
        if (sessionKey != null) {
            content += sessionKey.getData();
        }
        if (state != null) {
            for (Object obj : state) {
                content += obj.toString();
            }
        }
        return content;
    }
}
