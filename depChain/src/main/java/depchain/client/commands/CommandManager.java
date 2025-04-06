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
        register("getDepBal", new GetDepBalanceCommand(client));
        register("getISTBal", new GetISTBalanceCommand(client));
        register("addBlackList", new AddToBlackListCommand(client));
        register("removeBlackList", new RemoveFromBlackListCommand(client));
        register("isBlackListed", new IsBlackListedCommand(client));
        register("allowance", new AllowanceCommand(client));
        register("approve", new ApproveCommand(client));
        register("transferFrom", new TransferFromCommand(client));

        register("help", new HelpCommand(this));
        // Register other commands here
    }

    public void executeCommand(String inputLine) {
        String[] parts = inputLine.trim().split("\\s+");
        if (parts.length == 0)
            return;

        String commandName = parts[0];
        Command command = commands.get(commandName);

        if (command == null) {
            System.out.println("Unknown command: " + commandName);
            System.out.println("Type 'help' to see available commands.");
            return;
        }

        String[] args = new String[parts.length - 1];
        System.arraycopy(parts, 1, args, 0, args.length);
        command.execute(args, this.clientLib);
    }

    public void printAllCommands() {
        System.out.println("Available commands:");
        for (Map.Entry<String, Command> entry : commands.entrySet()) {
            String name = entry.getKey();
            String usage = entry.getValue().getUsage();
            System.out.printf("  %-20s%s%n", name, usage);
        }
    }

}
