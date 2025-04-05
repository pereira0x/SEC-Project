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
        if (args.length != 2 || !args[0].matches("\\d+") || !args[1].matches("\\d+") || !args[2].matches("\\d+")) {
            Logger.log(LogLevel.ERROR, getUsage());
            return;
        }

        int spender = Integer.parseInt(args[0]);
        long source = Long.parseLong(args[1]);
        long amount = Long.parseLong(args[2]);
        

        // How much can the spender spend on behalf of the source

        Logger.log(LogLevel.INFO, "Client sending allowance request...");
        try {
            // TODO: Implement the allowTransferFrom method in ClientLibrary
        } catch (Exception e) {
            Logger.log(LogLevel.ERROR, "Failed to allowance request: " + e.getMessage());
        }
    }

    @Override
    public String getUsage() {
        return "Usage: allow <spender> <source> <amount>";
    }
}