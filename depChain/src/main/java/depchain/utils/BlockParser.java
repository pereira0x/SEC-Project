package depchain.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import depchain.blockchain.block.*;
import java.util.Map;
import depchain.blockchain.Transaction;


public class BlockParser {
  public static Block parseBlock(String json) throws Exception {
      ObjectMapper objectMapper = new ObjectMapper();
      JsonNode rootNode = objectMapper.readTree(json);

      Block block = new Block();
      block.setBlockHash(rootNode.get("block_hash").asText());
      block.setPreviousBlockHash(rootNode.get("previous_block_hash").isNull() ? null : rootNode.get("previous_block_hash").asText());

      JsonNode transactionsNode = rootNode.get("transactions");
      Map<Long, Transaction> transactions = objectMapper.convertValue(transactionsNode, new TypeReference<Map<Long, Transaction>>() {});
      block.setTransactions(transactions);


      JsonNode stateNode = rootNode.get("state");
      State state = objectMapper.convertValue(stateNode, State.class);
      block.setState(state);

      return block;
  }
}
