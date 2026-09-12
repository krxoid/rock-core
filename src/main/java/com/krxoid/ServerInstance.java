package com.krxoid;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static com.krxoid.ServerCommandHandler.printPrompt;

public final class ServerInstance {

    private final String name;
    private final Path directory;
    private final Path executable;

    /*
     * Access to process/writer is guarded by lifecycleLock.
     */
    private final Object lifecycleLock = new Object();

    private Process process;
    private BufferedWriter writer;

    private volatile boolean attached;

    public ServerInstance(
            String name,
            Path directory
    ) {
        this.name = name;
        this.directory = directory;
        this.executable = directory.resolve("bedrock_server");
    }

    public String getName() {
        return name;
    }

    public Path getDirectory() {
        return directory;
    }

    public boolean exists() {
        return Files.isDirectory(directory);
    }

    public boolean isRunning() {
        synchronized (lifecycleLock) {
            return process != null && process.isAlive();
        }
    }

    public long getPid() {
        synchronized (lifecycleLock) {
            if (process == null || !process.isAlive()) {
                return -1;
            }

            return process.pid();
        }
    }

    public void start() throws ServerManagerException {

        synchronized (lifecycleLock) {

            if (process != null && process.isAlive()) {
                throw new ServerManagerException(
                        "Server '" + name + "' is already running."
                );
            }

            if (!Files.exists(executable)) {
                throw new ServerManagerException(
                        "Minecraft Bedrock server executable not found:\n" +
                                executable
                );
            }

            if (!Files.isExecutable(executable)) {
                throw new ServerManagerException(
                        "Minecraft Bedrock server executable is not executable:\n" +
                                executable
                );
            }

            try {

                ProcessBuilder builder =
                        new ProcessBuilder(
                                executable.toAbsolutePath().toString()
                        );

                builder.directory(directory.toFile());
                builder.redirectErrorStream(true);

                Process newProcess = builder.start();

                BufferedWriter newWriter =
                        new BufferedWriter(
                                new OutputStreamWriter(
                                        newProcess.getOutputStream(),
                                        StandardCharsets.UTF_8
                                )
                        );

                process = newProcess;
                writer = newWriter;

                startOutputReader(newProcess);

            } catch (IOException e) {

                process = null;
                writer = null;

                throw new ServerManagerException(
                        "Failed to start server '" + name + "'.",
                        e
                );
            }
        }
    }

    public void stop() throws ServerManagerException {

        final Process currentProcess;

        synchronized (lifecycleLock) {

            currentProcess = process;

            if (currentProcess == null ||
                    !currentProcess.isAlive()) {

                throw new ServerManagerException(
                        "Server '" + name + "' is not running."
                );
            }
        }

        try {

            sendCommand("stop");

            if (!currentProcess.waitFor(
                    15,
                    TimeUnit.SECONDS
            )) {

                currentProcess.destroy();

                if (!currentProcess.waitFor(
                        5,
                        TimeUnit.SECONDS
                )) {
                    currentProcess.destroyForcibly();
                }
            }

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            throw new ServerManagerException(
                    "Interrupted while stopping server '" +
                            name +
                            "'.",
                    e
            );

        } finally {

            /*
             * Do not blindly null the process here.
             *
             * The output reader owns cleanup of the process state.
             */
            synchronized (lifecycleLock) {

                if (process == currentProcess) {
                    process = null;
                    writer = null;
                }
            }
        }
    }

    public void restart() throws ServerManagerException {

        if (isRunning()) {
            stop();
        }

        start();
    }

    public void sendCommand(
            String command
    ) throws ServerManagerException {

        if (command == null || command.isBlank()) {
            throw new ServerManagerException(
                    "Command cannot be empty."
            );
        }

        synchronized (lifecycleLock) {

            if (process == null ||
                    !process.isAlive()) {

                throw new ServerManagerException(
                        "Server '" + name + "' is not running."
                );
            }

            if (writer == null) {
                throw new ServerManagerException(
                        "Server '" + name + "' input stream is unavailable."
                );
            }

            try {

                writer.write(command);
                writer.newLine();
                writer.flush();

            } catch (IOException e) {

                throw new ServerManagerException(
                        "Failed to send command to server '" +
                                name +
                                "'.",
                        e
                );
            }
        }
    }

