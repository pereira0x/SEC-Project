package depchain.client.commands;

import depchain.client.DepChainClient;
import depchain.utils.Logger;
import depchain.utils.Logger.LogLevel;
import depchain.library.ClientLibrary;

public class ApproveCommand implements Command {

    private final DepChainClient client;

    public ApproveCommand(DepChainClient client) {
        this.client = client;
    }

    @Override
    public void execute(String[] args, ClientLibrary clientLib) {
        if (args.length != 2 || !args[0].matches("\\d+") || !args[1].matches("\\d+")) {
            Logger.log(LogLevel.ERROR, getUsage());
            return;
        }

        int spender = Integer.parseInt(args[0]);
        long amount = Long.parseLong(args[1]);

        // The command caller approves that the spender can spend the amount on their behalf

        Logger.log(LogLevel.INFO, "Client sending approve request...");
        try {
            // TODO: Implement the approve method in ClientLibrary
        } catch (Exception e) {
            Logger.log(LogLevel.ERROR, "Failed to approve request: " + e.getMessage());
        }
    }

    @Override
    public String getUsage() {
        return "Usage: approve <spender> <amount>";
    }
}