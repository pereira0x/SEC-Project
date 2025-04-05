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
        if (args.length != 2) {
            Logger.log(LogLevel.ERROR, getUsage());
            return;
        }

        Logger.log(LogLevel.INFO, "Client sending approve request...");
        try {
            String targetAddress = args[0];
            long amount = Long.parseLong(args[1]);
            clientLib.approve(targetAddress, amount);
            Logger.log(LogLevel.INFO, "Successfully proposed to approve " + amount + " for " + targetAddress);
        } catch (Exception e) {
            Logger.log(LogLevel.ERROR, "Failed to approve request: " + e.getMessage());
        }
    }

    @Override
    public String getUsage() {
        return "Usage: approve <targetAddress> <amount>";
    }
}