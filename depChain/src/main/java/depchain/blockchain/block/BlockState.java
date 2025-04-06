package depchain.blockchain.block;

import java.io.Serializable;
import java.util.Map;
import java.util.Objects;

public class BlockState implements Serializable {
    private static final long serialVersionUID = 1L; // Add a serialVersionUID for version control
    private Map<String, Long> balances;

    public BlockState(Map<String, Long> balances) {
        this.balances = balances;
    }

    // Getters and setters
    public Map<String, Long> getBalances() {
        return balances;
    }

    public void setBalances(Map<String, Long> balances) {
        this.balances = balances;
    }

    @Override
    public String toString() {
        String str = "State{";
        for (Map.Entry<String, Long> entry : balances.entrySet()) {
            str += " " + entry.getKey() + ": " + entry.getValue() + ",";
        }
        str = str.substring(0, str.length() - 1); // Remove the last comma
        str += " }";
        return str;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof BlockState) {
            BlockState other = (BlockState) obj;
            return balances.equals(other.balances);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(balances);
    }
}
