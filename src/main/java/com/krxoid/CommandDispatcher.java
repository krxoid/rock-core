package com.krxoid;

import java.util.Arrays;
import java.util.Scanner;

public final class CommandDispatcher {

    private final ServerCommandHandler serverHandler;

    public CommandDispatcher() {
        this.serverHandler =
                new ServerCommandHandler();
    }

    public void startShell() {

        Scanner scanner = new Scanner(System.in);

        System.out.println("Rock Core");
        System.out.println("Type 'help' for a list of commands.");
        System.out.println("Type 'exit' or 'quit' to leave.");
        System.out.println();

        System.out.print("rock > ");
        System.out.flush();

        while (scanner.hasNextLine()) {

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
                System.out.print("rock > ");
                System.out.flush();
                continue;
            }

            dispatch(parseArguments(line));
        }
    }

    public int dispatch(String[] args) {

        if (args == null || args.length == 0) {
            return 0;
        }

        String command =
                args[0].toLowerCase();

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
                    System.out.print("rock > ");
                    return 0;

                case "version":
                    printVersion();
                    return 0;

                default:
                    System.err.println(
                            "Unknown command: " +
                                    args[0]
                    );

                    System.err.println(
                            "Type 'help' for help."
                    );

                    System.out.print("rock > ");

                    return 1;
            }

        } catch (Exception e) {

            System.err.println(
                    "Error: " +
                            e.getMessage()
            );

            return 1;
        }
    }

    private void printHelp() {

        System.out.println("""
                
                Rock Core - Minecraft Bedrock Server Manager
                
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
                
                Global:
                  help
                  version
                  exit
                  quit
                
                """);
    }

    private void printVersion() {

        System.out.println(
                "Rock Core 1.0.0"
        );
    }

    private String[] parseArguments(
            String line
    ) {

        return line
                .trim()
                .split(
                        "\\s+(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)"
                );
    }
}
