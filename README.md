# Rock Core

A command-line management tool for Minecraft Bedrock Dedicated Server instances, written in Java.

Rock Core provides a unified interface for creating, configuring, running, updating, and managing multiple Bedrock Dedicated Server instances. The project is designed around a CLI-first architecture, explicit command dispatch, isolated server state, and native Linux packaging.

A graphical interface is currently in development using Qt.

## Features

* Interactive command shell
* Command-based server management
* Multiple isolated server instances
* Version-specific BDS installation
* Automatic available-version fetching
* Automatic BDS acquisition from the official Minecraft distribution endpoint
* Server lifecycle management
* Server status and process tracking
* Server console attachment
* Remote command execution through the server console
* Player listing
* Server backups
* Backup restoration
* World importing
* Server deletion
* Server updating with world and configuration preservation
* Automatic update rollback on failure
* Filesystem-based server state
* Linux package support
* File based logging

## Philosophy

Rock Core is built around a simple principle:

> **The filesystem is the source of truth.**

Rock Core does not maintain a separate database or metadata system to keep track of server state. A server's files, directories, installed BDS files, worlds, and backups are the state of the server.

This follows the Unix philosophy of keeping mechanisms simple, composable, and predictable.

If an operation fails, Rock Core should report the failure rather than silently inventing or persisting state to compensate for it.

Version information is derived from the installed BDS files and Rock Core's version data rather than being stored in an additional per-server metadata file. Backups are ordinary filesystem data, and server configuration remains in the files used by the Bedrock server itself.

The goal is to make Rock Core manage the server without creating another system that the server depends on.

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
server list servers
server list backups
server create <name> <version>
server start <name>
server stop <name>
server restart <name>
server status <name>
server players <name>
server console <name>
server exec <name> <command>
server backup <name>
server restore <name> <world> <timestamp>
server delete <server|backup|logs> <name> [count]
server import world <server> <path>
server config <get|set> <name> <variable> [value]
server update <name> <version>
server restore <name> <world-name>
server rename <name> <new-name>
server logs <name> <count>
```

External operations such as acquiring, extracting, and updating BDS distributions are handled at this layer rather than by the lower-level server state manager.

### `ServerManager`

Owns server state, filesystem operations, and server instances.

A server is represented by an isolated directory containing its BDS installation, worlds, configuration, and other runtime data.

```text
servers/
└── <server>/
    ├── bedrock_server
    ├── worlds/
    ├── behavior_packs/
    ├── resource_packs/
    ├── server.properties
    └── ...
```

The manager is responsible for maintaining the server filesystem and managing server processes.

## Command Interface

Rock Core can be used interactively:

```text
rock >
```

or invoked directly:

```bash
rock server list servers
rock server status survival
rock server start survival
```

The same command implementation is used for both interactive shell usage and non-interactive invocation, allowing Rock Core commands to be used from scripts and other tooling.

## BDS Version Management

Server creation accepts an explicit Bedrock Dedicated Server version:

```text
server create survival 1.26.51.1
```

Updates can target an explicit version or the latest available release:

```text
server update survival 1.26.52.3
server update survival latest
```

Rock Core maintains available Linux BDS versions in its version data and automatically resolves `latest` to the newest available release.

Installed server versions are determined from the actual BDS installation. Rock Core identifies the installed `vanilla_x.x.x` behavior pack and maps that game version to the corresponding full BDS version from its version data.

For example:

```text
vanilla_1.26.51
       │
       ▼
    1.26.51
       │
       ▼
versions.json
       │
       ▼
   1.26.51.1
```

This avoids maintaining an additional per-server version metadata file.

The corresponding BDS archive is retrieved from the official Minecraft distribution endpoint and extracted into the server's isolated directory.

## Server Updates

Updates are designed to preserve the parts of a server that belong to the user while replacing the BDS installation itself.

The update process:

1. Determines the currently installed BDS version.
2. Resolves the requested target version.
3. Creates a temporary update backup.
4. Replaces the existing BDS installation.
5. Installs the requested version.
6. Restores server worlds and selected configuration.
7. Removes the temporary update backup after a successful update.

If the update fails, Rock Core uses the temporary backup to restore the previous server state.

This keeps server data independent from the BDS distribution itself.

## Backups

Rock Core provides filesystem-based world backups.

Backups are created only when the server is stopped to avoid copying potentially inconsistent live LevelDB data.

Backups use a single timestamp across all worlds:

```text
backups/
└── survival/
    ├── world/
    │   ├── 20260930-100000/
    │   └── 20260930-101000/
    └── nether/
        ├── 20260930-100000/
        └── 20260930-101000/
```

A backup operation therefore represents one point in time across the server's worlds.

Backups can be restored individually:

```text
server restore survival world 20260930-101000
```

Older complete backups can also be removed:

```text
server delete backup survival 2
```

This removes the oldest complete backup timestamps rather than treating each world backup as an unrelated backup.

Restoration creates a temporary safety backup before replacing the target world.

## World Importing

World importing is exposed through a typed import command:

```text
server import world survival /path/to/world
```

The explicit import type is intended to allow additional resource types to be introduced without changing the general command model.

## Server Lifecycle

Rock Core manages the lifecycle of individual BDS processes:

```text
server start <name>
server stop <name>
server restart <name>
server status <name>
server console <name>
```

A running server can be attached through its console or controlled remotely through:

```text
server exec <name> <command>
```

Process information such as status, CPU usage, memory usage, and process state can be inspected through the server management interface.

## Storage

Rock Core keeps its persistent data under:

```text
~/.local/share/rock-core/
```

The layout is intentionally filesystem-oriented:

```text
rock-core/
├── servers/
│   └── <server>/
├── backups/
│   └── <server>/
├── versions.json
└── history
```

No database is required.

Temporary update data is kept outside the persistent server state and is removed after successful operations.

## Build

Rock Core uses Gradle.

Build the project with:

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

## Installation

Rock Core is distributed through its PKGBUILD and `install.sh` script.

### Arch Linux

Arch-based distributions are supported through the PKGBUILD and native `pacman` packaging.

### Other Linux distributions

Rock Core also provides a generic installation script for most glibc-based Linux desktops:

```bash
curl -fsSL https://raw.githubusercontent.com/krxoid/rock-core/master/install.sh | sudo bash
```

Native Debian and Fedora packages are currently not maintained.

## Updating Rock Core

Rock Core can update itself through its built-in update functionality:

```bash
rock update latest
```

A specific version can also be selected when supported by the available release data.

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
├── LICENSE
└── PKGBUILD
```

The repository contains the application source, Gradle build configuration, and Linux packaging files.

## Requirements

* Linux
* Java runtime
* Minecraft Bedrock Dedicated Server

Rock Core is designed primarily for Linux server and desktop environments.

## Development

Rock Core intentionally keeps command parsing, command execution, server management, process handling, and external distribution handling separated.

This allows individual components to evolve without coupling the command interface directly to process or filesystem implementation details.

Current and planned development areas include:

* Qt graphical interface
* Additional import types
* Improved server configuration management
* Stronger process supervision
* Richer command parsing
* Additional Linux distribution support
* Automated release and packaging pipelines

## License

Rock Core is licensed under the GNU General Public License v3.0 or later.

See [`LICENSE`](LICENSE) for the complete license text.
