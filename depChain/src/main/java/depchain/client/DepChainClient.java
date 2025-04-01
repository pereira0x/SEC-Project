package depchain.client;

import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

import depchain.library.ClientLibrary;
import depchain.network.PerfectLink;
import depchain.utils.Config;
import depchain.utils.Logger;
import depchain.utils.Logger.LogLevel;
import io.github.cdimascio.dotenv.Dotenv;

public class DepChainClient {

    private ClientLibrary clientLib;
    private int clientPort;
    private int clientId;
    private final int f;
    private final List<Integer> processIds; // All node IDs (no clients).

    // constructor
    public DepChainClient(int clientId, int clientPort, int f) {
        this.clientId = clientId;
        this.clientPort = clientPort;
        this.f = f;
        this.processIds = Arrays.asList(1, 2, 3, 4);

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

        PerfectLink pl;
        try {
            pl = new PerfectLink(clientId, clientPort, Config.processAddresses, Config.getPrivateKey(clientId),
                    Config.publicKeys);
        } catch (Exception e) {
            Logger.log(LogLevel.ERROR, "Failed to create PerfectLink: " + e.getMessage());
            return;
        }

        this.clientLib = new ClientLibrary(pl, 1, processIds, clientId, f);
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            Logger.log(LogLevel.ERROR, "Usage: DepChainClient <clientId> <clientPort>");
            return;
        }

        int clientId = Integer.parseInt(args[0]);
        int clientPort = Integer.parseInt(args[1]);

        DepChainClient client = new DepChainClient(clientId, clientPort, 1);

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
                /* case "append":
                    if (parts.length < 2 || parts.length > 2) {
                        Logger.log(LogLevel.ERROR, "Usage: append <message>");
                    }
                    client.append(parts[1]);
                    break; */
                case "transfer":
                    if (parts.length < 3 || parts.length > 3) {
                        Logger.log(LogLevel.ERROR, "Usage: transfer <recipientId> <amount>");
                    }
                    client.transferDepcoin(Integer.parseInt(parts[1]), Long.parseLong(parts[2]));
                    break;
                case "exit":
                    break;
                default:
                    Logger.log(LogLevel.ERROR, "Unknown command: " + command);
                    break;
            }
        }
        Logger.log(LogLevel.INFO, "Exiting...");
        scanner.close();
        // stop the client
        System.exit(0);
    }

   /*  // Now you can access clientLib in other methods
    public String append(String message) {
        Logger.log(LogLevel.INFO, "Client sending append request...");
        try {
            String response = clientLib.append(message);
            Logger.log(LogLevel.INFO, "Client received response from f+1: " + response);
            return response;
        } catch (Exception e) {
            Logger.log(LogLevel.ERROR, "Failed to append message: " + e.getMessage());
            return null;
        }
    } */


    public String transferDepcoin(int recipientId, Long amount) {
        Logger.log(LogLevel.INFO, "Client sending transfer request...");
        try {
            String response = clientLib.transferDepcoin(recipientId, amount);
            Logger.log(LogLevel.INFO, "Client received response from f+1: " + response);
            return response;
        } catch (Exception e) {
            Logger.log(LogLevel.ERROR, "Failed to transfer Depcoin: " + e.getMessage());
            return null;
        }
    }
}
