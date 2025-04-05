package depchain.client.commands;

import depchain.client.DepChainClient;
import depchain.library.ClientLibrary;
import depchain.utils.Logger;
import depchain.utils.Logger.LogLevel;

public class RemoveFromBlackListCommand implements Command {
        private final DepChainClient client;

    public RemoveFromBlackListCommand(DepChainClient client) {
        this.client = client;
    }

    @Override
    public void execute(String[] args, ClientLibrary clientLib) {
        if (args.length != 1 || !args[0].matches("\\d+") || !args[1].matches("\\d+")) {
            Logger.log(LogLevel.ERROR, "Usage: removeBlackList <userId>");
            return;
        }


        Logger.log(LogLevel.INFO, "Client sending remove from black list request...");
        try {
            // TODO: Implement the removeFromBlackList method in ClientLibrary
        } catch (Exception e) {
            Logger.log(LogLevel.ERROR, "Failed to remove from black list: " + e.getMessage());
        }
    }
}
