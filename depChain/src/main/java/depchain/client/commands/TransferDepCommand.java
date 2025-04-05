package depchain.client.commands;

import depchain.client.DepChainClient;
import depchain.utils.Logger;
import depchain.utils.Logger.LogLevel;
import depchain.library.ClientLibrary;

public class TransferDepCommand implements Command {

    private final DepChainClient client;

    public TransferDepCommand(DepChainClient client) {
        this.client = client;
    }

    @Override
    public void execute(String[] args, ClientLibrary clientLib) {
        if (args.length != 2) {
            Logger.log(LogLevel.ERROR, getUsage());
            return;
        }

        Logger.log(LogLevel.INFO, "Client sending transfer DepCoin request...");
        try {
            String targetAddress = args[0];
            long amount = Long.parseLong(args[1]);
            clientLib.transferDepcoin(targetAddress, amount);
        } catch (Exception e) {
            Logger.log(LogLevel.ERROR, "Failed to transfer DepCoin: " + e.getMessage());
        }
    }

    @Override
    public String getUsage() {
        return "Usage: transferDep <recipientId> <amount>";
    }
}
