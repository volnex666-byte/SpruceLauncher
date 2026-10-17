package com.spruce.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Config {
    public static final int MIN_MEMORY = 512;
    public static final int MAX_MEMORY = 65536;

    public String theme = "graphite";
    public String animationIntensity = "normal";
    public boolean particles = true;
    public boolean backgroundGlow = true;
    public int memoryMb = 4096;
    public int minMemoryMb = 1024;
    public String username = "Player";
    public String gameDir = "";
    public String javaPath = "";
    public String lastVersion = "";
    public List<String> selectedClients = new ArrayList<>();
    public Map<String, String> clientPaths = new HashMap<>();
    public String clientsDir = "";
    public String darkTheme = "graphite";
    public long lastLaunchAt = 0;
    public String lastLaunchVersion = "";

    public static final String VERSION = "2.0.0";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static Config load() {
        try {
            Path file = AppPaths.configFile();
            if (Files.isRegularFile(file)) {
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    Config config = GSON.fromJson(reader, Config.class);
                    if (config != null) {
                        config.normalize();
                        return config;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        Config config = new Config();
        config.normalize();
        return config;
    }

    public void save() {
        try {
            try (Writer writer = Files.newBufferedWriter(AppPaths.configFile(), StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (Exception ignored) {
        }
    }

    public void normalize() {
        if (selectedClients == null) {
            selectedClients = new ArrayList<>();
        }
        if (clientPaths == null) {
            clientPaths = new HashMap<>();
        }
        if (clientsDir == null || clientsDir.isBlank()) {
            clientsDir = AppPaths.defaultClientsDir().toString();
        }
        if (memoryMb < MIN_MEMORY) {
            memoryMb = MIN_MEMORY;
        }
        if (minMemoryMb < MIN_MEMORY) {
            minMemoryMb = MIN_MEMORY;
        }
        if (minMemoryMb > memoryMb) {
            minMemoryMb = memoryMb;
        }
        if (username == null || username.isBlank()) {
            username = "Player";
        }
        if (darkTheme == null || darkTheme.isBlank() || "light".equals(darkTheme)) {
            darkTheme = "graphite";
        }
        if (lastLaunchVersion == null) {
            lastLaunchVersion = "";
        }
    }
}
