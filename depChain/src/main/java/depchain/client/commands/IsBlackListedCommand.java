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
        if (args.length != 1) {
            Logger.log(LogLevel.ERROR, getUsage());
            return;
        }

        Logger.log(LogLevel.INFO, "Client sending get black list request...");
        try {
            String targetAddress = args[0];
            String blackListStatus = clientLib.isBlacklisted(targetAddress);
            Logger.log(LogLevel.INFO, "Target " + targetAddress + " is blacklisted: " + blackListStatus);
        } catch (Exception e) {
            Logger.log(LogLevel.ERROR, "Failed to get black list: " + e.getMessage());
        }
    }

    @Override
    public String getUsage() {
        return "Usage: isBlackListed <targetAddress>";
    }
}
