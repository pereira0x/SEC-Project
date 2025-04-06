package depchain.client.commands;

import depchain.client.DepChainClient;
import depchain.library.ClientLibrary;
import depchain.utils.Logger;
import depchain.utils.Logger.LogLevel;

public class TransferFromCommand implements Command {
    private final DepChainClient client;

    public TransferFromCommand(DepChainClient client) {
        this.client = client;
    }

    @Override
    public void execute(String[] args, ClientLibrary clientLib) {
        if (args.length != 3) {
            Logger.log(LogLevel.ERROR, getUsage());
            return;
        }

        Logger.log(LogLevel.INFO, "Client sending transfer from request...");
        try {
            String sourceAddress = args[0];
            String targetAddress = args[1];
            long amount = Long.parseLong(args[2]);
            clientLib.transferFromISTCoin(sourceAddress, targetAddress, amount);
            Logger.log(LogLevel.INFO,
                    "Successfully proposed to transfer " + amount + " from " + sourceAddress + " to " + targetAddress);
        } catch (Exception e) {
            Logger.log(LogLevel.ERROR, "Failed to transfer from: " + e.getMessage());
        }
    }

    @Override
    public String getUsage() {
        return "Usage: transferFrom <sourceAddress> <targetAddress> <amount>";
    }
}
