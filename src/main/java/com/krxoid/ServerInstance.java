package com.krxoid;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

public final class ServerInstance {

    private final String name;
    private final Path directory;
    private final Path executable;

    private Process process;
    private BufferedWriter writer;

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
        return process != null && process.isAlive();
    }

    public long getPid() {
        if (!isRunning()) {
            return -1;
        }

        return process.pid();
    }

    public void start() throws ServerManagerException {
        if (isRunning()) {
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

            process = builder.start();

            writer =
                    new BufferedWriter(
                            new OutputStreamWriter(
                                    process.getOutputStream(),
                                    StandardCharsets.UTF_8
                            )
                    );

            startOutputReader();

        } catch (IOException e) {
            process = null;
            writer = null;

            throw new ServerManagerException(
                    "Failed to start server '" + name + "'.",
                    e
            );
        }
    }

    public void stop() throws ServerManagerException {
        if (!isRunning()) {
            throw new ServerManagerException(
                    "Server '" + name + "' is not running."
            );
        }

        try {
            sendCommand("stop");

            if (!process.waitFor(
                    15,
                    TimeUnit.SECONDS
            )) {
                process.destroy();

                if (!process.waitFor(
                        5,
                        TimeUnit.SECONDS
                )) {
                    process.destroyForcibly();
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
            process = null;
            writer = null;
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

        if (!isRunning()) {
            throw new ServerManagerException(
                    "Server '" + name + "' is not running."
            );
        }

        if (command == null || command.isBlank()) {
            throw new ServerManagerException(
                    "Command cannot be empty."
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

    public void attachConsole()
            throws ServerManagerException {

        if (!isRunning()) {
            throw new ServerManagerException(
                    "Server '" + name + "' is not running."
            );
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
                    continue;
                }

                sendCommand(line);
            }

        } catch (IOException e) {
            throw new ServerManagerException(
                    "Console input failed.",
                    e
            );
        }
    }

    private void startOutputReader() {

        Thread thread =
                new Thread(
                        this::readOutput
                );

        thread.setName(
                "rock-core-server-" + name
        );

        thread.setDaemon(true);
        thread.start();
    }

    private void readOutput() {

        try {
            BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(
                                    process.getInputStream(),
                                    StandardCharsets.UTF_8
                            )
                    );

            String line;

            while ((line = reader.readLine()) != null) {
                System.out.println(
                        "[" + name + "] " + line
                );
            }

        } catch (IOException ignored) {
            /*
             * The process may close its output stream during shutdown.
             */
        } finally {
            process = null;
            writer = null;
        }
    }
}
