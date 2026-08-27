package com.krxoid;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.krxoid.ServerManager.ROOT;

public final class ServerCommandHandler {

    public static final String prompt = "rock > ";

    private static final String BDS_URL =
            "https://www.minecraft.net/bedrockdedicatedserver/bin-linux/"
                    + "bedrock-server-%s.zip";

    private final ServerManager serverManager;

    private final HttpClient httpClient =
            HttpClient.newHttpClient();

    public ServerCommandHandler() {
        this.serverManager =
                new ServerManager();
    }

    public int handle(String[] args)
            throws ServerManagerException {

        if (args == null || args.length == 0) {
            printServerHelp();
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

        switch (command) {

            case "list":
                requireArguments(
                        command,
                        commandArgs,
                        0
                );
                return list();

            case "create":
                return create(commandArgs);

            case "start":
                requireArguments(
                        command,
                        commandArgs,
                        1
                );
                return start(commandArgs[0]);

            case "stop":
                requireArguments(
                        command,
                        commandArgs,
                        1
                );
                return stop(commandArgs[0]);

            case "restart":
                requireArguments(
                        command,
                        commandArgs,
                        1
                );
                return restart(commandArgs[0]);

            case "status":
                requireArguments(
                        command,
                        commandArgs,
                        1
                );
                return status(commandArgs[0]);

            case "players":
                requireArguments(
                        command,
                        commandArgs,
                        1
                );
                return players(commandArgs[0]);

            case "console":
                requireArguments(
                        command,
                        commandArgs,
                        1
                );
                return console(commandArgs[0]);

            case "exec":
                requireArguments(
                        command,
                        commandArgs,
                        2
                );

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
                requireArguments(
                        command,
                        commandArgs,
                        1
                );
                return backup(commandArgs[0]);

            case "delete":
                requireArguments(
                        command,
                        commandArgs,
                        1
                );
                return delete(commandArgs[0]);

            case "import":
                return importcmd(commandArgs);

            case "config":
                return config(commandArgs);

            case "help":
                printServerHelp();
                return 0;

            default:

                System.err.println(
                        "Unknown server command: " +
                                args[0]
                );

                printServerHelp();
                printPrompt();

                return 1;
        }
    }

    public boolean isIdle()
            throws IOException{

        return serverManager.isIdle();
    }

    private void downloadBds(
            String version,
            Path destination
    ) throws Exception {

        String url =
                BDS_URL.formatted(version);

        Path archive =
                Files.createTempFile(
                        "rock-core-bds-",
                        ".zip"
                );

        try {

            HttpRequest request =
                    HttpRequest.newBuilder(
                                    URI.create(url)
                            )
                            .GET()
                            .build();

            HttpResponse<Path> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.ofFile(
                                    archive
                            )
                    );

            if (response.statusCode() != 200) {

                throw new IOException(
                        "BDS download failed: HTTP " +
                                response.statusCode()
                );
            }

            extractZip(
                    archive,
                    destination
            );

            Path executable =
                    destination.resolve(
                            "bedrock_server"
                    );

            if (Files.exists(executable)) {

                executable.toFile()
                        .setExecutable(true);
            }

        } finally {

            Files.deleteIfExists(
                    archive
            );
        }
    }

