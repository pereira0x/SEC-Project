package depchain.client.commands;

import depchain.client.DepChainClient;
import depchain.utils.Logger;
import depchain.utils.Logger.LogLevel;
import depchain.library.ClientLibrary;

public class AllowTransferFromCommand implements Command {

    private final DepChainClient client;

    public AllowTransferFromCommand(DepChainClient client) {
        this.client = client;
    }

    @Override
    public void execute(String[] args, ClientLibrary clientLib) {
        if (args.length != 2 || !args[0].matches("\\d+") || !args[1].matches("\\d+")) {
            Logger.log(LogLevel.ERROR, "Usage: allowTransferFrom <userId> <amount>");
            return;
        }


        Logger.log(LogLevel.INFO, "Client sending allow transfer from request...");
        try {
            // TODO: Implement the allowTransferFrom method in ClientLibrary
        } catch (Exception e) {
            Logger.log(LogLevel.ERROR, "Failed to allow transfer from: " + e.getMessage());
        }
    }
}