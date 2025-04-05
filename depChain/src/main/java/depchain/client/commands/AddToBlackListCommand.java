package depchain.client.commands;

import org.apache.commons.math3.analysis.function.Add;

import depchain.client.DepChainClient;
import depchain.utils.Logger;
import depchain.utils.Logger.LogLevel;
import depchain.library.ClientLibrary;

public class AddToBlackListCommand implements Command {

    private final DepChainClient client;

    public AddToBlackListCommand(DepChainClient client) {
        this.client = client;
    }

    @Override
    public void execute(String[] args, ClientLibrary clientLib) {
        if (args.length != 1 || !args[0].matches("\\d+") || !args[1].matches("\\d+")) {
            Logger.log(LogLevel.ERROR, "Usage: addBlackList <userId>");
            return;
        }


        Logger.log(LogLevel.INFO, "Client sending add to black list request...");
        try {
            // TODO: Implement the addToBlackList method in ClientLibrary
        } catch (Exception e) {
            Logger.log(LogLevel.ERROR, "Failed to add to black list: " + e.getMessage());
        }
    }
}