    /*
     * server create <name> <version>
     */
    private int create(String[] args) {

        if (args.length != 2) {

            System.err.println(
                    "Usage: server create <name> <version>"
            );

            return 1;
        }

        String name =
                args[0];

        String version =
                args[1];

        try {
            if (version.equals("latest")) version = new CommandDispatcher()
                    .getLatestVersion()
                    .replace("[", "")
                    .replace("]", "");
        }
        catch (IOException e){
            e.printStackTrace();
        }

        try {

            if (!version.matches("\\d+(?:\\.\\d+){3}")) {
                throw new ServerManagerException(
                        "Invalid BDS version: " + version
                );
            }

            Path serverDirectory =
                    serverManager.createServer(name);

            System.out.println(
                    "Created server '" +
                            name +
                            "'."
            );

            System.out.println(
                    "Downloading BDS " +
                            version +
                            "..."
            );

            try {
                downloadBds(
                        version,
                        serverDirectory
                );
            } catch (Exception e) {
                serverManager.deleteServer(name);
                throw e;
            }

            System.out.println(
                    "Server '" +
                            name +
                            "' is ready with BDS " +
                            version +
                            "."
            );

            printPrompt();

            return 0;

        } catch (ServerManagerException e) {

            printError(e);
            return 1;

        } catch (Exception e) {

            printError(
                    new ServerManagerException(
                            "Failed to create server.",
                            e
                    )
            );

            printPrompt();

            return 1;
        }
    }

    /*
     * server import <type> <server> <path>
     *
     * Future extensions:
     *
     * server import world ...
     * server import mod ...
     * server import pack ...
     */
    private int importcmd(
            String[] args
    ) {

        if (args.length < 3) {

            System.err.println(
                    "Usage: server import <type> <server> <path>"
            );

            return 1;
        }

        String type =
                args[0].toLowerCase();

        String serverName =
                args[1];

        Path source =
                Path.of(args[2])
                        .toAbsolutePath()
                        .normalize();

        switch (type) {

            case "world":
                return importWorld(
                        serverName,
                        source
                );

            default:

                System.err.println(
                        "Unknown import type: " +
                                type
                );

                System.err.println(
                        "Available import types:"
                );

                System.err.println(
                        "  world"
                );

                printPrompt();

                return 1;
        }
    }

    private int importWorld(
            String serverName,
            Path source
    ) {

        try {

            if (!Files.exists(source)) {

                throw new ServerManagerException(
                        "World path does not exist: " +
                                source
                );
            }

            if (!Files.isDirectory(source)) {

                throw new ServerManagerException(
                        "World path must be a directory: " +
                                source
                );
            }

            Path worldsDirectory =
                    serverManager
                            .getServerDirectory(
                                    serverName
                            )
                            .resolve("worlds");

            Files.createDirectories(
                    worldsDirectory
            );

            Path destination =
                    worldsDirectory.resolve(
                            source.getFileName()
                                    .toString()
                    );

            if (Files.exists(destination)) {

                throw new ServerManagerException(
                        "A world named '" +
                                destination.getFileName() +
                                "' already exists."
                );
            }

            copyDirectory(
                    source,
                    destination
            );

            System.out.println(
                    "Imported world '" +
                            source.getFileName() +
                            "' into '" +
                            serverName +
                            "'."
            );

            return 0;

        } catch (ServerManagerException e) {

            printError(e);
            return 1;

        } catch (IOException e) {

            printError(
                    new ServerManagerException(
                            "Failed to import world.",
                            e
                    )
            );

            return 1;
        }
    }


    private static void extractZip(
            Path archive,
            Path destination
    ) throws IOException {

        Files.createDirectories(
                destination
        );

        Path normalizedDestination =
                destination
                        .toAbsolutePath()
                        .normalize();

        try (
                InputStream input =
                        Files.newInputStream(
                                archive
                        );

                ZipInputStream zip =
                        new ZipInputStream(
                                input
                        )
        ) {

            ZipEntry entry;

            while (
                    (entry = zip.getNextEntry())
                            != null
            ) {

                Path output =
                        normalizedDestination
                                .resolve(
                                        entry.getName()
                                )
                                .normalize();

                /*
                 * Prevent path traversal from
                 * malicious archives.
                 */
                if (!output.startsWith(
                        normalizedDestination
                )) {

                    throw new IOException(
                            "Unsafe path in BDS archive: " +
                                    entry.getName()
                    );
                }

                if (entry.isDirectory()) {

                    Files.createDirectories(
                            output
                    );

                } else {

                    Path parent =
                            output.getParent();

                    if (parent != null) {
                        Files.createDirectories(
                                parent
                        );
                    }

                    Files.copy(
                            zip,
                            output,
                            StandardCopyOption
                                    .REPLACE_EXISTING
                    );
                }

                zip.closeEntry();
            }
        }
    }

