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
        if (args.length != 2 || !args[0].matches("\\d+") || !args[1].matches("\\d+")) {
            Logger.log(LogLevel.ERROR, "Usage: transferDep <recipientId> <amount>");
            return;
        }

        int recipientId = Integer.parseInt(args[0]);
        long amount = Long.parseLong(args[1]);
        // client.transferDepcoin(recipientId, amount);

        Logger.log(LogLevel.INFO, "Client sending transfer depCoin request...");
        try {
            clientLib.transferDepcoin(recipientId, amount);
        } catch (Exception e) {
            Logger.log(LogLevel.ERROR, "Failed to transfer Depcoin: " + e.getMessage());
        }
    }
}
