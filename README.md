# Rock Core

A command-line management tool for Minecraft Bedrock Dedicated Server instances, written in Java.

Rock Core provides a unified interface for creating, configuring, running, and managing multiple Bedrock Dedicated Server instances. The project is designed around a CLI-first architecture with explicit command dispatch, isolated server state, and native Linux packaging.

## Features

* Interactive command shell
* Command-based server management
* Multiple isolated server instances
* Version-specific BDS installation
* Server lifecycle management
* Server status and process tracking
* Server console attachment
* Remote command execution through the server console
* Player listing
* Server backups
* World importing
* Server deletion
* Automatic BDS acquisition from the official Minecraft distribution endpoint
* Arch Linux packaging through the AUR
* Debian package distribution

## Architecture

The application is divided into several responsibilities:

```text
                    ┌─────────────────────┐
                    │        Main         │
                    └──────────┬──────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │ CommandDispatcher   │
                    └──────────┬──────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │ ServerCommandHandler│
                    └──────────┬──────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │   ServerManager     │
                    └──────────┬──────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │ Server filesystem   │
                    │ and processes       │
                    └─────────────────────┘
```

### `Main`

Application entry point.

Responsible for:

* initializing the command dispatcher
* handling global commands such as `--help` and `--version`
* entering the interactive shell when no arguments are provided

### `CommandDispatcher`

Responsible for command routing and shell interaction.

It separates command parsing from the implementation of individual operations and supports both interactive and non-interactive invocation.

### `ServerCommandHandler`

Contains the command-level implementation for server operations.

Examples include:

```text
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
```

External operations such as acquiring and extracting a BDS distribution are handled at this layer rather than by the lower-level server state manager.

### `ServerManager`

Owns server state and filesystem operations.

A server is represented as an isolated directory containing its executable, worlds, backups, and logs.

```text
servers/
└── <server>/
    ├── bedrock_server
    ├── worlds/
    ├── backups/
    └── logs/
```

The manager is responsible for maintaining this structure and managing the lifecycle of server processes.

## Command Interface

Rock Core can be used interactively:

```text
rock>
```

or invoked directly:

```bash
rock server list
rock server status survival
```

This allows the same command implementation to be used from both a human-operated shell and external scripts.

## BDS Version Management

Server creation accepts an explicit Bedrock Dedicated Server version:

```text
server create survival 1.26.43.1
```

The corresponding BDS archive is retrieved from the official Minecraft distribution endpoint and extracted into the newly created server directory.

This keeps the server's runtime independent from the version of BDS installed for other instances.

## World Importing

World importing is exposed through a typed import command:

```text
server import world <server> <path>
```

The explicit import type is intended to allow additional resource types to be introduced without changing the general command model.

## Build

Rock Core uses Gradle.

```bash
./gradlew build
```

To build the application JAR:

```bash
./gradlew jar
```

The generated artifact is placed under:

```text
build/libs/
```

## Packaging

Rock Core is distributed through native Linux package formats.

Current targets:

* Arch Linux / CachyOS
* Debian / Ubuntu
* Fedora / RHEL

Packaging definitions are maintained separately from the main source repository.

## Project Structure

```text
rock-core/
├── src/
│   └── main/
│       └── java/
│           └── com/
│               └── krxoid/
├── build.gradle
├── gradlew
├── gradlew.bat
├── README.md
└── LICENSE
```

The repository contains the application source and build configuration. Distribution-specific packaging is maintained independently.


## Requirements

* Linux
* Java runtime
* Minecraft Bedrock Dedicated Server

## Development

The project intentionally keeps command parsing, command execution, server management, and external distribution handling separated.

This makes individual components replaceable without coupling the command interface directly to process or filesystem implementation details.

Areas for future development include:

* additional import types
* improved server configuration management
* stronger process supervision
* richer command parsing
* additional distribution targets
* automated release and packaging pipelines

## License

Rock Core is licensed under the GNU General Public License v3.0 or later.

See [`LICENSE`](LICENSE) for the complete license text.