    private void copyDirectory(
            Path source,
            Path destination
    ) throws IOException {

        try (
                var paths =
                        Files.walk(source)
        ) {

            for (Path path :
                    paths.toList()) {

                Path relative =
                        source.relativize(path);

                Path target =
                        destination.resolve(
                                relative
                        );

                if (Files.isDirectory(path)) {

                    Files.createDirectories(
                            target
                    );

                } else {

                    Files.copy(
                            path,
                            target,
                            StandardCopyOption
                                    .REPLACE_EXISTING
                    );
                }
            }
        }
    }

    public int list() {

        try {
            serverManager.listServers();
            printPrompt();
            return 0;

        } catch (ServerManagerException e) {

            printError(e);
            printPrompt();

            return 1;
        }
    }

    public int start(String name) {

        try {
            serverManager.startServer(name);

            System.out.println(
                    "Server '" +
                            name +
                            "' started."
            );

            return 0;

        } catch (ServerManagerException e) {

            printError(e);
            printPrompt();
            return 1;
        }
    }

    public int stop(String name) {

        try {
            serverManager.stopServer(name);

            System.out.println(
                    "Server '" +
                            name +
                            "' stopped."
            );

            printPrompt();

            return 0;

        } catch (ServerManagerException e) {

            printError(e);

            printPrompt();

            return 1;
        }
    }

    public int restart(String name) {

        try {

            serverManager.restartServer(name);
            printPrompt();
            return 0;

        } catch (ServerManagerException e) {

            printError(e);
            printPrompt();
            return 1;
        }
    }

    public int status(String name) {

        try {
            serverManager.printStatus(name);
            printPrompt();
            return 0;

        } catch (ServerManagerException e) {

            printError(e);
            printPrompt();
            return 1;
        }
    }

    public int players(String name) {

        try {
            serverManager.printPlayers(name);
            printPrompt();
            return 0;

        } catch (ServerManagerException e) {

            printError(e);
            printPrompt();
            return 1;
        }
    }

    public int console(String name) {

        try {
            serverManager.attachConsole(name);
            return 0;

        } catch (ServerManagerException e) {

            printError(e);
            printPrompt();
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
            printPrompt();
            return 0;

        } catch (ServerManagerException e) {

            printError(e);
            printPrompt();
            return 1;
        }
    }

    public int delete(String name) {

        try {

            serverManager.deleteServer(name);
            printPrompt();
            return 0;

        } catch (ServerManagerException e) {

            printError(e);
            printPrompt();
            return 1;
        }
    }

    public int config(String[] args) {

        try {

            serverManager.changeConfig(args[1], args[2], args[0]);
            printPrompt();
            return 0;

        }

        catch (ServerManagerException | IOException e){

            e.printStackTrace(System.err);
            printPrompt();
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

    private String usageArguments(
            String command
    ) {

        return switch (command) {

            case "create" ->
                    " <name> <version>";

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

            case "import" ->
                    " <type> <server> <path>";

            default ->
                    "";
        };
    }

    private void printServerHelp() {

        System.out.println("""
                
                Server commands:
                
                  server list
                      List all configured servers.
                
                  server create <name> <version>
                      Create a server using the specified BDS version.
                
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
                      Attach to the server console.
                
                  server exec <name> <command>
                      Execute a command on a server.
                
                  server backup <name>
                      Create a backup.
                
                  server delete <name>
                      Delete a stopped server.
                
                  server import world <server> <path>
                      Import a Minecraft world.
                
                """);
    }

    private static void printError(
            ServerManagerException e
    ) {

        System.err.println(
                "Error: " +
                        e.getMessage()
        );
    }

    public static void printPrompt(){
        System.out.print(prompt);
        System.out.flush();
    }

}
