package depchain;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.FixMethodOrder;
import org.junit.jupiter.api.Test;
import org.junit.runners.MethodSorters;

import depchain.client.DepChainClient;
import depchain.blockchain.BlockchainMember;

/**
 * Unit test for MessageSender.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ClientServerCorrectCommunicationTest {

    @Test
    public void ClientServerCorrectCommunicationTest() {
        BlockchainMember server = new BlockchainMember(1, 8001, 1,1);
        DepChainClient client = new DepChainClient(5, 9001);

        client.append("Hello");
        assertTrue(server.getBlockchain().contains("Hello"));
    }

    @Test
    public void ClientServerCorrectCommunicationTest2() {
        assertTrue(true);
    }

}
