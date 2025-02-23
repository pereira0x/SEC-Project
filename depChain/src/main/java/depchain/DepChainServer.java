package depchain;

import java.net.*;
import java.util.*;
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
        
        // Populate Config.processAddresses (e.g., assume localhost with ports 8001, 8002, 8003, 8004)
        for (int pid : allProcessIds) {
            Config.processAddresses.put(pid, new InetSocketAddress("localhost", 8000 + pid));
        }
        
        // Generate and distribute key pairs.
        // In a real system these would be pre‑distributed; here we generate them on startup.
        PrivateKey myPrivateKey = null;
        for (int pid : allProcessIds) {
            KeyPair kp = CryptoUtil.generateKeyPair();
            Config.publicKeys.put(pid, kp.getPublic());
            if (pid == processId) {
                myPrivateKey = kp.getPrivate();
            }
        }
        
        // Create PerfectLink instance.
        PerfectLink pl = new PerfectLink(processId, port, Config.processAddresses, myPrivateKey, Config.publicKeys);
        // Assume maximum Byzantine faults f = 1 for 4 processes.
        BlockchainMember bm = new BlockchainMember(processId, leaderId, allProcessIds, pl, 1);
        System.out.println("DepChainServer " + processId + " started on port " + port);
        // The server runs indefinitely.
    }
}
