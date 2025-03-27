package depchain.blockchain.block;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import depchain.blockchain.Transaction;

public class Block {
    private String blockHash;
    private String previousBlockHash;
    private Map<Long, Transaction> transactions;
    private State state;

    // Getters and setters
    public String getBlockHash() { return blockHash; }
    public void setBlockHash(String blockHash) { this.blockHash = blockHash; }

    public String getPreviousBlockHash() { return previousBlockHash; }
    public void setPreviousBlockHash(String previousBlockHash) { this.previousBlockHash = previousBlockHash; }

    public Map<Long, Transaction> getTransactions() { return transactions; }
    public void setTransactions(Map<Long, Transaction> transactions) { this.transactions = transactions; }

    public State getState() { return state; }
    public void setState(State state) { this.state = state; }
}