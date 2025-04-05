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
        if (args.length != 1) {
            Logger.log(LogLevel.ERROR, getUsage());
            return;
        }

        try {
          String targetAddress = args[0];
          String balance = clientLib.getISTCoinBalance(targetAddress);
          Logger.log(LogLevel.INFO,
                     "Target " +targetAddress  + " has ISTCoin balance: " + balance);
        } catch (Exception e) {
            Logger.log(LogLevel.ERROR, "Failed to get ISTCoin balance: " + e.getMessage());
        }
    }

    @Override
    public String getUsage() {
        return "Usage: getISTBal" + " <userId>";
    }
}