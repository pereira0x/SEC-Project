package depchain.client.commands;

import depchain.client.DepChainClient;
import depchain.library.ClientLibrary;
import depchain.utils.Logger;
import depchain.utils.Logger.LogLevel;

public class TransferFromCommand implements Command{
     private final DepChainClient client;

    public TransferFromCommand(DepChainClient client) {
        this.client = client;
    }

    @Override
    public void execute(String[] args, ClientLibrary clientLib) {
        if (args.length != 3 || !args[0].matches("\\d+") || !args[1].matches("\\d+") || !args[2].matches("\\d+")) {
            Logger.log(LogLevel.ERROR, "Usage: transferFrom <sourceUserID> <recipientUserId> <amount>");
            return;
        }


        Logger.log(LogLevel.INFO, "Client sending transfer from request...");
        try {
            // TODO: Implement the transferFrom method in ClientLibrary
        } catch (Exception e) {
            Logger.log(LogLevel.ERROR, "Failed to transfer from: " + e.getMessage());
        }
    }
}
