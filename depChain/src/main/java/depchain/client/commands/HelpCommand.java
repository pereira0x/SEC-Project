package depchain.client.commands;

import depchain.library.ClientLibrary;

public class HelpCommand implements Command {
    private final CommandManager manager;

    public HelpCommand(CommandManager manager) {
        this.manager = manager;
    }

    @Override
    public void execute(String[] args, ClientLibrary clientLib) {
        manager.printAllCommands();
    }

    @Override
    public String getUsage() {
        return "Usages: help                - Show available commands.";
    }
}
