package com.krxoid;

public final class Main {

    private static final String VERSION = "1.0.0";

    private Main() {
    }

    public static void main(String[] args) {

        CommandDispatcher dispatcher =
                new CommandDispatcher();

        if (args.length == 0) {
            dispatcher.startShell();
            return;
        }

        switch (args[0].toLowerCase()) {
            case "help", "--help", "-h" -> printHelp();
            case "version", "--version", "-v" -> printVersion();
            default -> {

                int exitCode =
                        dispatcher.dispatch(args);

                if (exitCode != 0) {
                    System.exit(exitCode);
                }
            }
        }
    }

    private static void printVersion() {
        System.out.println(
                "Rock Core Server Manager " + VERSION
        );
    }

    private static void printHelp() {
        System.out.println("""
                
                Rock Core Server Manager
                A CLI-first Minecraft Bedrock server manager.
                
                Usage:
                  rock <command> [arguments]
                
                Server commands:
                  server list
                      List configured servers.
                
                  server create <name>
                      Create a new server instance.
                
                  server start <name>
                      Start a server.
                
                  server stop <name>
                      Stop a server.
                
                  server restart <name>
                      Restart a server.
                
                  server status <name>
                      Show server status.
                
                  server players <name>
                      Show connected players.
                
                  server console <name>
                      Attach to the server console.
                
                  server cmd <name> <command>
                      Send a command to the server.
                
                  server backup <name>
                      Create a world backup.
                
                  server delete <name>
                      Delete a server instance.
                
                Global commands:
                  help
                      Show this help message.
                
                  version
                      Show the program version.
                
                Examples:
                  rock server list
                  rock server create survival
                  rock server start survival
                  rock server status survival
                  rock server players survival
                  rock server cmd survival "op krxoid"
                  rock server console survival
                  rock server backup survival
                
                """);
    }
}