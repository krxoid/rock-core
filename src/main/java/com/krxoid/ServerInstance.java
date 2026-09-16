package com.krxoid;

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.UserInterruptException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

public final class ServerInstance {

    private final String name;
    private final String version;
    private final Path directory;
    private final Path executable;
    private final LineReader lineReader;

    private final Object lifecycleLock =
            new Object();

    private Process process;
    private BufferedWriter writer;
    private boolean attached;

    public ServerInstance(
            String name,
            String version,
            Path directory,
            LineReader lineReader
    ) {
        this.name = name;
        this.version = version;
        this.directory = directory;
        this.executable =
                directory.resolve("bedrock_server");
        this.lineReader = lineReader;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public Path getDirectory() {
        return directory;
    }

    public boolean exists() {
        return Files.isDirectory(directory);
    }

    public boolean isRunning() {
        synchronized (lifecycleLock) {
            return process != null &&
                    process.isAlive();
        }
    }

    public boolean isAttached() {
        synchronized (lifecycleLock) {
            return attached;
        }
    }

    public long getPid() {

        synchronized (lifecycleLock) {

            if (process == null ||
                    !process.isAlive()) {
                return -1;
            }

            return process.pid();
        }
    }

    public void start()
            throws ServerManagerException {

        synchronized (lifecycleLock) {

            if (process != null &&
                    process.isAlive()) {

                throw new ServerManagerException(
                        "Server '" +
                                name +
                                "' is already running."
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
                                executable
                                        .toAbsolutePath()
                                        .toString()
                        );

                builder.directory(
                        directory.toFile()
                );

                builder.redirectErrorStream(true);

                Process newProcess =
                        builder.start();

                BufferedWriter newWriter =
                        new BufferedWriter(
                                new OutputStreamWriter(
                                        newProcess.getOutputStream(),
                                        StandardCharsets.UTF_8
                                )
                        );

                process = newProcess;
                writer = newWriter;
                attached = false;

                startOutputReader(newProcess);

            } catch (IOException e) {

                process = null;
                writer = null;
                attached = false;

                throw new ServerManagerException(
                        "Failed to start server '" +
                                name +
                                "'.",
                        e
                );
            }
        }
    }

    public void stop()
            throws ServerManagerException {

        final Process currentProcess;

        synchronized (lifecycleLock) {

            currentProcess = process;

            if (currentProcess == null ||
                    !currentProcess.isAlive()) {

                throw new ServerManagerException(
                        "Server '" +
                                name +
                                "' is not running."
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

                    currentProcess.waitFor(
                            5,
                            TimeUnit.SECONDS
                    );
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

            synchronized (lifecycleLock) {

                if (process == currentProcess) {

                    process = null;
                    writer = null;
                    attached = false;
                }
            }
        }
    }

    public void restart()
            throws ServerManagerException {

        if (isRunning()) {
            stop();
        }

        start();
    }

    public void sendCommand(
            String command
    ) throws ServerManagerException {

        if (command == null ||
                command.isBlank()) {

            throw new ServerManagerException(
                    "Command cannot be empty."
            );
        }

        synchronized (lifecycleLock) {

            if (process == null ||
                    !process.isAlive()) {

                throw new ServerManagerException(
                        "Server '" +
                                name +
                                "' is not running."
                );
            }

            if (writer == null) {

                throw new ServerManagerException(
                        "Server '" +
                                name +
                                "' input stream is unavailable."
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
                        "Server '" +
                                name +
                                "' is not running."
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

        lineReader.printAbove(
                "Attached to '" +
                        name +
                        "'."
        );

        lineReader.printAbove(
                "Type commands directly."
        );

        lineReader.printAbove(
                "Press Ctrl+D to detach."
        );

        try {

            while (isRunning()) {

                final String line;

                try {

                    line =
                            lineReader.readLine(
                                    "[" +
                                            name +
                                            "] rock > "
                            );

                } catch (UserInterruptException e) {

                    /*
                     * Ctrl+C only cancels the current
                     * command line.
                     */
                    lineReader.printAbove("^C");
                    continue;

                } catch (EndOfFileException e) {

                    /*
                     * Ctrl+D detaches.
                     */
                    break;
                }

                if (line == null) {
                    break;
                }

                if (line.isBlank()) {
                    continue;
                }

                if (line.equalsIgnoreCase("stop")) {

                    lineReader.printAbove(
                            "To stop the server, detach from the " +
                                    "console and type 'server stop " +
                                    name +
                                    "'"
                    );

                    continue;
                }

                sendCommand(line);
            }

        } finally {

            synchronized (lifecycleLock) {
                attached = false;
            }

            lineReader.printAbove(
                    "Detached from '" +
                            name +
                            "'."
            );
        }
    }

    public long getCpuTime()
            throws ServerManagerException {

        final Process currentProcess;

        synchronized (lifecycleLock) {

            currentProcess = process;

            if (currentProcess == null ||
                    !currentProcess.isAlive()) {

                throw new ServerManagerException(
                        "Server '" +
                                name +
                                "' is not running."
                );
            }
        }

        return currentProcess.info()
                .totalCpuDuration()
                .map(Duration::toNanos)
                .orElseThrow(
                        () ->
                                new ServerManagerException(
                                        "Unable to read CPU statistics " +
                                                "for server '" +
                                                name +
                                                "'."
                                )
                );
    }

    public long getRamUsage()
            throws ServerManagerException {

        final long pid = getPid();

        if (pid == -1) {

            throw new ServerManagerException(
                    "Server '" +
                            name +
                            "' is not running."
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
                        line.trim()
                                .split("\\s+");

                if (parts.length < 2) {
                    break;
                }

                long kb =
                        Long.parseLong(parts[1]);

                return kb * 1024L;
            }

        } catch (
                IOException |
                NumberFormatException e
        ) {

            throw new ServerManagerException(
                    "Unable to read RAM usage for server '" +
                            name +
                            "'.",
                    e
            );
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
                        () ->
                                readOutput(
                                        serverProcess
                                )
                );

        thread.setName(
                "rock-core-server-" +
                        name
        );

        thread.setDaemon(true);
        thread.start();
    }

    private void readOutput(
            Process serverProcess
    ) {

        try (
                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        serverProcess.getInputStream(),
                                        StandardCharsets.UTF_8
                                )
                        )
        ) {

            String line;

            while ((line = reader.readLine()) != null) {

                /*
                 * ALWAYS go through JLine.
                 *
                 * This is important even when we're NOT
                 * attached to the server console, because
                 * the main `rock >` prompt may currently
                 * be waiting for input.
                 */
                lineReader.printAbove(
                        "[" +
                                name +
                                "] " +
                                line
                );
            }

        } catch (IOException ignored) {

            /*
             * Normal during process shutdown.
             */

        } finally {

            synchronized (lifecycleLock) {

                if (process == serverProcess) {

                    process = null;
                    writer = null;
                    attached = false;
                }
            }
        }
    }
}