package depchain;

import org.junit.FixMethodOrder;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.runners.MethodSorters;

import depchain.blockchain.BlockchainMember;
import depchain.client.DepChainClient;

/**
 * Unit test for MessageSender.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ClientServerCorrectCommunicationTest {

    @Test
    public void ClientServerCorrectCommunicationTest() {
        BlockchainMember server = new BlockchainMember(1, 8001, 1, 1);
        DepChainClient client = new DepChainClient(5, 9001);

        client.append("Hello");
        assertTrue(server.getBlockchain().contains("Hello"));
    }

}
