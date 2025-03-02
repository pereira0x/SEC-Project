package depchain.client;

import java.net.*;
import java.security.KeyPair;

import depchain.library.ClientLibrary;
import depchain.network.PerfectLink;
import depchain.utils.Config;
import io.github.cdimascio.dotenv.Dotenv;
import depchain.utils.Logger;
import depchain.utils.Logger.LogLevel;

public class DepChainClient {
    public static void main(String[] args) throws Exception {
        // Usage: java DepChainClient <clientPort>
        if (args.length < 1 || args.length > 2) {
            Logger.log(LogLevel.ERROR, "Usage: DepChainClient <clientPort>");
            return;
        }

        Dotenv dotenv = Dotenv.load();
        int clientPort = Integer.parseInt(args[0]);
        // For simplicity, assume leader is process 1 on localhost:8001.
        InetSocketAddress leaderAddr = new InetSocketAddress("localhost", 8001);

        String configFilePath = dotenv.get("CONFIG_FILE_PATH");
        String keysFolderPath = dotenv.get("KEYS_FOLDER_PATH");

        if (configFilePath == null || keysFolderPath == null) {
            Logger.log(LogLevel.ERROR, "Environment variables CONFIG_FILE_PATH or KEYS_FOLDER_PATH are not set.");
            return;
        }

        Config.loadConfiguration(configFilePath, keysFolderPath);

        int clientId = 5;
        PerfectLink pl = new PerfectLink(clientId, clientPort, Config.processAddresses, Config.getPrivateKey(clientId),
                Config.publicKeys);
        Logger.log(LogLevel.INFO, "Client library created.");
        ClientLibrary clientLib = new ClientLibrary(pl, 1, leaderAddr, clientId);

        Logger.log(LogLevel.INFO, "Client sending append request...");
        String response = clientLib.append("Hello, DepChain!");
        Logger.log(LogLevel.INFO, "Client received response: " + response);
        String response2 = clientLib.append("Hello, DepChain! 2");
        Logger.log(LogLevel.INFO, "Client received response: " + response2);
    }
}
