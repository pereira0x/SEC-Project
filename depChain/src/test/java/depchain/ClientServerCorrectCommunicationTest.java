package depchain;

import org.junit.FixMethodOrder;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.runners.MethodSorters;

import depchain.blockchain.BlockchainMember;
import depchain.client.DepChainClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Unit test for Client-Server correct communication.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ClientServerCorrectCommunicationTest {

    private List<BlockchainMember> servers = new ArrayList<>();

    /*
     * @Test public void testClientServerCorrectCommunication() throws InterruptedException { // Start Blockchain
     * Members in separate threads Thread server1Thread = new Thread(() -> servers.add(new BlockchainMember(1, 8001, 1,
     * 1))); Thread server2Thread = new Thread(() -> servers.add(new BlockchainMember(2, 8002, 1, 1))); Thread
     * server3Thread = new Thread(() -> servers.add(new BlockchainMember(3, 8003, 1, 1))); Thread server4Thread = new
     * Thread(() -> servers.add(new BlockchainMember(4, 8004, 1, 1))); server1Thread.start(); server2Thread.start();
     * server3Thread.start(); server4Thread.start(); // Start clients // Start a thread for the 2 client -> only for
     * full establishment new Thread(() -> { DepChainClient client1 = new DepChainClient(6, 9002, 1); }).start();
     * DepChainClient client1 = new DepChainClient(5, 9001, 1); String response = client1.append("Hello"); // blockchain
     * is of type ArrayList<String> boolean messageReceived = response.equals("Hello"); // Check if all nodes have
     * committed the value for (BlockchainMember server : servers) { messageReceived = messageReceived &&
     * server.getBlockchain().contains("Hello"); } assertTrue(messageReceived,
     * "Message was not stored in the Blockchain."); }
     */
}
