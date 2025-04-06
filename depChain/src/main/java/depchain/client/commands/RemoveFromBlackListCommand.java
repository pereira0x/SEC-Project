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
        if (args.length != 1) {
            Logger.log(LogLevel.ERROR, getUsage());
            return;
        }

        Logger.log(LogLevel.INFO, "Client sending remove from black list request...");
        try {
            String targetAddress = args[0];
            clientLib.removeFromBlackList(targetAddress);
            Logger.log(LogLevel.INFO, "Successfully proposed to remove " + targetAddress + " from the black list");
        } catch (Exception e) {
            Logger.log(LogLevel.ERROR, "Failed to remove from black list: " + e.getMessage());
        }
    }

    @Override
    public String getUsage() {
        return "Usage: removeBlackList <targetAddress>";
    }
}
