package depchain.blockchain;

import java.util.*;

import depchain.network.PerfectLink;
import depchain.utils.Config;

import io.github.cdimascio.dotenv.Dotenv;
import depchain.utils.Logger;
import depchain.utils.Logger.LogLevel;

public class DepChainServer {
    public static void main(String[] args) throws Exception {
        // Usage: java DepChainServer <processId> <port>
        if (args.length < 2) {
            Logger.log(LogLevel.ERROR, "Usage: DepChainServer <processId> <port>");
            return;
        }

        Dotenv dotenv = Dotenv.load();
        int processId = Integer.parseInt(args[0]);
        int port = Integer.parseInt(args[1]);
        int leaderId = 1; // assume process 1 is leader
        List<Integer> allProcessIds = Arrays.asList(1, 2, 3, 4);

        // Load configuration from config.txt and resources folder.
        String configFilePath = dotenv.get("CONFIG_FILE_PATH");
        String keysFolderPath = dotenv.get("KEYS_FOLDER_PATH");

        if (configFilePath == null || keysFolderPath == null) {
            Logger.log(LogLevel.ERROR, "Environment variables CONFIG_FILE_PATH or KEYS_FOLDER_PATH are not set.");
            return;
        }

        // Load configuration from environment variables
        Config.loadConfiguration(configFilePath, keysFolderPath);
        Logger.log(LogLevel.DEBUG, "Configuration loaded from paths: " + configFilePath + ", " + keysFolderPath);

        // Create PerfectLink instance.
        PerfectLink pl = new PerfectLink(processId, port, Config.processAddresses, Config.getPrivateKey(processId),
                Config.publicKeys);
        // Assume maximum Byzantine faults f = 1 for 4 processes.
        BlockchainMember bm = new BlockchainMember(processId, leaderId, allProcessIds, pl, 1);
        Logger.log(LogLevel.INFO, "DepChainServer " + processId + " started on port " + port);
        // The server runs indefinitely.
    }
}
