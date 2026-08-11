package com.krxoid;

import java.util.Arrays;

public final class ServerCommandHandler {

    private final ServerManager serverManager;

    public ServerCommandHandler() {
        this.serverManager = new ServerManager();
    }

    public int handle(String[] args)
            throws ServerManagerException {

        if (args == null || args.length == 0) {
            printServerHelp();
            return 0;
        }

        String command = args[0].toLowerCase();

        String[] commandArgs =
                Arrays.copyOfRange(
                        args,
                        1,
                        args.length
                );

        switch (command) {

            case "list":
                requireArguments(command, commandArgs, 0);
                return list();

            case "create":
                requireArguments(command, commandArgs, 1);
                return create(commandArgs[0]);

            case "start":
                requireArguments(command, commandArgs, 1);
                return start(commandArgs[0]);

            case "stop":
                requireArguments(command, commandArgs, 1);
                return stop(commandArgs[0]);

            case "restart":
                requireArguments(command, commandArgs, 1);
                return restart(commandArgs[0]);

            case "status":
                requireArguments(command, commandArgs, 1);
                return status(commandArgs[0]);

            case "players":
                requireArguments(command, commandArgs, 1);
                return players(commandArgs[0]);

            case "console":
                requireArguments(command, commandArgs, 1);
                return console(commandArgs[0]);

            case "exec":
                requireArguments(command, commandArgs, 2);

                String execCommand =
                        String.join(
                                " ",
                                Arrays.copyOfRange(
                                        commandArgs,
                                        1,
                                        commandArgs.length
                                )
                        );

                return command(
                        commandArgs[0],
                        execCommand
                );

            case "backup":
                requireArguments(command, commandArgs, 1);
                return backup(commandArgs[0]);

            case "delete":
                requireArguments(command, commandArgs, 1);
                return delete(commandArgs[0]);

            case "help":
                printServerHelp();
                return 0;

            default:
                System.err.println(
                        "Unknown server command: " +
                                args[0]
                );

                printServerHelp();
                return 1;
        }
    }

    public int list() {
        try {
            serverManager.listServers();
            return 0;
        } catch (ServerManagerException e) {
            printError(e);
            return 1;
        }
    }

    public int create(String name) {
        try {
            serverManager.createServer(name);

            System.out.println(
                    "Server '" + name + "' created."
            );

            return 0;

        } catch (ServerManagerException e) {
            printError(e);
            return 1;
        }
    }

    public int start(String name) {
        try {
            serverManager.startServer(name);

            System.out.println(
                    "Server '" + name + "' started."
            );

            return 0;

        } catch (ServerManagerException e) {
            printError(e);
            return 1;
        }
    }

    public int stop(String name) {
        try {
            serverManager.stopServer(name);

            System.out.println(
                    "Server '" + name + "' stopped."
            );

            return 0;

        } catch (ServerManagerException e) {
            printError(e);
            return 1;
        }
    }

    public int restart(String name) {
        try {
            serverManager.restartServer(name);

            System.out.println(
                    "Server '" + name + "' restarted."
            );

            return 0;

        } catch (ServerManagerException e) {
            printError(e);
            return 1;
        }
    }

    public int status(String name) {
        try {
            serverManager.printStatus(name);
            return 0;

        } catch (ServerManagerException e) {
            printError(e);
            return 1;
        }
    }

    public int players(String name) {
        try {
            serverManager.printPlayers(name);
            return 0;

        } catch (ServerManagerException e) {
            printError(e);
            return 1;
        }
    }

    public int console(String name) {
        try {
            serverManager.attachConsole(name);
            return 0;

        } catch (ServerManagerException e) {
            printError(e);
            return 1;
        }
    }

    public int command(
            String name,
            String command
    ) {
        try {
            serverManager.sendCommand(
                    name,
                    command
            );

            return 0;

        } catch (ServerManagerException e) {
            printError(e);
            return 1;
        }
    }

    public int backup(String name) {
        try {
            serverManager.createBackup(name);
            return 0;

        } catch (ServerManagerException e) {
            printError(e);
            return 1;
        }
    }

    public int delete(String name) {
        try {
            serverManager.deleteServer(name);

            System.out.println(
                    "Server '" + name + "' deleted."
            );

            return 0;

        } catch (ServerManagerException e) {
            printError(e);
            return 1;
        }
    }

    private void requireArguments(
            String command,
            String[] args,
            int required
    ) throws ServerManagerException {

        if (args.length < required) {

            throw new ServerManagerException(
                    "Usage: server " +
                            command +
                            usageArguments(command)
            );
        }
    }

    private String usageArguments(String command) {

        return switch (command) {

            case "create" ->
                    " <name>";

            case "start",
                 "stop",
                 "restart",
                 "status",
                 "players",
                 "console",
                 "backup",
                 "delete" ->
                    " <name>";

            case "exec" ->
                    " <name> <command>";

            default ->
                    "";
        };
    }

    private void printServerHelp() {

        System.out.println("""
                
                Server commands:
                
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
                      Request the player list.
                
                  server console <name>
                      Attach to the server console.
                
                  server exec <name> <command>
                      Execute a command on a server.
                
                  server backup <name>
                      Create a backup.
                
                  server delete <name>
                      Delete a stopped server.
                
                """);
    }

    private void printError(
            ServerManagerException e
    ) {
        System.err.println(
                "Error: " + e.getMessage()
        );
    }
}
