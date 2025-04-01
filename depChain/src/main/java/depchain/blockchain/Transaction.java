package depchain.blockchain;

import java.io.Serializable;
import java.util.Objects;


public class Transaction implements Serializable {

    private static final long serialVersionUID = 1L;

    // enum
    public enum TransactionType {
        TRANSFER_DEPCOIN,
        TRANSFER_IST_COIN,
        TRANSFER_FROM_IST_COIN,
        SET_BLACKLIST,
    }

    private long nonce;
    private String sender;
    private String recipient;
    private double amount;
    private String signature;
    private String data;
    private TransactionType type;

    public Transaction(long nonce, String sender, String recipient, double amount, String signature, String data,
            TransactionType type) {
        this.nonce = nonce;
        this.sender = sender;
        this.recipient = recipient;
        this.amount = amount;
        this.signature = signature;
        this.data = data;
        this.type = type;
    }

    public long getNonce() {
        return nonce;
    }

    @Override
    public String toString() {
        return "Transaction{" +
                "nonce=" + nonce +
                ", sender='" + sender + '\'' +
                ", recipient='" + recipient + '\'' +
                ", amount=" + amount +
                ", signature='" + signature + '\'' +
                ", data='" + data + '\'' +
                ", type=" + type +
                '}';
    }

    @Override
    public boolean equals(Object obj) {
        // TODO: consider signature and data
        if (obj instanceof Transaction) {
            Transaction other = (Transaction) obj;
            return nonce == other.nonce &&
                    sender.equals(other.sender) &&
                    recipient.equals(other.recipient) &&
                    amount == other.amount &&
                    type == other.type;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(nonce, sender, recipient, amount, type);
    }

    public static class TransactionBuilder {
        private long nonce;
        private String sender;
        private String recipient;
        private double amount;
        private String signature;
        private String data;
        private TransactionType type;

        public TransactionBuilder() {
        }

        public TransactionBuilder setNonce(long nonce) {
            this.nonce = nonce;
            return this;
        }

        public TransactionBuilder setSender(String sender) {
            this.sender = sender;
            return this;
        }

        public TransactionBuilder setRecipient(String recipient) {
            this.recipient = recipient;
            return this;
        }

        public TransactionBuilder setAmount(double amount) {
            this.amount = amount;
            return this;
        }

        public TransactionBuilder setSignature(String signature) {
            this.signature = signature;
            return this;
        }

        public TransactionBuilder setData(String data) {
            this.data = data;
            return this;
        }

        public TransactionBuilder setType(TransactionType type) {
            this.type = type;
            return this;
        }

        public Transaction build() {
            return new Transaction(nonce, sender, recipient, amount, signature, data, type);
        }
    }
}