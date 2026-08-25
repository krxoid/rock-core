package com.krxoid;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Scanner;
import java.util.stream.IntStream;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import static com.krxoid.ServerCommandHandler.printPrompt;
import static com.krxoid.ServerManager.ROOT;

public final class CommandDispatcher {

    private final ServerCommandHandler serverHandler;

    public static String VERSION = "1.2.0";

    public static Path VERSIONS_FILE = ROOT.resolve("versions.json");

    private static final URI BDS_VERSIONS_URI = URI.create(
            "https://raw.githubusercontent.com/" + "Bedrock-OSS/BDS-Versions/main/versions.json" );


    public CommandDispatcher() {
        this.serverHandler =
                new ServerCommandHandler();
    }

    public void startShell() {

        Scanner scanner = new Scanner(System.in);

        System.out.println("Rock Core");
        System.out.println("Type 'help' for a list of commands.");
        System.out.println("Type 'exit' or 'quit' to leave.");
        System.out.println();

        printPrompt();

        while (scanner.hasNextLine()) {

            String line = scanner.nextLine().trim();

            if (line.isEmpty()) {
                printPrompt();
                continue;
            }

            if (line.equalsIgnoreCase("exit") ||
                    line.equalsIgnoreCase("quit")) {
                break;
            }

            if (line.equalsIgnoreCase("help")) {
                printHelp();
                System.out.print("rock > ");
                System.out.flush();
                continue;
            }

            dispatch(parseArguments(line));
        }
    }

    public int dispatch(String[] args) {

        if (args == null || args.length == 0) {
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

        try {

            switch (command) {

                case "server":
                    return serverHandler.handle(
                            commandArgs
                    );

                case "help":
                    printHelp();
                    printPrompt();
                    return 0;

                case "version":
                    printVersion();
                    printPrompt();
                    return 0;

                case "cls", "clear":
                    System.out.print("\033[2J\033[3J\033[H");
                    printPrompt();
                    return 0;

                case "versions":

                    try {

                        if (commandArgs.length == 0) throw new IllegalArgumentException("Range not specified");

                        System.out.println(
                                Arrays.toString(
                                        getVersions(
                                                Integer.parseInt(
                                                        commandArgs[0]
                                                )
                                        )
                                )
                        );
                        printPrompt();
                        return 0;
                    }

                    catch (IllegalArgumentException e) {

                        System.err.println("Range not specified");
                        printPrompt();
                        return -1;
                    }

                case "latest":

                    System.out.println(getLatestVersion());
                    printPrompt();
                    return 0;

                default:
                    System.err.println(
                            "Unknown command: " +
                                    args[0]
                    );

                    System.err.println(
                            "Type 'help' for help."
                    );

                    printPrompt();

                    return 1;
            }

        } catch (Exception e) {

            System.err.println(
                    "Error: " +
                            e.getMessage()
            );

            return 1;
        }
    }

    private void printHelp() {

        System.out.println("""
                
                Rock Core - Minecraft Bedrock Server Manager
                
                Commands:
                  server list
                  server create <name> <version>
                  server start <name>
                  server stop <name>
                  server restart <name>
                  server status <name>
                  server players <name>
                  server console <name>
                  server exec <name> <command>
                  server backup <name>
                  server delete <name>
                  server import world <server> <path>
                  server config <name> <variable> <value>
                
                Global:
                  help
                  version
                  exit
                  quit
                  cls
                  versions <range>
                """);
    }

    protected void fetchVersions()
            throws IOException, InterruptedException {

        Files.createDirectories(VERSIONS_FILE.getParent());

        HttpClient client = HttpClient.newHttpClient();

        IOException lastException = null;

        for (int attempt = 1; attempt <= 5; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder(BDS_VERSIONS_URI)
                        .GET()
                        .build();

                HttpResponse<Path> response = client.send(
                        request,
                        HttpResponse.BodyHandlers.ofFile(VERSIONS_FILE)
                );

                if (response.statusCode() == 200) {
                    return;
                }

                Files.deleteIfExists(VERSIONS_FILE);

                lastException = new IOException(
                        "HTTP " + response.statusCode()
                );

            } catch (IOException e) {
                Files.deleteIfExists(VERSIONS_FILE);
                lastException = e;
            }

            if (attempt < 5) {
                Thread.sleep(1000);
            }
        }

        throw new IOException(
                "Failed to fetch BDS versions after 5 attempts",
                lastException
        );
    }

    private String[] getVersions(int range)
            throws IOException {

        if (range <= 0) {
            throw new IllegalArgumentException(
                    "Range must be greater than zero."
            );
        }

        String json =
                Files.readString(
                        VERSIONS_FILE,
                        StandardCharsets.UTF_8
                );

        JsonObject root =
                JsonParser.parseString(json)
                        .getAsJsonObject();

        JsonObject linux =
                root.getAsJsonObject("linux");

        JsonArray versions =
                linux.getAsJsonArray("versions");

        int count =
                Math.min(range, versions.size());

        String[] result =
                new String[count];

        int start =
                versions.size() - count;

        for (int i = 0; i < count; i++) {
            result[i] =
                    versions
                            .get(start + i)
                            .getAsString();
        }

        return IntStream.range(0, result.length)
                .mapToObj(i -> result[result.length - 1 - i])
                .toArray(String[]::new);

    }

    public String getLatestVersion() throws IOException{

        return Arrays.toString(getVersions(1));
    }


    private void printVersion(){

        System.out.println("v" + VERSION);

    }


    private String[] parseArguments(
            String line
    ) {

        return line
                .trim()
                .split(
                        "\\s+(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)"
                );
    }
}
