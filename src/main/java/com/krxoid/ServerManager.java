package com.krxoid;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ServerManager {

    private static final Path ROOT =
            Path.of(
                    System.getProperty("user.home"),
                    ".local",
                    "share",
                    "rock-core-server"
            );

    private static final Path SERVERS_DIR =
            ROOT.resolve("servers");

    private static final Path BACKUPS_DIR =
            ROOT.resolve("backups");

    private static final DateTimeFormatter BACKUP_FORMAT =
            DateTimeFormatter.ofPattern(
                    "yyyyMMdd-HHmmss"
            );

    private final Map<String, ServerInstance> instances =
            new HashMap<>();

    public ServerManager() {
        try {
            initializeDirectories();
            loadServers();
        } catch (ServerManagerException e) {
            throw new IllegalStateException(
                    "Failed to initialize Rock Core.",
                    e
            );
        }
    }

    public void listServers()
            throws ServerManagerException {

        initializeDirectories();

        try {
            List<Path> directories =
                    Files.list(SERVERS_DIR)
                            .filter(Files::isDirectory)
                            .sorted(
                                    Comparator.comparing(
                                            path ->
                                                    path.getFileName()
                                                            .toString()
                                    )
                            )
                            .toList();

            if (directories.isEmpty()) {
                System.out.println(
                        "No servers configured."
                );
                return;
            }

            System.out.printf(
                    "%-24s %-10s %-10s%n",
                    "NAME",
                    "STATUS",
                    "PID"
            );

            System.out.println(
                    "-".repeat(48)
            );

            for (Path directory : directories) {

                String name =
                        directory.getFileName()
                                .toString();

                ServerInstance server =
                        getInstance(name);

                String status =
                        server.isRunning()
                                ? "running"
                                : "stopped";

                String pid =
                        server.isRunning()
                                ? Long.toString(
                                server.getPid()
                        )
                                : "-";

                System.out.printf(
                        "%-24s %-10s %-10s%n",
                        name,
                        status,
                        pid
                );
            }

        } catch (IOException e) {
            throw new ServerManagerException(
                    "Failed to list servers.",
                    e
            );
        }
    }

    /**
     * Creates the filesystem layout for a new Bedrock server.
     * BDS itself is downloaded by ServerCommandHandler.
     */
    public Path createServer(String name)
            throws ServerManagerException {

        validateName(name);
        initializeDirectories();

        Path directory = serverPath(name);

        if (Files.exists(directory)) {
            throw new ServerManagerException(
                    "Server '" + name + "' already exists."
            );
        }

        try {
            Files.createDirectories(directory);
            Files.createDirectories(directory.resolve("worlds"));

            return directory;

        } catch (IOException e) {
            try {
                deleteDirectory(directory);
            } catch (IOException ignored) {
            }

            throw new ServerManagerException(
                    "Failed to create server '" + name + "'.",
                    e
            );
        }
    }

    public Path getServerDirectory(String name)
            throws ServerManagerException {
        validateName(name);
        return serverPath(name);
    }

    public void startServer(String name)
            throws ServerManagerException {

        getInstance(name).start();
    }

    public void stopServer(String name)
            throws ServerManagerException {

        getInstance(name).stop();
    }

    public void restartServer(String name)
            throws ServerManagerException {

        getInstance(name).restart();
    }

    public void printStatus(String name)
            throws ServerManagerException {

        ServerInstance server =
                getInstance(name);

        System.out.println(
                "Server: " + server.getName()
        );

        System.out.println(
                "Status: " +
                        (server.isRunning()
                                ? "running"
                                : "stopped")
        );

        if (server.isRunning()) {
            System.out.println(
                    "PID: " + server.getPid()
            );
        }

        System.out.println(
                "Directory: " +
                        server.getDirectory()
        );
    }

    public void changeConfig(String variable, String value, String name)
            throws ServerManagerException, IOException {

        Path configPath = getServerDirectory(name).resolve("server.properties");
        List<String> config = Files.readAllLines(configPath);

        boolean found = false;

        for (int i = 0; i < config.size(); i++) {
            String line = config.get(i).trim();

            // Ignore comments and blank lines
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            int separator = line.indexOf('=');

            if (separator == -1) {
                continue;
            }

            String key = line.substring(0, separator).trim();

            if (key.equals(variable)) {
                config.set(i, key + "=" + value);
                found = true;
                break;
            }
        }

        // If the property doesn't exist, add it
        if (!found) {
            config.add(variable + "=" + value);
        }

        Files.write(configPath, config);
    }


    public void printPlayers(String name)
            throws ServerManagerException {

        getInstance(name).sendCommand("list");
    }

    public void attachConsole(String name)
            throws ServerManagerException {

        getInstance(name).attachConsole();
    }

    public void sendCommand(
            String name,
            String command
    ) throws ServerManagerException {

        getInstance(name).sendCommand(command);
    }

    public void createBackup(String name)
            throws ServerManagerException {

        ServerInstance server =
                getInstance(name);

        Path worlds =
                server.getDirectory()
                        .resolve("worlds");

        if (!Files.exists(worlds)) {
            throw new ServerManagerException(
                    "World directory does not exist."
            );
        }

        Path backupDirectory =
                BACKUPS_DIR.resolve(name);

        String timestamp =
                LocalDateTime.now()
                        .format(BACKUP_FORMAT);

        Path destination =
                backupDirectory.resolve(timestamp);

        try {
            Files.createDirectories(
                    destination
            );

            copyDirectory(
                    worlds,
                    destination
            );

            System.out.println(
                    "Backup created:"
            );

            System.out.println(
                    destination
            );

        } catch (IOException e) {
            throw new ServerManagerException(
                    "Failed to create backup.",
                    e
            );
        }
    }

    public void deleteServer(String name)
            throws ServerManagerException {

        ServerInstance server =
                getInstance(name);

        if (server.isRunning()) {
            throw new ServerManagerException(
                    "Cannot delete a running server. " +
                            "Stop it first."
            );
        }

        try {
            deleteDirectory(
                    server.getDirectory()
            );

            instances.remove(name);

        } catch (IOException e) {
            throw new ServerManagerException(
                    "Failed to delete server '" +
                            name +
                            "'.",
                    e
            );
        }
    }

    private ServerInstance getInstance(
            String name
    ) throws ServerManagerException {

        validateName(name);

        ServerInstance existing =
                instances.get(name);

        if (existing != null) {
            return existing;
        }

        Path directory =
                serverPath(name);

        if (!Files.isDirectory(directory)) {
            throw new ServerManagerException(
                    "Server '" +
                            name +
                            "' does not exist."
            );
        }

        ServerInstance instance =
                new ServerInstance(
                        name,
                        directory
                );

        instances.put(
                name,
                instance
        );

        return instance;
    }

    private void loadServers()
            throws ServerManagerException {

        if (!Files.isDirectory(SERVERS_DIR)) {
            return;
        }

        try {
            Files.list(SERVERS_DIR)
                    .filter(Files::isDirectory)
                    .forEach(path -> {

                        String name =
                                path.getFileName()
                                        .toString();

                        instances.put(
                                name,
                                new ServerInstance(
                                        name,
                                        path
                                )
                        );
                    });

        } catch (IOException e) {
            throw new ServerManagerException(
                    "Failed to load server instances.",
                    e
            );
        }
    }

    private void initializeDirectories()
            throws ServerManagerException {

        try {
            Files.createDirectories(ROOT);
            Files.createDirectories(SERVERS_DIR);
            Files.createDirectories(BACKUPS_DIR);

        } catch (IOException e) {
            throw new ServerManagerException(
                    "Failed to initialize Rock Core directories.",
                    e
            );
        }
    }

    private Path serverPath(String name) {
        return SERVERS_DIR.resolve(name);
    }

    private void validateName(String name)
            throws ServerManagerException {

        if (name == null || name.isBlank()) {
            throw new ServerManagerException(
                    "Server name cannot be empty."
            );
        }

        if (!name.matches(
                "[a-zA-Z0-9_-]+"
        )) {
            throw new ServerManagerException(
                    "Invalid server name. " +
                            "Use only letters, numbers, '-' and '_'."
            );
        }
    }

    private void copyDirectory(
            Path source,
            Path destination
    ) throws IOException {

        try (var paths = Files.walk(source)) {

            for (Path path : paths.toList()) {

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
                            StandardCopyOption.REPLACE_EXISTING
                    );
                }
            }
        }
    }

    private void deleteDirectory(
            Path directory
    ) throws IOException {

        try (var paths = Files.walk(directory)) {

            List<Path> pathsToDelete =
                    paths.sorted(
                            Comparator.reverseOrder()
                    ).toList();

            for (Path path : pathsToDelete) {
                Files.deleteIfExists(path);
            }
        }
    }
}
