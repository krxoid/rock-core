package com.krxoid;

import java.util.Arrays;
import java.util.Scanner;

public final class CommandDispatcher {

    private final ServerCommandHandler serverHandler;

    public CommandDispatcher() {
        this.serverHandler = new ServerCommandHandler();
    }

    /**
     * Starts the interactive Rock Core shell.
     */
    public void startShell() {

        System.out.println("Rock Core");
        System.out.println("Type 'help' for a list of commands.");
        System.out.println("Type 'exit' or 'quit' to leave.");
        System.out.println();

        Scanner scanner = new Scanner(System.in);

        while (true) {

            System.out.print("rock> ");

            if (!scanner.hasNextLine()) {
                break;
            }

            String line = scanner.nextLine().trim();

            if (line.isEmpty()) {
                continue;
            }

            if (line.equalsIgnoreCase("exit") ||
                    line.equalsIgnoreCase("quit")) {
                break;
            }

            if (line.equalsIgnoreCase("help")) {
                printHelp();
                continue;
            }

            String[] args = parseArguments(line);

            try {
                dispatch(args);
            } catch (Exception e) {
                System.err.println(
                        "Error: " + e.getMessage()
                );
            }
        }

        System.out.println("Goodbye.");
    }

    /**
     * Dispatches a single command.
     *
     * @return 0 on success, non-zero on failure
     */
    public int dispatch(String[] args) {

        if (args == null || args.length == 0) {
            return 0;
        }

        String command = args[0].toLowerCase();

        String[] commandArgs =
                Arrays.copyOfRange(
                        args,
                        1,
                        args.length
                );

        try {

            switch (command) {

                case "server":
                    return serverHandler.handle(
                            commandArgs
                    );

                case "help":
                    printHelp();
                    return 0;

                case "version":
                    printVersion();
                    return 0;

                default:
                    System.err.println(
                            "Unknown command: " + args[0]
                    );

                    System.err.println(
                            "Type 'help' for help."
                    );

                    return 1;
            }

        } catch (ServerManagerException e) {

            System.err.println(
                    "Error: " + e.getMessage()
            );

            return 1;

        } catch (Exception e) {

            System.err.println(
                    "Unexpected error: " +
                            e.getMessage()
            );

            return 1;
        }
    }

    private void printHelp() {

        System.out.println("""
                
                Rock Core - Minecraft Bedrock Server Manager
                
                Usage:
                  rock                     Start interactive shell
                  rock <command> [args]    Run a command directly
                
                Commands:
                  server list
                      List all configured servers.
                
                  server create <name>
                      Create a new server.
                
                  server start <name>
                      Start a server.
                
                  server stop <name>
                      Stop a server.
                
                  server restart <name>
                      Restart a server.
                
                  server status <name>
                      Show server status and PID.
                
                  server players <name>
                      Show connected players.
                
                  server console <name>
                      Attach to a server console.
                
                  server exec <name> <command>
                      Send a command directly to a server.
                
                  server backup <name>
                      Create a server backup.
                
                  server delete <name>
                      Delete a stopped server.
                
                  version
                      Show Rock Core version.
                
                  help
                      Show this help.
                
                  exit / quit
                      Exit the interactive shell.
                
                """);
    }

    private void printVersion() {
        System.out.println(
                "Rock Core Server Manager 1.0.0"
        );
    }

    /**
     * Very small shell argument parser.
     *
     * Supports:
     *
     *   server list
     *
     * and quoted arguments:
     *
     *   server exec survival "say hello world"
     */
    private String[] parseArguments(String line) {

        return line
                .trim()
                .split(
                        "\\s+(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)"
                );
    }
}
