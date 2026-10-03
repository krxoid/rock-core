package com.krxoid;

import org.jline.reader.LineReader;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.krxoid.CommandDispatcher.getLatestVersion;

public final class ServerCommandHandler {

    private static final String BDS_URL =
            "https://www.minecraft.net/bedrockdedicatedserver/bin-linux/"
                    + "bedrock-server-%s.zip";

    private static final Pattern VERSION_PATTERN =
            Pattern.compile("\\d+(?:\\.\\d+)+");

    private static final Pattern ARGUMENT_PATTERN =
            Pattern.compile(
                    "'([^']*)'|\"([^\"]*)\"|(\\S+)"
            );

    private final ServerManager serverManager;

    private final HttpClient httpClient =
            HttpClient.newBuilder()
                    .connectTimeout(
                            Duration.ofSeconds(15)
                    )
                    .build();

    public ServerCommandHandler(
            LineReader lineReader
    ) {
        this.serverManager =
                new ServerManager(lineReader);
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
                        1
                );
                return list(commandArgs[0]);

            case "create":
                requireArguments(
                        command,
                        commandArgs,
                        2
                );
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
                return delete(commandArgs);

            case "update":
                requireArguments(
                        command,
                        commandArgs,
                        2
                );
                return update(commandArgs);

            case "import":
                requireArguments(
                        command,
                        commandArgs,
                        3
                );
                return importCommand(commandArgs);

            case "restore":
                requireArguments(
                        command,
                        commandArgs,
                        2
                );
                return restore(commandArgs);

            case "config":
                return config(commandArgs);

            case "rename":
                requireArguments(
                        command,
                        commandArgs,
                        2
                );
                return rename(commandArgs);

            case "clone":
                requireArguments(
                        command,
                        commandArgs,
                        2
                );
                return clone(commandArgs);

            case "logs":
                requireArguments(
                        command,
                        commandArgs,
                        2
                );
                return logs(commandArgs);

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

    public boolean isIdle() {
        return serverManager.isIdle();
    }

    private void downloadBds(
            String version,
            Path destination
    ) throws IOException, InterruptedException {

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
                            .timeout(
                                    Duration.ofMinutes(10)
                            )
                            .GET()
                            .build();

            System.out.println(
                    "Downloading from:"
            );

            System.out.println(
                    "  " + url
            );

            HttpResponse<Path> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers
                                    .ofFile(archive)
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

            if (!Files.isRegularFile(
                    executable
            )) {

                throw new IOException(
                        "Downloaded BDS archive does not contain " +
                                "'bedrock_server'."
                );
            }

            if (!executable.toFile()
                    .setExecutable(true)) {

                throw new IOException(
                        "Could not make bedrock_server executable."
                );
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

        final String version;

        try {
            version =
                    resolveVersion(
                            args[1]
                    );
        } catch (ServerManagerException e) {

            printError(e);
            return 1;
        }

        try {

            validateVersion(version);

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

                /*
                 * The server was only partially created.
                 * Remove it so failed creation does not leave
                 * a fake/incomplete server behind.
                 */
                try {
                    serverManager.deleteServer(name);
                } catch (ServerManagerException cleanupError) {
                    e.addSuppressed(cleanupError);
                }

                throw e;
            }

            System.out.println(
                    "Server '" +
                            name +
                            "' is ready with BDS " +
                            version +
                            "."
            );

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

            return 1;
        }
    }

    private String resolveVersion(
            String requested
    ) throws ServerManagerException {

        if (requested == null
                || requested.isBlank()) {

            throw new ServerManagerException(
                    "Version cannot be empty."
            );
        }

        if (!requested.equalsIgnoreCase(
                "latest"
        )) {
            return requested;
        }

        try {

            String latest =
                    getLatestVersion();

            if (latest.isBlank()) {

                throw new ServerManagerException(
                        "Could not determine the latest BDS version."
                );
            }

            /*
             * getLatestVersion() currently returns a value that
             * may be represented as a list, so clean the wrapper.
             */
            latest =
                    latest
                            .replace("[", "")
                            .replace("]", "")
                            .trim();

            if (latest.contains(",")) {
                latest =
                        latest
                                .split(",")[0]
                                .trim();
            }

            validateVersion(latest);

            return latest;

        } catch (IOException e) {

            throw new ServerManagerException(
                    "Failed to determine the latest BDS version.",
                    e
            );
        }
    }

