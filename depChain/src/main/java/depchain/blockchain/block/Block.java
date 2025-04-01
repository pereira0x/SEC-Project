package depchain.blockchain.block;

import java.util.ArrayList;
import java.util.Map;

import depchain.blockchain.Transaction;

public class Block {

    private String blockHash;
    private String previousBlockHash;
    private ArrayList<Transaction> transactions;
    private BlockState state;

    public Block() {
        // Default constructor
    }

    public Block(String blockHash, String previousBlockHash, ArrayList<Transaction> transactions, BlockState state) {
        this.blockHash = blockHash;
        this.previousBlockHash = previousBlockHash;
        this.transactions = transactions;
        this.state = state;
    }

    public Block(String blockHash, String previousBlockHash, ArrayList<Transaction> transactions, Map<String, Long> balances) {
        this.blockHash = blockHash;
        this.previousBlockHash = previousBlockHash;
        this.transactions = transactions;
        this.state = new BlockState(balances);
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

    public BlockState getBlockState() {
        return state;
    }

    public void setState(BlockState state) {
        this.state = state;
    }

    public Map<String, Long> getBalances() {
        return state.getBalances();
    }

    // toString method
    @Override
    public String toString() {
        return "Block{"
                + "blockHash='" + blockHash + '\''
                + ", previousBlockHash='" + previousBlockHash + '\''
                + ", transactions=" + transactions
                + ", state=" + state
                + '}';
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Block) {
            Block other = (Block) obj;
            return blockHash.equals(other.blockHash) && previousBlockHash.equals(other.previousBlockHash) && transactions.equals(other.transactions) && state.equals(other.state);
        }
        return false;
    }

    // BlockBuilder class
    public static class BlockBuilder {
        private String blockHash;
        private String previousBlockHash;
        private ArrayList<Transaction> transactions;
        private BlockState state;

        public BlockBuilder(ArrayList<Transaction> transactions, String previousBlockHash) {
            this.transactions = transactions;
            this.previousBlockHash = previousBlockHash;
        }

        public BlockBuilder setBlockHash(String blockHash) {
            this.blockHash = blockHash;
            return this;
        }

        public BlockBuilder setBlockState(BlockState state) {
            this.state = state;
            return this;
        }

        public BlockBuilder setBalances(Map<String, Long> balances) {
            this.state = new BlockState(balances);
            return this;
        }

        public Block build() {
            return new Block(blockHash, previousBlockHash, transactions, state);
        }
    }
}
