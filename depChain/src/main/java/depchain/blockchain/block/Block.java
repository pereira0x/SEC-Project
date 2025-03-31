package depchain.blockchain.block;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import depchain.blockchain.Transaction;

public class Block {
    private String blockHash;
    private String previousBlockHash;
    private Map<Long, Transaction> transactions;
    private State state;

    public Block() {
        // Default constructor
    }

    public Block(String blockHash, String previousBlockHash, Map<Long, Transaction> transactions, State state) {
        this.blockHash = blockHash;
        this.previousBlockHash = previousBlockHash;
        this.transactions = transactions;
        this.state = state;
    }

    public Block(String blockHash, String previousBlockHash, Map<Long, Transaction> transactions, Map<String, Long> balances) {
        this.blockHash = blockHash;
        this.previousBlockHash = previousBlockHash;
        this.transactions = transactions;
        this.state = new State(balances);
    }

    // Getters and setters
    public String getBlockHash() { return blockHash; }
    public void setBlockHash(String blockHash) { this.blockHash = blockHash; }

    public String getPreviousBlockHash() { return previousBlockHash; }
    public void setPreviousBlockHash(String previousBlockHash) { this.previousBlockHash = previousBlockHash; }

    public Map<Long, Transaction> getTransactions() { return transactions; }
    public void setTransactions(Map<Long, Transaction> transactions) { this.transactions = transactions; }

    public State getState() { return state; }
    public void setState(State state) { this.state = state; }

    public List<Transaction> getTransactionList() {
        return transactions.values().stream().toList();
    }
    
    public Map<String, Long> getBalances() {
        return state.getBalances();
    }

    // toString method
    @Override
    public String toString() {
        return "Block{" +
                "blockHash='" + blockHash + '\'' +
                ", previousBlockHash='" + previousBlockHash + '\'' +
                ", transactions=" + transactions +
                ", state=" + state +
                '}';
    }
}