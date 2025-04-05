package depchain.client.commands;

import java.util.HashMap;
import java.util.Map;

import depchain.library.ClientLibrary;
import depchain.client.DepChainClient;

public class CommandManager {
    private final Map<String, Command> commands = new HashMap<>();
    private final ClientLibrary clientLib;

    public CommandManager(ClientLibrary clientLib) {
        this.clientLib = clientLib;
    }

    public void register(String name, Command command) {
        commands.put(name, command);
    }

    public void registerAllCommands(DepChainClient client) {
        register("transferDep", new TransferDepCommand(client));
        register("transferIST", new TransferISTCommand(client));
        register("getDepBalance", new GetDepBalanceCommand(client));
        register("getISTBalance", new GetISTBalanceCommand(client));
        // Register other commands here
    }

    public void executeCommand(String inputLine) {
        String[] parts = inputLine.trim().split("\\s+");
        if (parts.length == 0) return;

        String commandName = parts[0];
        Command command = commands.get(commandName);

        if (command == null) {
            System.out.println("Unknown command: " + commandName);
            return;
        }

        String[] args = new String[parts.length - 1];
        System.arraycopy(parts, 1, args, 0, args.length);
        command.execute(args, this.clientLib);
    }
}
