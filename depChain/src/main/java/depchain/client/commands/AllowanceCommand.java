package depchain.client.commands;

import depchain.client.DepChainClient;
import depchain.utils.Logger;
import depchain.utils.Logger.LogLevel;
import depchain.library.ClientLibrary;

public class AllowanceCommand implements Command {

    private final DepChainClient client;

    public AllowanceCommand(DepChainClient client) {
        this.client = client;
    }

    @Override
    public void execute(String[] args, ClientLibrary clientLib) {
        if (args.length != 2) {
            Logger.log(LogLevel.ERROR, getUsage());
            return;
        }
        // How much can the spender spend on behalf of the source
        Logger.log(LogLevel.INFO, "Client sending allowance request...");
        try {
          String source = args[0];
          String spender = args[1];
          String allowance = clientLib.allowance(source, spender);
            Logger.log(LogLevel.INFO,
                    "Allowance for spender " + spender + " on source " + source + ": " + allowance);

        } catch (Exception e) {
            Logger.log(LogLevel.ERROR, "Failed to allowance request: " + e.getMessage());
        }
    }

    @Override
    public String getUsage() {
        return "Usage: allowance <source> <spender>";
    }
}