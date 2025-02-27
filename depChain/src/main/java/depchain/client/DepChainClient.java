package depchain.client;

import java.net.*;
import java.security.KeyPair;

import depchain.library.ClientLibrary;
import depchain.network.PerfectLink;
import depchain.utils.Config;
import io.github.cdimascio.dotenv.Dotenv;


public class DepChainClient {
    public static void main(String[] args) throws Exception {
        // Usage: java DepChainClient <clientPort>
        if (args.length < 1) {
            System.out.println("Usage: DepChainClient <clientPort>");
            return;
        }
        Dotenv dotenv = Dotenv.load();
        int clientPort = Integer.parseInt(args[0]);
        // For simplicity, assume leader is process 1 on localhost:8001.
        InetSocketAddress leaderAddr = new InetSocketAddress("localhost", 8001);

        // Load configuration from config.txt and resources folder.
        // Load configuration from config.txt and resources folder.
        String configFilePath = dotenv.get("CONFIG_FILE_PATH");
        String keysFolderPath = dotenv.get("KEYS_FOLDER_PATH");

        if (configFilePath == null || keysFolderPath == null) {
            System.err.println("Environment variables CONFIG_FILE_PATH or KEYS_FOLDER_PATH are not set.");
            return;
        }

        // Load configuration from environment variables
        Config.loadConfiguration(configFilePath, keysFolderPath);

        // Create PerfectLink for the client.
        int clientId = 5;
        PerfectLink pl = new PerfectLink(clientId, clientPort, Config.processAddresses, Config.getPrivateKey(clientId), Config.publicKeys);
        ClientLibrary clientLib = new ClientLibrary(pl, 1, leaderAddr, clientId);
        
        System.out.println("Client " + clientId + " sending append request...");
        String response = clientLib.append("Hello, DepChain!");
        System.out.println("Client received response: " + response);
    }
}
