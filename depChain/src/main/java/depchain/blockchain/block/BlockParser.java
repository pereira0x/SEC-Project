package depchain.blockchain.block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import com.fasterxml.jackson.databind.ObjectMapper;

import depchain.blockchain.Transaction;
import depchain.utils.Logger;
import depchain.utils.Logger.LogLevel;
import depchain.utils.ByteArrayWrapper;

public class BlockParser {
    
    public static Block parseBlock(JSONObject jsonFile) throws Exception {

        String blockHash = jsonFile.getString("block_hash");
        String previousBlockHash = jsonFile.optString("previous_block_hash", null);

        
        JSONArray transactionsJson = jsonFile.getJSONArray("transactions");
        ArrayList<Transaction> transactions = new ArrayList<>();

        // Handle transactions being an array instead of an object
    if (jsonFile.has("transactions")) {
            for (int i = 0; i < transactionsJson.length(); i++) {
                JSONObject transactionJson = transactionsJson.getJSONObject(i);
                ByteArrayWrapper sig = new ByteArrayWrapper(transactionJson.getString("signature").getBytes());
                Transaction t = new Transaction.TransactionBuilder()
                        .setNonce(Long.parseLong(transactionJson.getString("nonce")))
                        .setSender(transactionJson.getString("sender"))
                        .setRecipient(transactionJson.getString("recipient"))
                        .setAmount(Long.parseLong(transactionJson.getString("amount")))
                        .setSignature(sig)
                        .setData(transactionJson.getString("data"))
                        .setStatus(Transaction.TransactionStatus.valueOf(transactionJson.getString("status")))
                        .build();
                transactions.add(t);
            }
        } else {
            throw new IllegalArgumentException("Invalid transactions format");
        }

        Map<String, Long> balances = new HashMap<>();
        JSONObject stateJson = jsonFile.getJSONObject("state");
        for (String accountAddress : stateJson.keySet()) {
            JSONObject account = stateJson.getJSONObject(accountAddress);
            Long balance = account.getLong("balance");
            balances.put(accountAddress, balance);
        }

       /*  return new Block(blockHash, previousBlockHash, transactions, balances); */
       return new Block.BlockBuilder(transactions, previousBlockHash)
                .setBlockHash(blockHash)
                .build();
    }

    public static JSONObject blockToJson(Block block) {
        try {
            // First convert the Block to a JSON string using Jackson
            ObjectMapper objectMapper = new ObjectMapper();
            String blockAsString = objectMapper.writeValueAsString(block);
            
            // Then create a JSONObject from the string
            return new JSONObject(blockAsString);
        } catch (Exception e) {
            Logger.log(LogLevel.ERROR, "Error converting block to JSON: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}
