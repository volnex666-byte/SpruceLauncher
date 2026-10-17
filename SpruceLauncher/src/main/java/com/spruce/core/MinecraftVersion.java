package com.spruce.core;

import java.nio.file.Path;

public class MinecraftVersion {
    public final String id;
    public final Path directory;
    public final Path jsonPath;
    public final Path jarPath;
    public final String type;

    public MinecraftVersion(String id, Path directory, Path jsonPath, Path jarPath, String type) {
        this.id = id;
        this.directory = directory;
        this.jsonPath = jsonPath;
        this.jarPath = jarPath;
        this.type = type;
    }

    @Override
    public String toString() {
        return id;
    }
}
