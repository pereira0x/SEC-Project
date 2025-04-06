package depchain.client.commands;

import depchain.library.ClientLibrary;

public interface Command {
    void execute(String[] args, ClientLibrary clientLib);

    String getUsage();
}
