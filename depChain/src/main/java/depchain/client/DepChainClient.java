package depchain.client;

import java.net.*;
import java.security.KeyPair;

import depchain.library.ClientLibrary;
import depchain.network.PerfectLink;
import depchain.utils.Config;
import io.github.cdimascio.dotenv.Dotenv;
import depchain.utils.Logger;
import depchain.utils.Logger.LogLevel;
import java.util.Scanner;

public class DepChainClient {

    private ClientLibrary clientLib;
    private int clientPort;
    private int clientId;

    // constructor
    public DepChainClient(int clientId, int clientPort) {
        this.clientId = clientId;
        this.clientPort = clientPort;


        Dotenv dotenv = Dotenv.load();
        String configFilePath = dotenv.get("CONFIG_FILE_PATH");
        String keysFolderPath = dotenv.get("KEYS_FOLDER_PATH");

        if (configFilePath == null || keysFolderPath == null) {
            Logger.log(LogLevel.ERROR, "Environment variables CONFIG_FILE_PATH or KEYS_FOLDER_PATH are not set.");
            return;
        }

        try {
            Config.loadConfiguration(configFilePath, keysFolderPath);
        } catch (Exception e) {
            Logger.log(LogLevel.ERROR, "Failed to load configuration: " + e.getMessage());
            return;
        }

        // Assume leader is the always running process with id 1 on localhost:8001
        InetSocketAddress leaderAddr = new InetSocketAddress("localhost", 8001);

        PerfectLink pl;
        try {
            pl = new PerfectLink(clientId, clientPort, Config.processAddresses, Config.getPrivateKey(clientId),
                    Config.publicKeys);
        } catch (Exception e) {
            Logger.log(LogLevel.ERROR, "Failed to create PerfectLink: " + e.getMessage());
            return;
        }
        
        this.clientLib = new ClientLibrary(pl, 1, leaderAddr, clientId);
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            Logger.log(LogLevel.ERROR, "Usage: DepChainClient <clientId> <clientPort>");
            return;
            }

        int clientId = Integer.parseInt(args[0]);
        int clientPort = Integer.parseInt(args[1]);

        DepChainClient client = new DepChainClient(clientId, clientPort);

        final Scanner scanner = new Scanner(System.in);

        String line = "";
        while (!line.equals("exit")) {
            System.out.flush();
            System.out.print("> ");

            line = scanner.nextLine();

            // empty line
            if (line.isEmpty()) {
                continue;
            }

            // command is first word, rest is arguments
            String[] parts = line.split(" ");
            String command = parts[0];
            switch (command) {
                case "append":
                    if (parts.length < 2 || parts.length > 2) {
                        Logger.log(LogLevel.ERROR, "Usage: append <message>");
                    }
                    client.append(parts[1]);
                case "exit":
                    break;
                default:
                    Logger.log(LogLevel.ERROR, "Unknown command: " + command);
            }
        }
        Logger.log(LogLevel.INFO, "Exiting...");
        scanner.close();
        // stop the client
        System.exit(0);
    }

    // Now you can access clientLib in other methods
    public void append(String message) {
        Logger.log(LogLevel.INFO, "Client sending append request...");
        try {
            String response = clientLib.append(message);
            Logger.log(LogLevel.INFO, "Client received response: " + response);
        } catch (Exception e) {
            Logger.log(LogLevel.ERROR, "Failed to append message: " + e.getMessage());
        }
    }
}