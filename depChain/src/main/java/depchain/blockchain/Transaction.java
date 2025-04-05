package depchain.blockchain;

import java.io.Serializable;
import java.util.Objects;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;

import depchain.utils.ByteArrayWrapper;

public class Transaction implements Serializable {

    private static final long serialVersionUID = 1L;

    // enum
    public enum TransactionType {
      TRANSFER_DEPCOIN,
      TRANSFER_IST_COIN,
      TRANSFER_FROM_IST_COIN,
      GET_DEPCOIN_BALANCE,
      ADD_BLACKLIST,
      GET_ISTCOIN_BALANCE,
      IS_BLACKLISTED,
      APPROVE,
      ALLOWANCE
    }

    public enum TransactionStatus {
        PENDING,
        CONFIRMED,
        REJECTED,
    }

    private long nonce;
    private String sender;
    private String recipient;
    private long amount;
    private ByteArrayWrapper signature;
    private String data;
    private TransactionType type;
    private TransactionStatus status;

    // optional
    private String spender;

    public Transaction(long nonce, String sender, String recipient, long amount, ByteArrayWrapper signature, String data,
            TransactionType type, TransactionStatus status, String spender) {
        this.nonce = nonce;
        this.sender = sender;
        this.recipient = recipient;
        this.amount = amount;
        this.signature = signature;
        this.data = data;
        this.type = type;
        this.status = status;

        // optional
        this.spender = spender;
    }

    public long getNonce() {
        return nonce;
    }

    public String getSender() {
        return sender;
    }

    public String getRecipient() {
        return recipient;
    }

    public long getAmount() {
        return amount;
    }

    public byte[] getSignature() {
        return signature.getData();
    }

    public String getData() {
        return data;
    }

    public TransactionType getType() {
        return type;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public String getSpender() {
        return spender;
    }

    public void setStatus(TransactionStatus status) {
        this.status = status;
    }

    public void setSignature(ByteArrayWrapper signature) {
        this.signature = signature;
    }

    public byte[] toByteArray() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            
            // Write all transaction fields
            oos.writeLong(nonce);
            oos.writeUTF(sender != null ? sender : "");
            oos.writeUTF(recipient != null ? recipient : "");
            oos.writeLong(amount);
            oos.writeUTF(data != null ? data : "");
            oos.writeInt(type != null ? type.ordinal() : -1);
            oos.writeInt(status != null ? status.ordinal() : -1);
            
            oos.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Error serializing transaction", e);
        }
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
                ", status=" + status +
                '}';
    }

    @Override
    public boolean equals(Object obj) {
        // TODO: consider data
        if (obj instanceof Transaction) {
            Transaction other = (Transaction) obj;
            // System.out.println("-------------------- sig: " + signature);
            // System.out.println("-------------------- other sig: " + other.signature);
            return nonce == other.nonce &&
                    sender.equals(other.sender) &&
                    recipient.equals(other.recipient) &&
                    amount == other.amount &&
                    signature.equals(other.signature) &&
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
        private long amount;
        private ByteArrayWrapper signature;
        private String data;
        private TransactionType type;
        private TransactionStatus status;

        // optional
        private String spender;

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

        public TransactionBuilder setAmount(long amount) {
            this.amount = amount;
            return this;
        }

        public TransactionBuilder setSignature(ByteArrayWrapper signature) {
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

        public TransactionBuilder setStatus(TransactionStatus status) {
            this.status = status;
            return this;
        }

        public TransactionBuilder setSpender(String spender) {
          this.spender = spender;
          return this;
        }

        public Transaction build() {
            return new Transaction(nonce, sender, recipient, amount, signature, data, type, status, spender);
        }
    }
}