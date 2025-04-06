package depchain.blockchain.block;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

import depchain.blockchain.Transaction;

public class Block implements Serializable {

    private static final long serialVersionUID = 1L;

    private String blockHash;
    private String previousBlockHash;
    private ArrayList<Transaction> transactions;

    public Block() {
        // Default constructor
    }

    public Block(String blockHash, String previousBlockHash, ArrayList<Transaction> transactions) {
        this.blockHash = blockHash;
        this.previousBlockHash = previousBlockHash;
        this.transactions = transactions;
    }

    public Block(String blockHash, String previousBlockHash, ArrayList<Transaction> transactions,
            Map<String, Long> balances) {
        this.blockHash = blockHash;
        this.previousBlockHash = previousBlockHash;
        this.transactions = transactions;
    }

    // Getters and setters
    public String getBlockHash() {
        return blockHash;
    }

    public void setBlockHash(String blockHash) {
        this.blockHash = blockHash;
    }

    public String getPreviousBlockHash() {
        return previousBlockHash;
    }

    public void setPreviousBlockHash(String previousBlockHash) {
        this.previousBlockHash = previousBlockHash;
    }

    public ArrayList<Transaction> getTransactions() {
        return transactions;
    }

    public void setTransactions(ArrayList<Transaction> transactions) {
        this.transactions = transactions;
    }

    // toString method
    @Override
    public String toString() {
        return "Block{" + "blockHash='" + blockHash + '\'' + ", previousBlockHash='" + previousBlockHash + '\''
                + ", transactions=" + transactions + '}';
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Block) {
            Block other = (Block) obj;
            return blockHash.equals(other.blockHash) && previousBlockHash.equals(other.previousBlockHash)
                    && transactions.equals(other.transactions);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(blockHash, previousBlockHash, transactions);
    }

    // BlockBuilder class
    public static class BlockBuilder {
        private String blockHash;
        private String previousBlockHash;
        private ArrayList<Transaction> transactions;

        public BlockBuilder(ArrayList<Transaction> transactions, String previousBlockHash) {
            // System.out.println("BlockBuilder constructor called");
            // System.out.println("transactions: " + transactions);
            this.transactions = transactions;
            this.previousBlockHash = previousBlockHash;
        }

        public BlockBuilder setBlockHash(String blockHash) {
            this.blockHash = blockHash;
            return this;
        }

        public Block build() {
            return new Block(blockHash, previousBlockHash, transactions);
        }
    }
}
