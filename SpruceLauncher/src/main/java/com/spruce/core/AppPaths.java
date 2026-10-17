package com.spruce.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class AppPaths {
    private AppPaths() {
    }

    public static Path dataDir() {
        Path dir = Paths.get(System.getProperty("user.home"), ".sprucelauncher");
        try {
            Files.createDirectories(dir);
        } catch (Exception ignored) {
        }
        return dir;
    }

    public static Path configFile() {
        return dataDir().resolve("config.json");
    }

    public static Path defaultClientsDir() {
        return Paths.get("D:\\SpruceLauncher", "merge-clean");
    }

    public static Path logsDir() {
        Path dir = dataDir().resolve("logs");
        try {
            Files.createDirectories(dir);
        } catch (Exception ignored) {
        }
        return dir;
    }

    public static Path defaultGameDir() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isBlank()) {
                return Paths.get(appData, ".minecraft");
            }
            return Paths.get(System.getProperty("user.home"), "AppData", "Roaming", ".minecraft");
        }
        if (os.contains("mac")) {
            return Paths.get(System.getProperty("user.home"), "Library", "Application Support", "minecraft");
        }
        return Paths.get(System.getProperty("user.home"), ".minecraft");
    }

    public static Path versionsDir(Path gameDir) {
        return gameDir.resolve("versions");
    }

    public static Path librariesDir(Path gameDir) {
        return gameDir.resolve("libraries");
    }

    public static Path assetsDir(Path gameDir) {
        return gameDir.resolve("assets");
    }
}
