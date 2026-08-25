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

    private Process process;
    private BufferedWriter writer;

    private boolean isAttached;

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

        System.out.print("[" + name + "] "); printPrompt();

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
                    System.out.print("[" + name + "] "); printPrompt();
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

    public long getCpuUsage()
            throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader("/proc/" + process.pid() + "/stat"));

        String line = reader.readLine();
        if (line != null) {
            // The command name (field 2) is enclosed in parentheses and may contain spaces.
            // We must find the last ')' to correctly index the subsequent fields.
            int lastParenIndex = line.lastIndexOf(')');
            if (lastParenIndex != -1 && lastParenIndex + 2 < line.length()) {
                String[] stats = line.substring(lastParenIndex + 2).split("\\s+");

                long utime = Long.parseLong(stats[11]);
                long stime = Long.parseLong(stats[12]);

                return utime + stime;
            }
        }

        throw new NullPointerException("Line cannot be empty");
    }

    public long getRamUsage()
            throws ServerManagerException, IOException {
        BufferedReader reader = new BufferedReader(new FileReader("/proc/" + process.pid() + "/status"));
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("VmRSS:")) {
                // Format: "VmRSS:     12345 kB"
                String[] parts = line.split("\\s+");
                if (parts.length >= 2) {
                    long kb = Long.parseLong(parts[1]);
                    return kb * 1024L; // Convert kB to bytes
                }
            }
        }

        throw new ServerManagerException("Line cannot be empty");
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

                if(isAttached) {
                    System.out.print("\r\033[2K");

                    System.out.println(
                            "[" + name + "] " + line
                    );

                    printPrompt();
                }

                else {
                    System.out.print("\r\033[2K");

                    System.out.println(
                            "[" + name + "] " + line
                    );

                    System.out.print("[" + name + "] "); printPrompt();
                }
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
