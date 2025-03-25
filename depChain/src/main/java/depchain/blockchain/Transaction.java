package depchain.blockchain;

import java.util.ArrayList;



public class Transaction {

    private long nonce;
    private String sender;
    private String recipient;
    private double amount;
    private String signature;
    private String data;
    // TODO: need to add gas??


    public Transaction(long nonce, String sender, String recipient, double amount, String signature, String data) {
        this.nonce = nonce;
        this.sender = sender;
        this.recipient = recipient;
        this.amount = amount;
        this.signature = signature;
        this.data = data;
    }


    public class TransactionBuilder {
        private long nonce;
        private String sender;
        private String recipient;
        private double amount;
        private String signature;
        private String data;

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

        public Transaction build() {
            return new Transaction(nonce, sender, recipient, amount, signature, data);
        }
    }
}