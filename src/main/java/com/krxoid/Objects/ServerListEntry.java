package com.krxoid.Objects;

public record ServerListEntry(
        String name,
        boolean running,
        long pid,
        String version,
        long size
) {}

