package com.spruce.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ClientFolderScanner {
    private static final String[] MARKERS = {
            "baritone", "chams", "autorespawn", "freelook", "espbox", "shaderesp",
            "targetesp", "target-esp", "neurofly", "glowesp", "seeinvisible"
    };

    public static final class Skipped {
        public final String folder;
        public final List<String> markers;

        Skipped(String folder, List<String> markers) {
            this.folder = folder;
            this.markers = markers;
        }
    }

    public static final class Result {
        public final List<ClientProfile> profiles = new ArrayList<>();
        public final List<Skipped> skipped = new ArrayList<>();
    }

    private ClientFolderScanner() {
    }

    public static Result scan(Path root) {
        Result result = new Result();
        if (root == null || !Files.isDirectory(root)) {
            return result;
        }
        List<Path> folders = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            for (Path path : stream) {
                if (Files.isDirectory(path)) {
                    folders.add(path);
                }
            }
        } catch (IOException ignored) {
        }
        folders.sort(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)));
        for (Path folder : folders) {
            scanFolder(folder, result);
        }
        return result;
    }

    private static void scanFolder(Path folder, Result result) {
        ClientProfile profile = parse(folder, result.profiles.size());
        if (profile == null) {
            return;
        }
        List<String> markers = detectMarkers(folder);
        if (markers.isEmpty()) {
            result.profiles.add(profile);
        } else {
            result.skipped.add(new Skipped(folder.getFileName().toString(), markers));
        }
    }

    private static ClientProfile parse(Path folder, int index) {
        Path spec = folder.resolve("run.spec.json");
        if (!Files.isRegularFile(spec)) {
            return null;
        }
        try (var reader = Files.newBufferedReader(spec, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            String name = readString(root, "name", folder.getFileName().toString());
            String version = readString(root, "version", "—");
            String mcVersion = readString(root, "mcVersion", "—");
            String runtime = "—";
            if (root.has("requires") && root.get("requires").isJsonObject()) {
                runtime = readString(root.getAsJsonObject("requires"), "runtime", runtime);
            }
            return new ClientProfile(folder, name, version, mcVersion, runtime, index);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String readString(JsonObject object, String key, String fallback) {
        if (object.has(key) && object.get(key).isJsonPrimitive()) {
            String value = object.get(key).getAsString();
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return fallback;
    }

    private static List<String> detectMarkers(Path folder) {
        Set<String> found = new LinkedHashSet<>();
        Path mods = folder.resolve("mods");
        List<Path> files = new ArrayList<>();
        if (Files.isDirectory(mods)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(mods)) {
                for (Path file : stream) {
                    if (Files.isRegularFile(file)) {
                        files.add(file);
                    }
                }
            } catch (IOException ignored) {
            }
        }
        files.sort(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)));
        for (Path file : files) {
            collect(file.getFileName().toString().toLowerCase(Locale.ROOT), found);
            if (file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
                try (ZipFile zip = new ZipFile(file.toFile())) {
                    var entries = zip.entries();
                    while (entries.hasMoreElements()) {
                        ZipEntry entry = entries.nextElement();
                        collect(entry.getName().toLowerCase(Locale.ROOT), found);
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return new ArrayList<>(found);
    }

    private static void collect(String text, Set<String> found) {
        for (String marker : MARKERS) {
            if (text.contains(marker)) {
                found.add(marker);
            }
        }
    }
}
