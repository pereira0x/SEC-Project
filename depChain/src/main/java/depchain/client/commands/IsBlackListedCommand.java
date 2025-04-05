package depchain.client.commands;

import depchain.client.DepChainClient;
import depchain.utils.Logger;
import depchain.utils.Logger.LogLevel;
import depchain.library.ClientLibrary;

public class IsBlackListedCommand implements Command {

    private final DepChainClient client;

    public IsBlackListedCommand(DepChainClient client) {
        this.client = client;
    }

    @Override
    public void execute(String[] args, ClientLibrary clientLib) {
        if (args.length != 0 || !args[0].matches("\\d+")) {
            Logger.log(LogLevel.ERROR, getUsage());
            return;
        }

        int userId = Integer.parseInt(args[0]);

        // Check if the user is blacklisted

        Logger.log(LogLevel.INFO, "Client sending get black list request...");
        try {
            // TODO: Implement the getBlackList method in ClientLibrary
        } catch (Exception e) {
            Logger.log(LogLevel.ERROR, "Failed to get black list: " + e.getMessage());
        }
    }

    @Override
    public String getUsage() {
        return "Usage: isBlackListed <userId>";
    }
}