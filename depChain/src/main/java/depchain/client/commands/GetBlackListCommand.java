package depchain.client.commands;

import depchain.client.DepChainClient;
import depchain.utils.Logger;
import depchain.utils.Logger.LogLevel;
import depchain.library.ClientLibrary;

public class GetBlackListCommand implements Command {

    private final DepChainClient client;

    public GetBlackListCommand(DepChainClient client) {
        this.client = client;
    }

    @Override
    public void execute(String[] args, ClientLibrary clientLib) {
        if (args.length != 0) {
            Logger.log(LogLevel.ERROR, "Usage: getBlackList");
            return;
        }


        Logger.log(LogLevel.INFO, "Client sending get black list request...");
        try {
            // TODO: Implement the getBlackList method in ClientLibrary
        } catch (Exception e) {
            Logger.log(LogLevel.ERROR, "Failed to get black list: " + e.getMessage());
        }
    }
}