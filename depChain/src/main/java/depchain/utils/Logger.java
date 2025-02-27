package depchain.utils;

import io.github.cdimascio.dotenv.Dotenv;

public class Logger {
    private static boolean debugEnabled = Dotenv.load().get("DEBUG") == null ? false
            : Boolean.parseBoolean(Dotenv.load().get("DEBUG"));
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_BLUE = "\u001B[34m";
    private static final String ANSI_PURPLE = "\u001B[35m";
    private static final String ANSI_BOLD = "\u001B[1m";

    public enum LogLevel {
        DEBUG, INFO, WARNING, ERROR
    }

    public static void log(LogLevel level, String message) {

        boolean shouldPrint = false;
        String prefix = "";

        switch (level) {
            case DEBUG:
                if (debugEnabled) {
                    shouldPrint = true;
                    prefix = "[" + ANSI_BOLD + ANSI_YELLOW + "DEBUG" + ANSI_RESET + "]";
                }
                break;
            case INFO:
                shouldPrint = true;
                prefix = "[" + ANSI_BOLD + ANSI_BLUE + "INFO" + ANSI_RESET + "]";
                break;
            case WARNING:
                shouldPrint = true;
                prefix = "[" + ANSI_BOLD + ANSI_PURPLE + "WARNING" + ANSI_RESET + "]";
                break;
            case ERROR:
                shouldPrint = true;
                prefix = "[" + ANSI_BOLD + ANSI_RED + "ERROR" + ANSI_RESET + "]";
                break;
        }

        if (shouldPrint) {
            System.out.println(prefix + " " + message);
        }
    }
}
