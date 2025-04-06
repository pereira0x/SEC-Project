package depchain.client;

import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

import depchain.client.commands.CommandManager;
import depchain.client.commands.TransferDepCommand;
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

        this.clientLib = new ClientLibrary(pl, processIds, clientId, f);
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            Logger.log(LogLevel.ERROR, "Usage: DepChainClient <clientId> <clientPort>");
            return;
        }

        int clientId = Integer.parseInt(args[0]);
        int clientPort = Integer.parseInt(args[1]);

        DepChainClient client = new DepChainClient(clientId, clientPort, 1);

        CommandManager commandManager = new CommandManager(client.clientLib);
        commandManager.registerAllCommands(client);

        Logger.log(LogLevel.INFO, "Client started. Type 'help' for a list of commands.");

        Scanner scanner = new Scanner(System.in);
        String line = "";
        while (!line.equals("exit")) {
            System.out.print("> ");
            line = scanner.nextLine();

            if (line.equals("exit"))
                break;
            if (line.isEmpty())
                continue;

            switch (Config.processBehaviors.get(clientId)) {
                case "byzantineClient":
                    Logger.log(LogLevel.WARNING, "Sending 100 commands to the nodes...");
                    for (int i = 0; i < 100; i++) {
                        commandManager.executeCommand(line);
                    }
                default:
                    break;
            }

            commandManager.executeCommand(line);	
        }

        Logger.log(LogLevel.INFO, "Exiting...");
        scanner.close();
        System.exit(0);
    }

}
