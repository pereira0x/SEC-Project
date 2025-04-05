package depchain.client.commands;

import depchain.client.DepChainClient;
import depchain.utils.Logger;
import depchain.utils.Logger.LogLevel;
import depchain.library.ClientLibrary;

public class GetISTBalanceCommand implements Command {

    private final DepChainClient client;

    public GetISTBalanceCommand(DepChainClient client) {
        this.client = client;
    }

    @Override
    public void execute(String[] args, ClientLibrary clientLib) {
        if (args.length != 1 || !args[0].matches("\\d+")) {
            Logger.log(LogLevel.ERROR, getUsage());
            return;
        }

        Logger.log(LogLevel.INFO, "Client sending get ISTCoin balance request...");
        try {
            // TODO: Implement the getISTBalance method in ClientLibrary
        } catch (Exception e) {
            Logger.log(LogLevel.ERROR, "Failed to get ISTCoin balance: " + e.getMessage());
        }
    }

    @Override
    public String getUsage() {
        return "Usage: getISTBal" + " <userId>";
    }
}