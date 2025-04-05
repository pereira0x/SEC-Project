package depchain.client.commands;

import depchain.client.DepChainClient;
import depchain.utils.Logger;
import depchain.utils.Logger.LogLevel;
import depchain.library.ClientLibrary;

public class GetDepBalanceCommand implements Command {

    private final DepChainClient client;

    public GetDepBalanceCommand(DepChainClient client) {
        this.client = client;
    }

    @Override
    public void execute(String[] args, ClientLibrary clientLib) {
        if (args.length != 0) {
            Logger.log(LogLevel.ERROR, getUsage());
            return;
        }


        Logger.log(LogLevel.INFO, "Client sending get DepCoin balance request...");
        try {
            // TODO: Implement the getDepBalance method in ClientLibrary
        } catch (Exception e) {
            Logger.log(LogLevel.ERROR, "Failed to get DepCoin balance: " + e.getMessage());
        }
    }

    @Override
    public String getUsage() {
        return "Usage: getDepBal";
    }
}