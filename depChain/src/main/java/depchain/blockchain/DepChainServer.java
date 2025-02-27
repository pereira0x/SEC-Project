package depchain.blockchain;

import java.net.*;
import java.util.*;

import depchain.network.PerfectLink;
import depchain.utils.Config;

import java.security.*;

public class DepChainServer {
    public static void main(String[] args) throws Exception {
        // Usage: java DepChainServer <processId> <port>
        if (args.length < 2) {
            System.out.println("Usage: DepChainServer <processId> <port>");
            return;
        }
        int processId = Integer.parseInt(args[0]);
        int port = Integer.parseInt(args[1]);
        int leaderId = 1;  // assume process 1 is leader
        List<Integer> allProcessIds = Arrays.asList(1, 2, 3, 4);

        // Load configuration from config.txt and resources folder.
        Config.loadConfiguration(
            "src/main/java/resources/config/config.txt",
            "src/main/java/resources/keys"
        );

        // Create PerfectLink instance.
        PerfectLink pl = new PerfectLink(processId, port, Config.processAddresses, Config.getPrivateKey(processId), Config.publicKeys);
        // Assume maximum Byzantine faults f = 1 for 4 processes.
        BlockchainMember bm = new BlockchainMember(processId, leaderId, allProcessIds, pl, 1);
        System.out.println("DepChainServer " + processId + " started on port " + port);
        // The server runs indefinitely.
    }
}
