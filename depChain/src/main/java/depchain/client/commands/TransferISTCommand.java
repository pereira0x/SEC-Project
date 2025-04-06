package depchain.client.commands;

import depchain.client.DepChainClient;
import depchain.utils.Logger;
import depchain.utils.Logger.LogLevel;
import depchain.library.ClientLibrary;

public class TransferISTCommand implements Command {

    private final DepChainClient client;

    public TransferISTCommand(DepChainClient client) {
        this.client = client;
    }

    @Override
    public void execute(String[] args, ClientLibrary clientLib) {
        if (args.length != 2) {
            Logger.log(LogLevel.ERROR, getUsage());
            return;
        }

        Logger.log(LogLevel.INFO, "Client sending transfer ISTCoin request...");
        try {
            String targetAddress = args[0];
            long amount = Long.parseLong(args[1]);
            clientLib.transferISTCoin(targetAddress, amount);
            Logger.log(LogLevel.INFO, "Successfully proposed to transfer " + amount + " IST Coin to " + targetAddress);
        } catch (Exception e) {
            Logger.log(LogLevel.ERROR, "Failed to transfer IST Coin: " + e.getMessage());
        }
    }

    @Override
    public String getUsage() {
        return "Usage: transferIST <targetAddress> <amount>";
    }
}
