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
        try {
            BlockchainMember server1 = new BlockchainMember(1, 8001, 1, 1);
            Thread.sleep(1500);
            BlockchainMember server2 = new BlockchainMember(2, 8002, 1, 1);
            Thread.sleep(1500);
            BlockchainMember server3 = new BlockchainMember(3, 8003, 1, 1);
            Thread.sleep(1500);
            BlockchainMember server4 = new BlockchainMember(4, 8004, 1, 1);
            Thread.sleep(1500);
            DepChainClient client1 = new DepChainClient(5, 9001);
            Thread.sleep(1500);
            client1.append("Hello");
            assertTrue(server1.getBlockchain().contains("Hello"));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

}
