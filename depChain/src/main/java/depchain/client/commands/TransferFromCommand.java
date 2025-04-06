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
            String senderAddress = args[0];
            String targetAddress = args[1];
            long amount = Long.parseLong(args[2]);
            clientLib.transferFromISTCoin(senderAddress, targetAddress, amount);
            Logger.log(LogLevel.INFO,
                    "Successfully proposed to transfer " + amount + " from " + senderAddress + " to " + targetAddress);
        } catch (Exception e) {
            Logger.log(LogLevel.ERROR, "Failed to transfer from: " + e.getMessage());
        }
    }

    @Override
    public String getUsage() {
        return "Usage: transferFrom <senderAddress> <targetAddress> <amount>";
    }
}
