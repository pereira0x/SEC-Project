package depchain;

import java.net.*;
import java.security.KeyPair;

public class DepChainClient {
    public static void main(String[] args) throws Exception {
        // Usage: java DepChainClient <clientPort>
        if (args.length < 1) {
            System.out.println("Usage: DepChainClient <clientPort>");
            return;
        }
        int clientPort = Integer.parseInt(args[0]);
        // For simplicity, assume leader is process 1 on localhost:8001.
        InetSocketAddress leaderAddr = new InetSocketAddress("localhost", 8001);
        // Add client’s address to Config.
        int clientId = ClientIdGenerator.getNextClientId();
        Config.clientAddresses.put(clientId, new InetSocketAddress("localhost", clientPort));
        
        // Generate client key pair.
        KeyPair kp = CryptoUtil.generateKeyPair();
        // Create PerfectLink for the client.
        PerfectLink pl = new PerfectLink(clientId, clientPort, Config.processAddresses, kp.getPrivate(), Config.publicKeys);
        ClientLibrary clientLib = new ClientLibrary(pl, 1, leaderAddr);
        
        System.out.println("Client " + clientId + " sending append request...");
        String response = clientLib.append("Hello, DepChain!");
        System.out.println("Client received response: " + response);
    }
}