    private void validateVersion(
            String version
    ) throws ServerManagerException {

        if (!VERSION_PATTERN
                .matcher(version)
                .matches()) {

            throw new ServerManagerException(
                    "Invalid BDS version: " +
                            version
            );
        }
    }

    /*
     * server import <type> <server> <path>
     */
    private int importCommand(
            String[] args
    ) {

        String type =
                args[0].toLowerCase();

        String serverName =
                args[1];

        Path source;

        try {

            source =
                    Path.of(args[2])
                            .toAbsolutePath()
                            .normalize();

        } catch (Exception e) {

            printError(
                    new ServerManagerException(
                            "Invalid source path.",
                            e
                    )
            );

            return 1;
        }

        try {

            switch (type) {

                case "world":
                    serverManager.importWorld(
                            serverName,
                            source
                    );
                    return 0;

                case "config":
                    serverManager.importConfig(
                            serverName,
                            source
                    );
                    return 0;

                default:

                    System.err.println(
                            "Unknown import type: " +
                                    type
                    );

                    System.err.println(
                            "Available import types:"
                    );

                    System.err.println(
                            "  world \n  config"
                    );

                    return 1;
            }
        } catch (ServerManagerException e) {
            printError(e);
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

                Path output = getOutput(entry, normalizedDestination);

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

    private static Path getOutput(ZipEntry entry, Path normalizedDestination) throws IOException {
        String entryName =
                entry.getName();

        Path output =
                normalizedDestination
                        .resolve(
                                entryName
                        )
                        .normalize();

        /*
         * Prevent ../ and absolute-path entries from
         * escaping the server directory.
         */
        if (!output.startsWith(
                normalizedDestination
        )) {

            throw new IOException(
                    "Unsafe path in BDS archive: " +
                            entryName
            );
        }
        return output;
    }

    private void copyDirectory(
            Path source,
            Path destination
    ) throws IOException {

        if (!Files.isDirectory(source)) {

            throw new IOException(
                    "Source directory does not exist: " +
                            source
            );
        }

        Path normalizedSource =
                source.toAbsolutePath()
                        .normalize();

        Path normalizedDestination = getNormalizedDestination(destination, normalizedSource);

        Files.createDirectories(
                normalizedDestination
        );

        try (
                var paths =
                        Files.walk(
                                normalizedSource
                        )
        ) {

            for (Path path :
                    paths.toList()) {

                Path relative =
                        normalizedSource.relativize(
                                path
                        );

                Path target =
                        normalizedDestination.resolve(
                                relative
                        );

                if (Files.isDirectory(path)) {

                    Files.createDirectories(
                            target
                    );

                } else {

                    Path parent =
                            target.getParent();

                    if (parent != null) {
                        Files.createDirectories(
                                parent
                        );
                    }

                    Files.copy(
                            path,
                            target,
                            StandardCopyOption
                                    .REPLACE_EXISTING,
                            StandardCopyOption
                                    .COPY_ATTRIBUTES
                    );
                }
            }
        }
    }

    private static Path getNormalizedDestination(Path destination, Path normalizedSource) throws IOException {
        Path normalizedDestination =
                destination.toAbsolutePath()
                        .normalize();

        if (normalizedDestination.equals(
                normalizedSource
        )) {

            throw new IOException(
                    "Source and destination are identical."
            );
        }

        if (normalizedDestination.startsWith(
                normalizedSource
        )) {

            throw new IOException(
                    "Destination cannot be inside source."
            );
        }
        return normalizedDestination;
    }

    public int list(String modifier) {

        try {

            serverManager.listServers(
                    modifier
            );

            return 0;

        } catch (ServerManagerException e) {

            printError(e);
            return 1;

        } catch (IOException e) {

            printError(
                    new ServerManagerException(
                            "Failed to list servers.",
                            e
                    )
            );

            return 1;
        }
    }

    public int start(String name) {

        try {

            serverManager.startServer(
                    name
            );

            System.out.println(
                    "Server '" +
                            name +
                            "' started."
            );

            return 0;

        } catch (ServerManagerException e) {

            printError(e);
            return 1;
        }
    }

    public int stop(String name) {

        try {

            serverManager.stopServer(
                    name
            );

            System.out.println(
                    "Server '" +
                            name +
                            "' stopped."
            );

            return 0;

        } catch (ServerManagerException e) {

            printError(e);
            return 1;
        }
    }

    public int restart(String name) {

        try {

            serverManager.restartServer(
                    name
            );

            System.out.println(
                    "Server '" +
                            name +
                            "' restarted"
            );

            return 0;

        } catch (ServerManagerException e) {

            printError(e);
            return 1;
        }
    }

    public int clone(String[] args) {

        String name = args[0];
        String cloneName = args[1];

        try {

            serverManager.cloneServer(
                    name,
                    cloneName
            );

            System.out.println(
                    "Server '" +
                            cloneName +
                            "' cloned"
            );

            return 0;

        } catch (ServerManagerException e) {
            printError(e);
            return 1;
        }
    }

    public int status(String name) {

        try {

            serverManager.printStatus(
                    name
            );

            return 0;

        } catch (ServerManagerException e) {

            printError(e);
            return 1;
        }
    }

    public int players(String name) {

        try {

            serverManager.printPlayers(
                    name
            );

            return 0;

        } catch (ServerManagerException e) {

            printError(e);
            return 1;
        }
    }

    public int console(String name) {

        try {

            serverManager.attachConsole(
                    name
            );

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

            serverManager.createBackup(
                    name
            );

            return 0;

        } catch (ServerManagerException e) {

            printError(e);
            return 1;
        }
    }

    private int restore(
            String[] args
    ) {

        String serverName =
                args[0];

        String worldName =
                args[1];

        final List<String> backups;

        try {

            backups =
                    serverManager.getBackups(
                            serverName,
                            worldName
                    );

        } catch (IOException e) {

            printError(
                    new ServerManagerException(
                            "Failed to get backups.",
                            e
                    )
            );

            return 1;
        }

        if (backups.isEmpty()) {

            System.out.println(
                    "No backups found for " +
                            serverName +
                            "/" +
                            worldName
            );

            return 1;
        }

        LineReader lineReader =
                serverManager.getLineReader();

        int selected = 0;

        try {

            lineReader
                    .getTerminal()
                    .enterRawMode();

            lineReader
                    .getTerminal()
                    .writer()
                    .print(
                            "\033[?1049h\033[?25l"
                    );

            lineReader
                    .getTerminal()
                    .writer()
                    .flush();

            while (true) {

                var terminal =
                        lineReader.getTerminal();

                var out =
                        terminal.writer();

                out.print(
                        "\033[H\033[2J"
                );

                out.println(
                        "Restore backup for \"" +
                                serverName +
                                "/" +
                                worldName +
                                "\""
                );

                out.println();

                for (int i = 0;
                     i < backups.size();
                     i++) {

                    out.printf(
                            "%s%s%n",
                            i == selected
                                    ? "> "
                                    : "  ",
                            backups.get(i)
                    );
                }

                out.println();

                out.print(
                        "↑/↓ Select    " +
                                "Enter Restore    " +
                                "Esc Cancel"
                );

                out.flush();

                int key =
                        terminal
                                .reader()
                                .read();

                if (key == 27) {

                    int next =
                            terminal
                                    .reader()
                                    .read();

                    if (next == '[') {

                        int arrow =
                                terminal
                                        .reader()
                                        .read();

                        if (arrow == 'A') {

                            selected =
                                    Math.max(
                                            0,
                                            selected - 1
                                    );

                        } else if (arrow == 'B') {

                            selected =
                                    Math.min(
                                            backups.size() - 1,
                                            selected + 1
                                    );

                        } else {
                            return 1;
                        }

                    } else {

                        return 1;
                    }

                } else if (
                        key == '\n'
                                || key == '\r'
                ) {

                    try {

                        serverManager.restoreBackup(
                                serverName,
                                worldName,
                                backups.get(selected)
                        );

                        return 0;

                    } catch (ServerManagerException e) {

                        printError(e);
                        return 1;
                    }
                }
            }

        } catch (IOException e) {

            printError(
                    new ServerManagerException(
                            "Restore interface failed.",
                            e
                    )
            );

            return 1;

        } finally {

            lineReader
                    .getTerminal()
                    .writer()
                    .print(
                            "\033[?25h\033[?1049l"
                    );

            lineReader
                    .getTerminal()
                    .writer()
                    .flush();
        }
    }

    private int delete(
            String[] args
    ) throws ServerManagerException {

        try {

            if (args.length < 2) {

                throw new ServerManagerException(
                        "Usage: server delete " +
                                "<server|backup|logs> <name> [count]"
                );
            }

            String type =
                    args[0].toLowerCase();

            String name =
                    args[1];

            switch (type) {

                case "server":

                    if (args.length != 2) {

                        throw new ServerManagerException(
                                "Usage: server delete " +
                                        "server <name>"
                        );
                    }

                    serverManager.deleteServer(
                            name
                    );

                    System.out.println(
                            "Server '" +
                                    name +
                                    "' deleted."
                    );

                    return 0;

                case "backup":

                    if (args.length != 3) {

                        throw new ServerManagerException(
                                "Usage: server delete " +
                                        "backup <name> <count>"
                        );
                    }

                    int count;

                    try {

                        count =
                                Integer.parseInt(
                                        args[2]
                                );

                    } catch (NumberFormatException e) {

                        throw new ServerManagerException(
                                "Backup count must be a number.",
                                e
                        );
                    }

                    serverManager.deleteBackup(
                            name,
                            count
                    );

                    return 0;

                case "logs":

                    if (args.length != 2) {

                        throw new ServerManagerException(
                                "Usage: server delete " +
                                        "logs <name>"
                        );
                    }

                    serverManager.deleteLogs(
                            name
                    );

                    System.out.println(
                            "Logs for server '" +
                                    name +
                                    "' deleted."
                    );

                    return 0;

                default:

                    throw new ServerManagerException(
                            "Unknown delete target: " +
                                    type
                    );
            }
        } catch (ServerManagerException e) {
            printError(e);
            return 1;
        }
    }

    public int update(String[] args) {

        if (args.length != 2) {

            System.err.println(
                    "Usage: server update <name> <version>"
            );

            return 1;
        }

        String name =
                args[0];

        final String targetVersion;

        try {

            targetVersion =
                    resolveVersion(
                            args[1]
                    );

            validateVersion(
                    targetVersion
            );

        } catch (ServerManagerException e) {

            printError(e);
            return 1;
        }

        String currentVersion;

        try {

            currentVersion =
                    serverManager
                            .getInstance(name)
                            .getVersion();

        } catch (ServerManagerException e) {

            printError(e);
            return 1;
        }

        if (currentVersion == null
                || currentVersion.isBlank()) {

            System.err.println(
                    "Could not determine the current " +
                            "BDS version for '" +
                            name +
                            "'."
            );

            return 1;
        }

        int comparison =
                compareVersions(
                        targetVersion,
                        currentVersion
                );

        if (comparison == 0) {

            System.err.println(
                    "Server '" +
                            name +
                            "' is already on BDS " +
                            currentVersion +
                            "."
            );

            return 1;
        }

        if (comparison < 0) {

            System.err.println(
                    "Refusing to downgrade server '" +
                            name +
                            "' from " +
                            currentVersion +
                            " to " +
                            targetVersion +
                            "."
            );

            return 1;
        }

        Path updateBackup =
                serverManager
                        .getUpdateBackupsDir()
                        .resolve(name);

        try {

            /*
             * ServerManager creates a temporary complete copy
             * and removes the old live server.
             */
            serverManager.updateServer(name);

        } catch (ServerManagerException e) {

            printError(e);
            return 1;
        }

        System.out.println(
                "Updating '" +
                        name +
                        "' from " +
                        currentVersion +
                        " to " +
                        targetVersion +
                        "..."
        );

        /*
         * create() builds a completely fresh BDS installation.
         * If anything fails, restore the old installation from
         * the temporary update backup.
         */
        int createResult =
                create(
                        new String[]{
                                name,
                                targetVersion
                        }
                );

        if (createResult != 0) {

            System.err.println(
                    "New BDS installation failed. " +
                            "Attempting rollback..."
            );

            if (rollbackUpdate(
                    name,
                    updateBackup
            )) {

                System.err.println(
                        "Rollback completed successfully."
                );

            } else {

                System.err.println(
                        "CRITICAL: automatic rollback failed."
                );
            }

            return 1;
        }

        try {

            Path newServerDirectory =
                    serverManager
                            .getServerDirectory(
                                    name
                            );

            Path oldWorlds =
                    updateBackup
                            .resolve("worlds");

            Path newWorlds =
                    newServerDirectory
                            .resolve("worlds");

            if (Files.isDirectory(oldWorlds)) {

                if (Files.exists(newWorlds)) {
                    deleteDirectory(newWorlds);
                }

                copyDirectory(
                        oldWorlds,
                        newWorlds
                );
            }

            preserveServerProperties(
                    updateBackup,
                    newServerDirectory
            );

            /*
             * Update succeeded. The temporary old server is
             * no longer needed.
             */
            deleteDirectory(
                    updateBackup
            );

            System.out.println(
                    "Server '" +
                            name +
                            "' updated successfully."
            );

            System.out.println(
                    "  " +
                            currentVersion +
                            " -> " +
                            targetVersion
            );

            return 0;

        } catch (Exception e) {

            System.err.println(
                    "Failed while transferring server data: " +
                            e.getMessage()
            );

            System.err.println(
                    "Attempting rollback..."
            );

            if (rollbackUpdate(
                    name,
                    updateBackup
            )) {

                System.err.println(
                        "Rollback completed successfully."
                );

            } else {

                System.err.println(
                        "CRITICAL: automatic rollback failed."
                );
            }

            return 1;
        }
    }

    private void preserveServerProperties(
            Path oldServerDirectory,
            Path newServerDirectory
    ) throws IOException {

        Path oldProperties =
                oldServerDirectory.resolve(
                        "server.properties"
                );

        if (!Files.isRegularFile(
                oldProperties
        )) {
            return;
        }

        String[] preservedSettings = {
                "server-name",
                "gamemode",
                "difficulty",
                "allow-cheats",
                "max-players",
                "online-mode",
                "allow-list",
                "level-name",
                "level-seed",
                "server-port",
                "server-portv6",
                "view-distance",
                "tick-distance",
                "player-idle-timeout"
        };

        for (String key :
                preservedSettings) {

            String value =
                    serverManager.getVariable(
                            oldServerDirectory.getFileName().toString(),
                            key
                    );

            if (value == null) {
                continue;
            }

            try {

                serverManager.setConfig(
                        key,
                        value,
                        newServerDirectory
                                .getFileName()
                                .toString()
                );
            } catch (ServerManagerException e) {
                System.err.println(e.getMessage());
            }
        }
    }

    private boolean rollbackUpdate(
            String name,
            Path updateBackup
    ) {

        try {

            if (!Files.isDirectory(
                    updateBackup
            )) {
                return false;
            }

            Path currentServer =
                    serverManager
                            .getServerDirectory(
                                    name
                            );

            if (Files.exists(currentServer)) {

                try {

                    if (serverManager
                            .getInstance(name)
                            .isRunning()) {

                        serverManager.stopServer(
                                name
                        );
                    }

                } catch (ServerManagerException ignored) {
                }

                deleteDirectory(
                        currentServer
                );
            }

            Path restoredServer =
                    serverManager.createServer(
                            name
                    );

            deleteDirectory(
                    restoredServer
            );

            copyDirectory(
                    updateBackup,
                    restoredServer
            );

            System.out.println(
                    "Restored previous server installation."
            );

            return true;

        } catch (Exception e) {

            System.err.println(
                    "Rollback error: " +
                            e.getMessage()
            );

            return false;
        }
    }

    public int config(
            String[] args
    ) {

        if (args.length < 3) {

            System.err.println(
                    "Usage: server config " +
                            "<get|set> <name> <key> [value]"
            );

            return 1;
        }

        String operation =
                args[0];

        String name =
                args[1];

        String key =
                args[2];


        try {

            ServerInstance server =
                    serverManager.getInstance(
                            name
                    );

            if (server.isRunning()) {

                throw new ServerManagerException(
                        "Cannot change server.properties " +
                                "while the server is running. " +
                                "Stop it first."
                );
            }

            if (args[0].equalsIgnoreCase("set")) {

                if (args.length!=4)
                    throw new ServerManagerException(
                            "Usage: server config set " +
                                    "<name> <key> <value>"
                    );

                String value = args[3];

                serverManager.setConfig(
                        key,
                        value,
                        name
                );

                System.out.println(
                        "Updated " +
                                key +
                                "=" +
                                value
                );
            } else if (operation.equalsIgnoreCase("get")) {

                if (args.length!=3)
                    throw new ServerManagerException(
                            "Usage: server config get " +
                                "<name> <key>"
                    );
                System.out.println(
                        serverManager.getVariable(
                                name,
                                key
                        )
                );
            } else
                System.err.println(usageArguments("config"));

            return 0;

        } catch (
                ServerManagerException |
                IOException e
        ) {

            printError(
                    e instanceof ServerManagerException
                            ? (ServerManagerException) e
                            : new ServerManagerException(
                            "Failed to change configuration.",
                            e
                    )
            );

            return 1;
        }
    }

    public int rename(String[] args) {

        String name = args[0];
        String newName = args[1];

        try {

            serverManager.renameServer(
                    name,
                    newName
            );

            System.out.println("Renamed server '" + name + "' to '" + newName + "'");

            return 0;

        } catch (ServerManagerException e) {

            printError(e);
            return 1;
        }
    }

    public int logs(String[] args)
            throws ServerManagerException {

        String name = args[0];
        int count;

        try {
            count = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            System.err.println(
                    usageArguments(
                            "Error: Count should be a number"
                    )
            );
            return 1;
        }

        List<String> logs;
        try {
            logs =
                    serverManager.getLogs(
                            name,
                            count
                    );
        } catch (IOException e) {
            System.err.println(
                    "Could not read logs: " +
                    e
            );
            return 1;
        }

        for (String log : logs) {
            System.out.println(log);
        }
        return 0;
    }

    private int compareVersions(
            String first,
            String second
    ) {

        String[] firstParts =
                first.split("\\.");

        String[] secondParts =
                second.split("\\.");

        int length =
                Math.max(
                        firstParts.length,
                        secondParts.length
                );

        for (int i = 0;
             i < length;
             i++) {

            int firstValue =
                    i < firstParts.length
                            ? Integer.parseInt(
                            firstParts[i]
                    )
                            : 0;

            int secondValue =
                    i < secondParts.length
                            ? Integer.parseInt(
                            secondParts[i]
                    )
                            : 0;

            if (firstValue != secondValue) {

                return Integer.compare(
                        firstValue,
                        secondValue
                );
            }
        }

        return 0;
    }

    private void requireArguments(
            String command,
            String[] args,
            int required
    ) throws ServerManagerException {

        if (args.length != required) {

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

            case "create",
                 "update" ->
                    " <name> <version>";

            case "start",
                 "stop",
                 "restart",
                 "status",
                 "players",
                 "console",
                 "backup" ->
                    " <name>";

            case "exec" ->
                    " <name> <command>";

            case "import" ->
                    " <world|config> <server> <path>";

            case "list" ->
                    " <servers|backups>";

            case "restore" ->
                    " <name> <world-name>";

            case "config" ->
                    " <get|set> <name> <key> [value]";

            case "delete" ->
                    " <server|backup|logs> <name> [count]";

            case "rename" ->
                    " <name> <new-name>";

            case "logs" ->
                    " <name> <count>";

            default ->
                    "";
        };
    }

    private void printServerHelp() {

        System.out.println("""
                
                Server commands:
                
                  server list <servers|backups>
                      List configured servers or backups.
                
                  server create <name> <version>
                      Create a server using the specified BDS version.
                      Use "latest" for the newest version.
                
                  server start <name>
                      Start a server.
                
                  server stop <name>
                      Stop a server.
                
                  server restart <name>
                      Restart a server.
                
                  server status <name>
                      Show server status, PID, RAM and CPU.
                
                  server players <name>
                      Show connected players.
                
                  server console <name>
                      Attach to the server console.
                
                  server exec <name> <command>
                      Execute a command on a server.
                
                  server backup <name>
                      Create a backup of all worlds.
                
                  server delete server <name>
                      Delete a stopped server.
                
                  server rename <name> <new-name>
                      Change a server's name.
                
                  server delete backup <name> <count>
                      Delete the oldest backup snapshots.
                
                  server import <world|config> <server> <path>
                      Import a Minecraft world or server.properties.
                
                  server update <name> <version>
                      Update a server to a newer BDS version.
                      Use "latest" for the newest version.
                
                  server restore <name> <world-name>
                      Interactively restore a world backup.
                
                  server config <get|set> <name> <key> [value]
                      Change a server.properties value.
                
                  server logs <name> <count>
                      Get last x lines of logs from a server
                
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

    private List<String> parseArguments(
            String input
    ) {

        List<String> args =
                new java.util.ArrayList<>();

        Matcher matcher =
                ARGUMENT_PATTERN.matcher(
                        input
                );

        while (matcher.find()) {

            if (matcher.group(1) != null) {

                args.add(
                        matcher.group(1)
                );

            } else if (
                    matcher.group(2) != null
            ) {

                args.add(
                        matcher.group(2)
                );

            } else {

                args.add(
                        matcher.group(3)
                );
            }
        }

        return args;
    }

    private static void deleteDirectory(
            Path directory
    ) throws IOException {

        if (!Files.exists(directory)) {
            return;
        }

        try (var paths =
                     Files.walk(directory)) {

            for (Path path :
                    paths.sorted(
                            java.util.Comparator
                                    .reverseOrder()
                    ).toList()) {

                Files.deleteIfExists(path);
            }
        }
    }
}