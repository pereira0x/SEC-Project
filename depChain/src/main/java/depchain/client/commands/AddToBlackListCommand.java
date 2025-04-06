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
        if (args.length != 1) {
            Logger.log(LogLevel.ERROR, getUsage());
            return;
        }

        Logger.log(LogLevel.INFO, "Client sending add to black list request...");
        try {
            String targetAddress = args[0];
            clientLib.addToBlackList(targetAddress);
            Logger.log(LogLevel.INFO, "Successfully proposed to add " + targetAddress + " to the black list");

        } catch (Exception e) {
            Logger.log(LogLevel.ERROR, "Failed to add to black list: " + e.getMessage());
        }
    }

    @Override
    public String getUsage() {
        return "Usage: addBlackList <targetAddress>";
    }
}