    public void attachConsole()
            throws ServerManagerException {

        synchronized (lifecycleLock) {

            if (process == null ||
                    !process.isAlive()) {

                throw new ServerManagerException(
                        "Server '" + name + "' is not running."
                );
            }

            if (attached) {
                throw new ServerManagerException(
                        "Already attached to server '" +
                                name +
                                "'."
                );
            }

            attached = true;
        }

        System.out.println(
                "Attached to '" + name + "'."
        );

        System.out.println(
                "Type commands directly."
        );

        System.out.println(
                "Press Ctrl+D to detach."
        );

        System.out.print(
                "[" + name + "] "
        );

        printPrompt();

        try {

            BufferedReader input =
                    new BufferedReader(
                            new InputStreamReader(
                                    System.in,
                                    StandardCharsets.UTF_8
                            )
                    );

            String line;

            while (isRunning() &&
                    (line = input.readLine()) != null) {

                if (line.isBlank()) {

                    System.out.print(
                            "[" + name + "] "
                    );

                    printPrompt();

                    continue;
                }

                sendCommand(line);
            }

        } catch (IOException e) {

            throw new ServerManagerException(
                    "Console input failed.",
                    e
            );

        } finally {

            attached = false;

            System.out.println();

            System.out.println(
                    "Detached from '" + name + "'."
            );
        }
    }

    /**
     * Returns the total CPU time consumed by the server process
     * in nanoseconds.
     *
     * This is NOT a percentage.
     */
    public long getCpuTime()
            throws ServerManagerException, IOException {

        final long pid = getPid();

        if (pid == -1) {
            throw new ServerManagerException(
                    "Server '" + name + "' is not running."
            );
        }

        Path stat =
                Path.of(
                        "/proc",
                        Long.toString(pid),
                        "stat"
                );

        try (BufferedReader reader =
                     Files.newBufferedReader(
                             stat,
                             StandardCharsets.UTF_8
                     )) {

            String line = reader.readLine();

            if (line == null || line.isBlank()) {
                throw new ServerManagerException(
                        "Unable to read CPU statistics for server '" +
                                name +
                                "'."
                );
            }

            /*
             * /proc/[pid]/stat:
             *
             * field 14 = utime
             * field 15 = stime
             *
             * The process name is enclosed in parentheses,
             * so find the final ')' first.
             */
            int lastParen =
                    line.lastIndexOf(')');

            if (lastParen == -1 ||
                    lastParen + 2 >= line.length()) {

                throw new ServerManagerException(
                        "Invalid /proc stat data for server '" +
                                name +
                                "'."
                );
            }

            String[] fields =
                    line.substring(lastParen + 2)
                            .split("\\s+");

            /*
             * After removing fields 1 and 2:
             *
             * fields[11] = original field 14 (utime)
             * fields[12] = original field 15 (stime)
             */
            long utime =
                    Long.parseLong(fields[11]);

            long stime =
                    Long.parseLong(fields[12]);

            return utime + stime;
        }
    }

    /**
     * Returns resident memory usage in bytes.
     */
    public long getRamUsage()
            throws ServerManagerException, IOException {

        final long pid = getPid();

        if (pid == -1) {
            throw new ServerManagerException(
                    "Server '" + name + "' is not running."
            );
        }

        Path status =
                Path.of(
                        "/proc",
                        Long.toString(pid),
                        "status"
                );

        try (BufferedReader reader =
                     Files.newBufferedReader(
                             status,
                             StandardCharsets.UTF_8
                     )) {

            String line;

            while ((line = reader.readLine()) != null) {

                if (!line.startsWith("VmRSS:")) {
                    continue;
                }

                String[] parts =
                        line.trim().split("\\s+");

                if (parts.length < 2) {
                    break;
                }

                long kb =
                        Long.parseLong(parts[1]);

                return kb * 1024L;
            }
        }

        throw new ServerManagerException(
                "Unable to read RAM usage for server '" +
                        name +
                        "'."
        );
    }

    private void startOutputReader(
            Process serverProcess
    ) {

        Thread thread =
                new Thread(
                        () -> readOutput(serverProcess)
                );

        thread.setName(
                "rock-core-server-" + name
        );

        thread.setDaemon(true);
        thread.start();
    }

    private void readOutput(
            Process serverProcess
    ) {

        try (BufferedReader reader =
                     new BufferedReader(
                             new InputStreamReader(
                                     serverProcess.getInputStream(),
                                     StandardCharsets.UTF_8
                             )
                     )) {

            String line;

            while ((line = reader.readLine()) != null) {

                if (attached) {

                    System.out.print(
                            "\r\033[2K"
                    );

                    System.out.print(
                            "[" + name + "] " + line
                    );

                } else {

                    System.out.print(
                            "\r\033[2K"
                    );

                    System.out.println(
                            "[" + name + "] " + line
                    );

                    printPrompt();
                }
            }

        } catch (IOException ignored) {

            /*
             * The process may close its output stream
             * during normal shutdown.
             */

        } finally {

            /*
             * Only clean up if this is still the same
             * process that this reader belongs to.
             */
            synchronized (lifecycleLock) {

                if (process == serverProcess) {
                    process = null;
                    writer = null;
                }
            }
        }
    }
}
