package com.krxoid;

import java.io.IOException;

import static com.krxoid.CommandDispatcher.VERSION;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) {

        CommandDispatcher dispatcher =
                new CommandDispatcher();

        if (args.length == 0) {

            try {
                dispatcher.fetchVersions();
            }
            catch (IOException | InterruptedException e) {
                System.err.println("Could not fetch versions.json");
            }

            dispatcher.startShell();

            return;
        }

        switch (args[0].toLowerCase()) {

            case "help", "--help", "-h" ->
                    printHelp();

            case "version", "--version", "-v" ->
                    printVersion();

            default -> {

                int exitCode =
                        dispatcher.dispatch(args);

                if (exitCode != 0) {
                    System.exit(exitCode);
                }
            }
        }
    }

    private static void printHelp() {

        System.out.println("""
                
                Rock Core - Minecraft Bedrock Server Manager
                
                Usage:
                  rock                     Start interactive shell
                  rock <command> [args]    Run a command directly
                
                Global options:
                  -h, --help               Show this help
                  -v, --version            Show version
                
                Commands:
                  server list
                  server create <name> <version>
                  server start <name>
                  server stop <name>
                  server restart <name>
                  server status <name>
                  server players <name>
                  server console <name>
                  server exec <name> <command>
                  server backup <name>
                  server delete <name>
                  server import world <server> <path>
                  server config <name> <variable> <value>
                
                Run 'rock' without arguments to enter
                the interactive Rock Core shell.
                
                """);
    }

    private static void printVersion() {
        System.out.println(
                "Rock Core v" + VERSION
        );
    }
}
