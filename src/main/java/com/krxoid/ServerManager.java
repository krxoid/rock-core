package com.krxoid;

import org.jline.reader.LineReader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

public final class ServerManager {

    public static final Path ROOT =
            Path.of(
                    System.getProperty("user.home"),
                    ".local",
                    "share",
                    "rock-core"
            );

    private static final Path SERVERS_DIR =
            ROOT.resolve("servers");

    private static final Path BACKUPS_DIR =
            ROOT.resolve("backups");

    private static final Path TEMPORARY_DIR =
            Path.of(
                    System.getProperty("java.io.tmpdir"),
                    "rock-core"
            );

    private static final Path UPDATE_BACKUPS_DIR =
            TEMPORARY_DIR.resolve("update_backups");

    private static final DateTimeFormatter BACKUP_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String BACKUP_TIMESTAMP_REGEX =
            "\\d{8}-\\d{6}";

    private static final Comparator<String> VERSION_COMPARATOR =
            ServerManager::compareVersions;

    private final Map<String, ServerInstance> instances =
            new HashMap<>();

    final LineReader lineReader;

    public ServerManager(LineReader lineReader) {
        this.lineReader = Objects.requireNonNull(
                lineReader,
                "lineReader"
        );

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

    public Path getUpdateBackupsDir() {
        return UPDATE_BACKUPS_DIR;
    }

    public Path getBackupsDir() {
        return BACKUPS_DIR;
    }

    public LineReader getLineReader() {
        return lineReader;
    }

    public boolean isIdle() {
        return instances.values()
                .stream()
                .noneMatch(ServerInstance::isRunning);
    }

    public void listServers(String modifier)
            throws ServerManagerException, IOException {

        initializeDirectories();

        if (!"servers".equals(modifier)
                && !"backups".equals(modifier)) {

            throw new ServerManagerException(
                    "Usage: server list <servers|backups>"
            );
        }

        Path targetDirectory =
                "backups".equals(modifier)
                        ? BACKUPS_DIR
                        : SERVERS_DIR;

        try (Stream<Path> stream = Files.list(targetDirectory)) {

            List<Path> directories = stream
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
                        "backups".equals(modifier)
                                ? "No backups found."
                                : "No servers configured."
                );
                return;
            }

            if ("backups".equals(modifier)) {
                listBackups(directories);
            } else {
                listServers(directories);
            }
        }
    }

    private void listServers(List<Path> directories)
            throws IOException {

        System.out.println("SERVERS");

        System.out.printf(
                "%-20s %-12s %-10s %-14s %-10s%n",
                "NAME",
                "STATUS",
                "PID",
                "VERSION",
                "SIZE"
        );

        System.out.println("─".repeat(70));

        for (Path directory : directories) {

            String name =
                    directory.getFileName().toString();

            ServerInstance server;

            try {
                server =
                        getInstance(name);
            } catch (ServerManagerException e) {
                System.err.println(e.getMessage());
                return;
            }

            boolean running =
                    server.isRunning();

            String version =
                    server.getVersion();

            long size =
                    getDirectorySize(directory);

            System.out.printf(
                    "%-20s %-12s %-10s %-14s %-10s%n",
                    name,
                    running ? "running" : "stopped",
                    running
                            ? Long.toString(server.getPid())
                            : "-",
                    version != null
                            ? version
                            : "-",
                    formatSize(size)
            );
        }

        System.out.println("─".repeat(70));

        System.out.printf(
                "%-20s %s%n",
                "TOTAL",
                formatSize(
                        getTotalDirectorySize(directories)
                )
        );
    }

    private void listBackups(List<Path> serverDirectories)
            throws IOException {

        System.out.println("WORLD BACKUPS");

        for (int i = 0;
             i < serverDirectories.size();
             i++) {

            Path serverDirectory =
                    serverDirectories.get(i);

            String serverName =
                    serverDirectory
                            .getFileName()
                            .toString();

            boolean lastServer =
                    i == serverDirectories.size() - 1;

            String branch =
                    lastServer ? "└──" : "├──";

            String pipe =
                    lastServer ? "   " : "│  ";

            System.out.println(
                    branch + " " + serverName
            );

            try (Stream<Path> worldStream =
                         Files.list(serverDirectory)) {

                List<Path> worlds =
                        worldStream
                                .filter(Files::isDirectory)
                                .sorted(
                                        Comparator.comparing(
                                                path ->
                                                        path.getFileName()
                                                                .toString()
                                        )
                                )
                                .toList();

                if (worlds.isEmpty()) {
                    System.out.println(
                            pipe + "└── No world backups"
                    );
                    continue;
                }

                for (int j = 0;
                     j < worlds.size();
                     j++) {

                    Path worldDirectory =
                            worlds.get(j);

                    String worldName =
                            worldDirectory
                                    .getFileName()
                                    .toString();

                    boolean lastWorld =
                            j == worlds.size() - 1;

                    String worldBranch =
                            lastWorld ? "└──" : "├──";

                    String worldPipe =
                            lastWorld
                                    ? "    "
                                    : "│   ";

                    System.out.println(
                            pipe +
                                    worldBranch +
                                    " " +
                                    worldName
                    );

                    try (Stream<Path> backupStream =
                                 Files.list(worldDirectory)) {

                        List<Path> backups =
                                backupStream
                                        .filter(Files::isDirectory)
                                        .sorted(
                                                Comparator.comparing(
                                                        path ->
                                                                path.getFileName()
                                                                        .toString(),
                                                        Comparator.reverseOrder()
                                                )
                                        )
                                        .toList();

                        if (backups.isEmpty()) {
                            System.out.println(
                                    pipe +
                                            worldPipe +
                                            "└── No backups"
                            );
                            continue;
                        }

                        for (int k = 0;
                             k < backups.size();
                             k++) {

                            Path backup =
                                    backups.get(k);

                            String timestamp =
                                    backup.getFileName()
                                            .toString();

                            String formatted =
                                    formatTimestamp(timestamp);

                            String size =
                                    formatSize(
                                            getDirectorySize(
                                                    backup
                                            )
                                    );

                            boolean lastBackup =
                                    k == backups.size() - 1;

                            String backupBranch =
                                    lastBackup
                                            ? "└──"
                                            : "├──";

                            System.out.printf(
                                    "%s%s %s  %s%n",
                                    pipe +
                                            worldPipe,
                                    backupBranch,
                                    formatted,
                                    size
                            );
                        }

                        long totalSize = 0;

                        for (Path backup : backups) {
                            totalSize +=
                                    getDirectorySize(backup);
                        }

                        System.out.printf(
                                "%s       Total: %s%n",
                                pipe + worldPipe,
                                formatSize(totalSize)
                        );
                    }
                }
            }
        }
    }

    private long getDirectorySize(Path directory)
            throws IOException {

        if (!Files.exists(directory)) {
            return 0L;
        }

        try (Stream<Path> stream =
                     Files.walk(directory)) {

            return stream
                    .filter(Files::isRegularFile)
                    .mapToLong(path -> {

                        try {
                            return Files.size(path);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }

                    })
                    .sum();

        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private String formatSize(long bytes) {

        if (bytes < 1024L) {
            return bytes + " B";
        }

        if (bytes < 1024L * 1024L) {
            return String.format(
                    "%.1f KB",
                    bytes / 1024.0
            );
        }

        if (bytes < 1024L * 1024L * 1024L) {
            return String.format(
                    "%.1f MB",
                    bytes / (1024.0 * 1024.0)
            );
        }

        return String.format(
                "%.1f GB",
                bytes / (1024.0 * 1024.0 * 1024.0)
        );
    }

    private String formatTimestamp(String timestamp) {

        try {
            LocalDateTime dateTime =
                    LocalDateTime.parse(
                            timestamp,
                            BACKUP_FORMAT
                    );

            return dateTime.format(
                    DISPLAY_FORMAT
            );

        } catch (Exception ignored) {
            return timestamp;
        }
    }

    private long getTotalDirectorySize(
            List<Path> directories
    ) throws IOException {

        long total = 0L;

        for (Path directory : directories) {
            total += getDirectorySize(directory);
        }

        return total;
    }

    /**
     * Creates the filesystem layout for a new Bedrock server.
     * BDS itself is downloaded by ServerCommandHandler.
     */
    public Path createServer(String name)
            throws ServerManagerException {

        validateName(name);
        initializeDirectories();

        Path directory =
                serverPath(name);

        if (Files.exists(directory)) {
            throw new ServerManagerException(
                    "Server '" + name +
                            "' already exists."
            );
        }

        try {
            Files.createDirectories(
                    directory
            );

            Files.createDirectories(
                    directory.resolve("worlds")
            );

            loadServers();

            return directory;

        } catch (IOException e) {

            try {
                if (Files.exists(directory)) {
                    deleteDirectory(directory);
                }
            } catch (IOException ignored) {
            }

            throw new ServerManagerException(
                    "Failed to create server '" +
                            name +
                            "'.",
                    e
            );
        }
    }

    public List<Path> updateServer(String name)
            throws ServerManagerException {

        validateName(name);
        initializeDirectories();

        Path serverDirectory =
                getServerDirectory(name);

        if (!Files.isDirectory(serverDirectory)) {
            throw new ServerManagerException(
                    "Server '" + name +
                            "' does not exist."
            );
        }

        Path backupDirectory =
                UPDATE_BACKUPS_DIR.resolve(name);

        try {
            /*
             * Remove any stale update backup first.
             * Otherwise, files deleted from the current server
             * can survive inside an old temporary backup.
             */
            if (Files.exists(backupDirectory)) {
                deleteDirectory(backupDirectory);
            }

            copyDirectory(
                    serverDirectory,
                    backupDirectory
            );

            /*
             * Verify that the backup contains the server's
             * worlds directory before removing the live copy.
             */
            Path backupWorlds =
                    backupDirectory.resolve("worlds");

            if (!Files.isDirectory(backupWorlds)) {
                throw new ServerManagerException(
                        "Update backup is missing the worlds directory."
                );
            }

            /*
             * The live server is now safely backed up.
             * Remove its directory and its in-memory instance.
             */
            deleteDirectory(serverDirectory);
            instances.remove(name);

            List<Path> worlds =
                    new ArrayList<>();

            try (DirectoryStream<Path> stream =
                         Files.newDirectoryStream(backupWorlds)) {

                for (Path folder : stream) {
                    if (Files.isDirectory(folder)) {
                        worlds.add(folder);
                    }
                }
            }

            return worlds;

        } catch (ServerManagerException e) {
            throw e;

        } catch (IOException e) {
            throw new ServerManagerException(
                    "Failed to prepare server '" +
                            name +
                            "' for update.",
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
                "\n===" +
                        server.getName() +
                        "===\n"
        );

        System.out.println(
                "Status: " +
                        (server.isRunning()
                                ? "running"
                                : "stopped")
        );

        if (!server.isRunning()) {
            return;
        }

        System.out.println(
                "PID: " + server.getPid()
        );

        try {

            System.out.println(
                    "RAM: " +
                            formatSize(
                                    server.getRamUsage()
                            )
            );

            long previousCpuNanos =
                    server.getCpuTime();

            long sampleTimeMs = 250L;

            Thread.sleep(sampleTimeMs);

            long currentCpuNanos =
                    server.getCpuTime();

            double cpu =
                    calculateCpuUsage(
                            previousCpuNanos,
                            currentCpuNanos,
                            sampleTimeMs
                    );

            System.out.printf(
                    "CPU: %.1f%%%n",
                    cpu
            );

        } catch (ServerManagerException e) {

            throw new ServerManagerException(
                    "Failed to read server status.",
                    e
            );

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            throw new ServerManagerException(
                    "Status sampling interrupted.",
                    e
            );
        }
    }

    public double calculateCpuUsage(
            long previousCpuNanos,
            long currentCpuNanos,
            long elapsedMs
    ) {

        if (elapsedMs <= 0
                || currentCpuNanos < previousCpuNanos) {
            return 0.0;
        }

        double cpuSeconds =
                (currentCpuNanos - previousCpuNanos)
                        / 1_000_000_000.0;

        double elapsedSeconds =
                elapsedMs / 1000.0;

        return (cpuSeconds / elapsedSeconds)
                * 100.0;
    }

    public int getThreadCount() {
        return Runtime.getRuntime()
                .availableProcessors();
    }

    public void changeConfig(
            String variable,
            String value,
            String name
    ) throws ServerManagerException, IOException {

        Objects.requireNonNull(
                variable,
                "variable"
        );

        Objects.requireNonNull(
                value,
                "value"
        );

        Path configPath =
                getServerDirectory(name)
                        .resolve("server.properties");

        if (!Files.isRegularFile(configPath)) {
            throw new ServerManagerException(
                    "server.properties does not exist."
            );
        }

        List<String> config =
                Files.readAllLines(configPath);

        boolean found = false;

        for (int i = 0;
             i < config.size();
             i++) {

            String line =
                    config.get(i).trim();

            if (line.isEmpty()
                    || line.startsWith("#")) {
                continue;
            }

            int separator =
                    line.indexOf('=');

            if (separator == -1) {
                continue;
            }

            String key =
                    line.substring(
                            0,
                            separator
                    ).trim();

            if (key.equals(variable)) {

                config.set(
                        i,
                        key + "=" + value
                );

                found = true;
                break;
            }
        }

        if (!found) {
            config.add(
                    variable + "=" + value
            );
        }

        Files.write(
                configPath,
                config,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
        );
    }

    public void printPlayers(String name)
            throws ServerManagerException {

        getInstance(name)
                .sendCommand("list");
    }

    public void attachConsole(String name)
            throws ServerManagerException {

        getInstance(name)
                .attachConsole();
    }

    public void sendCommand(
            String name,
            String command
    ) throws ServerManagerException {

        getInstance(name)
                .sendCommand(command);
    }

    public void createBackup(String name)
            throws ServerManagerException {

        ServerInstance server =
                getInstance(name);

        /*
         * Never copy a live Bedrock world directly.
         * A filesystem copy while LevelDB is being modified
         * is not guaranteed to be consistent.
         */
        if (server.isRunning()) {
            throw new ServerManagerException(
                    "Cannot create a backup while the server " +
                            "is running. Stop it first."
            );
        }

        Path worlds =
                server.getDirectory()
                        .resolve("worlds");

        if (!Files.isDirectory(worlds)) {
            throw new ServerManagerException(
                    "World directory does not exist."
            );
        }

        List<Path> worldDirectories;

        try (Stream<Path> stream =
                     Files.list(worlds)) {

            worldDirectories =
                    stream
                            .filter(Files::isDirectory)
                            .toList();

        } catch (IOException e) {

            throw new ServerManagerException(
                    "Failed to inspect world directory.",
                    e
            );
        }

        if (worldDirectories.isEmpty()) {
            throw new ServerManagerException(
                    "No worlds found."
            );
        }

        String timestamp =
                createUniqueBackupTimestamp(
                        name
                );

        try {

            Path serverBackupDirectory =
                    BACKUPS_DIR.resolve(name);

            Files.createDirectories(
                    serverBackupDirectory
            );

            for (Path world : worldDirectories) {

                String worldName =
                        world.getFileName()
                                .toString();

                Path destination =
                        serverBackupDirectory
                                .resolve(worldName)
                                .resolve(timestamp);

                Files.createDirectories(
                        destination
                );

                try {
                    copyDirectory(
                            world,
                            destination
                    );
                } catch (IOException e) {

                    /*
                     * Never leave a partially-created backup
                     * pretending to be valid.
                     */
                    deleteDirectory(
                            destination
                    );

                    throw e;
                }
            }

            System.out.println(
                    "Backup created successfully."
            );

            System.out.println();
            System.out.println(
                    "  Server: " + name
            );

            System.out.println(
                    "  Backup: " + timestamp
            );

        } catch (IOException e) {

            throw new ServerManagerException(
                    "Failed to create backup.",
                    e
            );
        }
    }

    public List<String> getBackups(
            String name,
            String worldName
    ) throws IOException {

        Path worldBackups =
                BACKUPS_DIR
                        .resolve(name)
                        .resolve(worldName);

        if (!Files.isDirectory(worldBackups)) {
            return List.of();
        }

        try (Stream<Path> stream =
                     Files.list(worldBackups)) {

            return stream
                    .filter(Files::isDirectory)
                    .map(path ->
                            path.getFileName()
                                    .toString())
                    .sorted(
                            Comparator.reverseOrder()
                    )
                    .toList();
        }
    }

    public void restoreBackup(
            String name,
            String worldName,
            String date
    ) throws ServerManagerException {

        validateName(name);

        if (worldName == null
                || worldName.isBlank()) {
            throw new ServerManagerException(
                    "World name cannot be empty."
            );
        }

        if (date == null
                || date.isBlank()) {
            throw new ServerManagerException(
                    "Backup date cannot be empty."
            );
        }

        ServerInstance server =
                getInstance(name);

        if (server.isRunning()) {
            throw new ServerManagerException(
                    "Cannot restore a backup while the server " +
                            "is running. Stop it first."
            );
        }

        Path serverWorld =
                SERVERS_DIR
                        .resolve(name)
                        .resolve("worlds")
                        .resolve(worldName);

        Path backupWorld =
                BACKUPS_DIR
                        .resolve(name)
                        .resolve(worldName)
                        .resolve(date);

        if (!Files.isDirectory(backupWorld)) {
            throw new ServerManagerException(
                    "Backup '" +
                            date +
                            "' does not exist for world '" +
                            worldName +
                            "'."
            );
        }

        Path worldsDirectory =
                SERVERS_DIR
                        .resolve(name)
                        .resolve("worlds");

        try {
            Files.createDirectories(
                    worldsDirectory
            );

            /*
             * Protect the currently installed world before
             * replacing it.
             */
            if (Files.isDirectory(serverWorld)) {

                String safetyTimestamp =
                        LocalDateTime.now()
                                .format(BACKUP_FORMAT) +
                                "-pre-restore-" +
                                UUID.randomUUID()
                                        .toString()
                                        .substring(0, 8);

                Path safetyBackup =
                        BACKUPS_DIR
                                .resolve(name)
                                .resolve(worldName)
                                .resolve(safetyTimestamp);

                copyDirectory(
                        serverWorld,
                        safetyBackup
                );
            }

            Path temporaryRestore =
                    worldsDirectory.resolve(
                            "." + worldName + ".restore-" +
                                    UUID.randomUUID()
                                            .toString()
                    );

            /*
             * Copy to a temporary location first.
             * This prevents a failed copy from leaving a
             * half-restored world.
             */
            copyDirectory(
                    backupWorld,
                    temporaryRestore
            );

            if (Files.exists(serverWorld)) {
                deleteDirectory(serverWorld);
            }

            try {
                Files.move(
                        temporaryRestore,
                        serverWorld,
                        StandardCopyOption.ATOMIC_MOVE
                );
            } catch (AtomicMoveNotSupportedException e) {

                Files.move(
                        temporaryRestore,
                        serverWorld
                );
            }

            System.out.println(
                    "Backup restored successfully."
            );

            System.out.println();
            System.out.println(
                    "  Server:   " + name
            );

            System.out.println(
                    "  World:    " + worldName
            );

            System.out.println(
                    "  Backup:   " + date
            );

            System.out.println(
                    "  Restored: worlds/" +
                            worldName
            );

        } catch (IOException e) {

            throw new ServerManagerException(
                    "Failed to restore world '" +
                            worldName +
                            "'.",
                    e
            );
        }
    }

    public void deleteBackup(
            String name,
            int count
    ) throws ServerManagerException {

        validateName(name);

        if (count <= 0) {
            throw new ServerManagerException(
                    "Backup count must be greater than 0."
            );
        }

        Path serverBackupDirectory =
                BACKUPS_DIR.resolve(name);

        if (!Files.isDirectory(
                serverBackupDirectory)) {

            throw new ServerManagerException(
                    "No backups found for server '" +
                            name +
                            "'."
            );
        }

        try {

            /*
             * One timestamp represents one complete backup
             * operation across all worlds.
             */
            Set<String> timestamps =
                    new HashSet<>();

            try (Stream<Path> worlds =
                         Files.list(
                                 serverBackupDirectory
                         )) {

                for (Path world : worlds.toList()) {

                    if (!Files.isDirectory(world)) {
                        continue;
                    }

                    try (Stream<Path> backups =
                                 Files.list(world)) {

                        backups
                                .filter(Files::isDirectory)
                                .map(path ->
                                        path.getFileName()
                                                .toString())
                                .filter(
                                        timestamp ->
                                                timestamp.matches(
                                                        BACKUP_TIMESTAMP_REGEX
                                                )
                                )
                                .forEach(
                                        timestamps::add
                                );
                    }
                }
            }

            if (timestamps.isEmpty()) {
                throw new ServerManagerException(
                        "No backups found for server '" +
                                name +
                                "'."
                );
            }

            List<String> oldestFirst =
                    timestamps.stream()
                            .sorted()
                            .toList();

            int deleteCount =
                    Math.min(
                            count,
                            oldestFirst.size()
                    );

            List<String> toDelete =
                    oldestFirst.subList(
                            0,
                            deleteCount
                    );

            try (Stream<Path> worlds =
                         Files.list(
                                 serverBackupDirectory
                         )) {

                for (Path world :
                        worlds.toList()) {

                    if (!Files.isDirectory(world)) {
                        continue;
                    }

                    for (String timestamp :
                            toDelete) {

                        Path backup =
                                world.resolve(
                                        timestamp
                                );

                        if (Files.isDirectory(
                                backup
                        )) {
                            deleteDirectory(
                                    backup
                            );
                        }
                    }
                }
            }

            /*
             * Remove empty world backup directories.
             */
            try (Stream<Path> worlds =
                         Files.list(
                                 serverBackupDirectory
                         )) {

                for (Path world :
                        worlds.toList()) {

                    if (!Files.isDirectory(world)) {
                        continue;
                    }

                    try (Stream<Path> contents =
                                 Files.list(world)) {

                        if (contents.findAny().isEmpty()) {
                            Files.deleteIfExists(world);
                        }
                    }
                }
            }

            System.out.println(
                    "Backups deleted successfully."
            );

            System.out.println();

            System.out.println(
                    "  Server:  " + name
            );

            System.out.println(
                    "  Deleted: " + deleteCount
            );

        } catch (IOException e) {

            throw new ServerManagerException(
                    "Failed to delete backups.",
                    e
            );
        }
    }

    public void deleteServer(String name)
            throws ServerManagerException {

        validateName(name);

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

    public ServerInstance getInstance(
            String name
    ) throws ServerManagerException {

        validateName(name);

        ServerInstance existing = instances.get(name);

        if (existing != null && existing.getVersion() != null) {
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

        String version;

        try {
            version =
                    getServerVersion(directory);

        } catch (IOException e) {

            throw new ServerManagerException(
                    "Could not determine version of server '" +
                            name +
                            "'.",
                    e
            );
        }

        ServerInstance instance =
                new ServerInstance(
                        name,
                        version,
                        directory,
                        lineReader
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

        try (Stream<Path> stream =
                     Files.list(SERVERS_DIR)) {

            stream
                    .filter(Files::isDirectory)
                    .forEach(path -> {

                        String name =
                                path.getFileName()
                                        .toString();

                        String version = null;

                        try {
                            version =
                                    getServerVersion(path);

                        } catch (IOException e) {

                            System.err.println(
                                    "Could not determine version " +
                                            "for '" +
                                            name +
                                            "'."
                            );
                        }

                        instances.put(
                                name,
                                new ServerInstance(
                                        name,
                                        version,
                                        path,
                                        lineReader
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

    public static String getServerVersion(
            Path serverPath
    ) throws IOException {

        Path packsDirectory =
                serverPath.resolve(
                        "behavior_packs"
                );

        if (!Files.isDirectory(packsDirectory)) {
            return null;
        }

        try (Stream<Path> stream =
                     Files.list(packsDirectory)) {

            return stream
                    .map(path ->
                            path.getFileName()
                                    .toString())
                    .filter(name ->
                            name.startsWith("vanilla_"))
                    .map(name ->
                            name.substring(
                                    "vanilla_".length()
                            ))
                    .filter(version ->
                            version.matches(
                                    "\\d+(\\.\\d+)+"
                            ))
                    .max(VERSION_COMPARATOR)
                    .orElse(null);
        }
    }

    private void initializeDirectories()
            throws ServerManagerException {

        try {

            Files.createDirectories(ROOT);
            Files.createDirectories(SERVERS_DIR);
            Files.createDirectories(BACKUPS_DIR);
            Files.createDirectories(TEMPORARY_DIR);
            Files.createDirectories(
                    UPDATE_BACKUPS_DIR
            );

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

        if (!Files.isDirectory(source)) {
            throw new IOException(
                    "Source directory does not exist: " +
                            source
            );
        }

        Files.createDirectories(destination);

        try (Stream<Path> stream =
                     Files.walk(source)) {

            for (Path path :
                    stream.toList()) {

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
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.COPY_ATTRIBUTES
                    );
                }
            }
        }
    }

    protected String getVariable(
            Path serverPath,
            String variable
    ) throws IOException {

        Path properties =
                serverPath.resolve(
                        "server.properties"
                );

        if (!Files.isRegularFile(properties)) {
            return null;
        }

        for (String line :
                Files.readAllLines(properties)) {

            line = line.trim();

            if (line.isEmpty()
                    || line.startsWith("#")) {
                continue;
            }

            int separator =
                    line.indexOf('=');

            if (separator == -1) {
                continue;
            }

            String key =
                    line.substring(
                            0,
                            separator
                    ).trim();

            if (key.equals(variable)) {
                return line.substring(
                        separator + 1
                ).trim();
            }
        }

        return null;
    }

    private void deleteDirectory(
            Path directory
    ) throws IOException {

        if (!Files.exists(directory)) {
            return;
        }

        try (Stream<Path> stream =
                     Files.walk(directory)) {

            List<Path> paths =
                    stream
                            .sorted(
                                    Comparator.reverseOrder()
                            )
                            .toList();

            for (Path path : paths) {
                Files.deleteIfExists(path);
            }
        }
    }

    private String createUniqueBackupTimestamp(
            String serverName
    ) throws ServerManagerException {

        String timestamp =
                LocalDateTime.now()
                        .format(BACKUP_FORMAT);

        Path serverBackupDirectory =
                BACKUPS_DIR.resolve(serverName);

        try {

            /*
             * Normal timestamps are preferred for clean
             * filesystem ordering and display.
             */
            if (!backupTimestampExists(
                    serverBackupDirectory,
                    timestamp
            )) {
                return timestamp;
            }

            /*
             * If two backups happen during the same second,
             * wait for the clock to advance instead of creating
             * a non-standard timestamp that would break the
             * backup-management logic.
             */
            for (int i = 0; i < 1000; i++) {

                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {

                    Thread.currentThread()
                            .interrupt();

                    throw new ServerManagerException(
                            "Backup timestamp generation interrupted.",
                            e
                    );
                }

                timestamp =
                        LocalDateTime.now()
                                .format(BACKUP_FORMAT);

                if (!backupTimestampExists(
                        serverBackupDirectory,
                        timestamp
                )) {
                    return timestamp;
                }
            }

        } catch (IOException e) {

            throw new ServerManagerException(
                    "Failed to generate backup timestamp.",
                    e
            );
        }

        throw new ServerManagerException(
                "Could not generate a unique backup timestamp."
        );
    }

    private boolean backupTimestampExists(
            Path serverBackupDirectory,
            String timestamp
    ) throws IOException {

        if (!Files.isDirectory(
                serverBackupDirectory
        )) {
            return false;
        }

        try (Stream<Path> worlds =
                     Files.list(
                             serverBackupDirectory
                     )) {

            for (Path world : worlds.toList()) {

                if (!Files.isDirectory(world)) {
                    continue;
                }

                if (Files.isDirectory(
                        world.resolve(timestamp)
                )) {
                    return true;
                }
            }
        }

        return false;
    }

    private static int compareVersions(
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
}