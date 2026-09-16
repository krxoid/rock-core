package com.krxoid;

import com.sun.management.OperatingSystemMXBean;

import javax.swing.*;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.jline.reader.LineReader;

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
            Path.of(System.getProperty("java.io.tmpdir"), "rock-core");

    private static final Path UPDATE_BACKUPS_DIR =
            TEMPORARY_DIR.resolve("update_backups");

    private static final DateTimeFormatter BACKUP_FORMAT =
            DateTimeFormatter.ofPattern(
                    "yyyyMMdd-HHmmss"
            );

    private final Map<String, ServerInstance> instances =
            new HashMap<>();

    private final LineReader lineReader;

    public ServerManager(LineReader lineReader) {
        this.lineReader = lineReader;

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

    public boolean isIdle() {
        return instances.values()
                .stream()
                .noneMatch(ServerInstance::isRunning);
    }

    public void listServers() throws ServerManagerException {

        initializeDirectories();

        try (var stream = Files.list(SERVERS_DIR)) {

            List<Path> directories = stream
                    .filter(Files::isDirectory)
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();

            if (directories.isEmpty()) {
                System.out.println("No servers configured.");
                return;
            }

            System.out.printf("%-20s %-10s %-8s %-12s%n",
                    "NAME", "STATUS", "PID", "VERSION");
            System.out.println("-".repeat(52));

            for (Path dir : directories) {
                String name = dir.getFileName().toString();
                ServerInstance server = getInstance(name);
                boolean running = server.isRunning();

                System.out.printf("%-20s %-10s %-8s %-12s%n",
                        name,
                        running ? "running" : "stopped",
                        running ? Long.toString(server.getPid()) : "-",
                        running || server.getVersion() != null
                                ? server.getVersion() : "-"
                );
            }

        } catch (IOException e) {
            throw new ServerManagerException("Failed to list servers.", e);
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

    public List<Path> updateServer(String name)
            throws ServerManagerException {

        validateName(name);
        initializeDirectories();

        try {
            copyDirectory(getServerDirectory(name), UPDATE_BACKUPS_DIR.resolve(name));
        } catch (IOException e) {
            throw new ServerManagerException(
                    "Could not find server '"
                            + name
                            + "'"
            );
        }

        try {
            deleteDirectory(getServerDirectory(name));
        } catch (IOException e) {
            throw new ServerManagerException(
                    "Could not delete directory '"
                            + getServerDirectory(name).normalize()
                            + "'"
            );
        }

        Path worlds = UPDATE_BACKUPS_DIR.resolve(name).resolve("worlds");

        try (DirectoryStream<Path> stream =
                     Files.newDirectoryStream(worlds)) {

            List<Path> folders = new ArrayList<>();

            for (Path folder : stream) {
                if (Files.isDirectory(folder)) {
                    folders.add(folder);
                }
            }

            return folders;

        } catch (IOException e) {
            e.printStackTrace();
            throw new ServerManagerException(
                    "Could not find world backups", e
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

        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();

        MemoryUsage heap = memory.getHeapMemoryUsage();

        OperatingSystemMXBean os =
                (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

        double cpu = os.getProcessCpuLoad() * 100;

        long used = heap.getUsed();
        long max = heap.getMax();

        String ramUsagePercentage = used/max * 100 + "%";

        System.out.println(
                "\n" + "===" + server.getName() + "==="  + "\n"
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
            try {
                System.out.println(
                        "Ram: " +
                                server.getRamUsage()/(1024*1024) + "MB"
                );

                long prevTotalJiffies = server.getCpuTime();
                //In ms
                long cpuSamplingIntervalTime = 100;
                Thread.sleep(cpuSamplingIntervalTime);

                System.out.println(
                        "Cpu: " +
                                calculateCpuUsage(prevTotalJiffies, server.getCpuTime(), cpuSamplingIntervalTime)/getThreadCount() + "\n"

                );
            }

            catch (IOException | InterruptedException e){
                e.printStackTrace();
            }
        }

    }

    public double calculateCpuUsage(
            long prevCpuNanos,
            long currCpuNanos,
            long elapsedMs
    ) {
        if (elapsedMs <= 0 || currCpuNanos < prevCpuNanos) {
            return 0.0;
        }

        double cpuSeconds =
                (currCpuNanos - prevCpuNanos) / 1_000_000_000.0;

        double elapsedSeconds =
                elapsedMs / 1000.0;

        return (cpuSeconds / elapsedSeconds) * 100.0;
    }

    public int getThreadCount()
            throws IOException {

        return Runtime.getRuntime().availableProcessors();
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

    public ServerInstance getInstance(
            String name
    ) throws ServerManagerException {

        String version;

        validateName(name);

        ServerInstance existing =
                instances.get(name);

        if (existing != null) {
            return existing;
        }

        Path directory =
                serverPath(name);

        try {
            version = getServerVersion(directory);
        } catch (IOException e) {
            throw new ServerManagerException(
                    "Directory '" +
                            directory +
                            "behaviour_packs" +
                            "/' not found"
            );
        }

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

        try {
            Files.list(SERVERS_DIR)
                    .filter(Files::isDirectory)
                    .forEach(path -> {

                        String name =
                                path.getFileName().toString();

                        String version =
                                null;
                        try {
                            version = getServerVersion(path);
                        } catch (IOException e) {
                            System.err.println("Directory '" + path + "behaviour_packs" + "/' not found");
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

    public static String getServerVersion(Path serverPath)
            throws IOException {

        Path packsDir = serverPath.resolve("behavior_packs");

        if (!Files.isDirectory(packsDir)) {
            return null;
        }

        try (var stream = Files.list(packsDir)) {
            return stream
                    .map(p -> p.getFileName().toString())
                    .filter(n -> n.startsWith("vanilla_"))
                    .map(n -> n.substring("vanilla_".length()))
                    .filter(n -> n.matches("\\d+(\\.\\d+)+"))
                    .max(Comparator.comparing(
                            n -> Arrays.stream(n.split("\\."))
                                    .mapToInt(Integer::parseInt)
                                    .boxed()
                                    .toList(),
                            (a, b) -> {
                                int size = Math.max(a.size(), b.size());

                                for (int i = 0; i < size; i++) {
                                    int av = i < a.size() ? a.get(i) : 0;
                                    int bv = i < b.size() ? b.get(i) : 0;

                                    if (av != bv) {
                                        return Integer.compare(av, bv);
                                    }
                                }

                                return 0;
                            }))
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
            Files.createDirectories(TEMPORARY_DIR.resolve("update_backups"));

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

    protected String getVariable(Path serverPath, String varName) throws IOException {
        for (String line : Files.readAllLines(serverPath.resolve("server.properties"))) {
            line = line.trim();

            if (line.isEmpty() || line.startsWith("#"))
                continue;

            int separator = line.indexOf('=');

            if (separator == -1)
                continue;

            String key = line.substring(0, separator).trim();

            if (key.equals(varName))
                return line.substring(separator + 1).trim();
        }

        return null;
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
