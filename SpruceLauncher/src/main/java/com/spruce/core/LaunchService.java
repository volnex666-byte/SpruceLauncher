package com.spruce.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class LaunchService {

    public LaunchResult launch(MinecraftVersion version, Config config,
                               Consumer<String> log, Consumer<Double> progress) {
        try {
            if (version == null) {
                return LaunchResult.failure("Версия Minecraft не выбрана.");
            }
            Path gameDir = gameDir(config);
            if (!Files.isDirectory(gameDir)) {
                return LaunchResult.failure("Каталог игры не найден: " + gameDir);
            }
            if (!Files.isRegularFile(version.jsonPath)) {
                return LaunchResult.failure("Файл версии не найден: " + version.jsonPath);
            }

            Path javaExe = resolveJava(config);
            if (javaExe == null) {
                return LaunchResult.failure("Java не найдена. Укажите путь к java.exe в настройках.");
            }

            log.accept("Подготовка версии " + version.id + "...");
            progress.accept(0.05);

            JsonObject root = loadMergedJson(version.jsonPath);
            if (root == null) {
                return LaunchResult.failure("Не удалось прочитать описание версии.");
            }

            String assetsIndex = optString(root, "assets");
            String mainClass = optString(root, "mainClass");
            if (mainClass == null) {
                return LaunchResult.failure("Не удалось определить главный класс версии.");
            }

            List<Path> classpath = new ArrayList<>();
            if (Files.isRegularFile(version.jarPath)) {
                classpath.add(version.jarPath);
            }
            List<Path> nativeJars = new ArrayList<>();
            collectLibraries(root, gameDir, classpath, nativeJars, log);
            progress.accept(0.5);

            Path nativesDir = version.directory.resolve("natives");
            extractNatives(nativeJars, nativesDir, log);
            progress.accept(0.8);

            String classpathString = classpath.stream()
                    .map(Path::toString)
                    .collect(Collectors.joining(File.pathSeparator));

            Map<String, String> vars = new HashMap<>();
            String playerName = (config.username == null || config.username.isBlank()) ? "Player" : config.username;
            vars.put("auth_player_name", playerName);
            vars.put("version_name", version.id);
            vars.put("game_directory", gameDir.toString());
            vars.put("assets_root", AppPaths.assetsDir(gameDir).toString());
            vars.put("assets_index_name", assetsIndex == null ? "legacy" : assetsIndex);
            vars.put("auth_uuid", offlineUuid(playerName));
            vars.put("auth_access_token", "0");
            vars.put("auth_session", "0");
            vars.put("user_type", "legacy");
            vars.put("version_type", version.type == null ? "release" : version.type);
            vars.put("natives_directory", nativesDir.toString());
            vars.put("launcher_name", "SpruceLauncher");
            vars.put("launcher_version", "1.0.0");
            vars.put("classpath", classpathString);
            vars.put("library_directory", AppPaths.librariesDir(gameDir).toString());

            List<String> command = new ArrayList<>();
            command.add(javaExe.toString());
            command.add("-Xmx" + Math.max(Config.MIN_MEMORY, config.memoryMb) + "M");
            command.add("-Xms" + Math.max(Config.MIN_MEMORY, Math.min(config.minMemoryMb, config.memoryMb)) + "M");
            command.add("-Dfile.encoding=UTF-8");

            if (root.has("arguments")) {
                buildArgsFrom(root, "jvm", command, vars);
            } else {
                command.add("-Djava.library.path=" + vars.get("natives_directory"));
                command.add("-cp");
                command.add(classpathString);
            }

            command.add(mainClass);

            if (root.has("arguments")) {
                buildArgsFrom(root, "game", command, vars);
            } else if (root.has("minecraftArguments")) {
                command.addAll(splitArgs(substitute(root.get("minecraftArguments").getAsString(), vars)));
            }

            log.accept("Команда запуска: " + String.join(" ", command));
            progress.accept(0.9);

            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(gameDir.toFile());
            builder.redirectErrorStream(true);
            Process process = builder.start();
            progress.accept(1.0);

            Thread pump = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        log.accept(line);
                    }
                } catch (IOException ignored) {
                }
            }, "spruce-log-pump");
            pump.setDaemon(true);
            pump.start();

            return LaunchResult.running(process);
        } catch (Exception e) {
            return LaunchResult.failure("Ошибка запуска: " + e.getMessage());
        }
    }

    public Path gameDir(Config config) {
        if (config.gameDir != null && !config.gameDir.isBlank()) {
            return Paths.get(config.gameDir);
        }
        return AppPaths.defaultGameDir();
    }

    private Path resolveJava(Config config) {
        if (config.javaPath != null && !config.javaPath.isBlank()) {
            Path path = Paths.get(config.javaPath);
            if (Files.isRegularFile(path)) {
                return path;
            }
        }
        String home = System.getenv("JAVA_HOME");
        if (home != null && !home.isBlank()) {
            Path path = Paths.get(home, "bin", isWindows() ? "java.exe" : "java");
            if (Files.isRegularFile(path)) {
                return path;
            }
        }
        return Paths.get(isWindows() ? "java.exe" : "java");
    }

    private void collectLibraries(JsonObject root, Path gameDir, List<Path> classpath,
                                  List<Path> nativeJars, Consumer<String> log) {
        if (!root.has("libraries") || !root.get("libraries").isJsonArray()) {
            return;
        }
        Path librariesDir = AppPaths.librariesDir(gameDir);
        for (JsonElement element : root.getAsJsonArray("libraries")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject library = element.getAsJsonObject();
            if (!rulesAllow(library.has("rules") ? library.getAsJsonArray("rules") : null)) {
                continue;
            }
            String name = optString(library, "name");

            Path artifact = null;
            if (library.has("downloads") && library.get("downloads").isJsonObject()) {
                JsonObject downloads = library.getAsJsonObject("downloads");
                if (downloads.has("artifact") && downloads.get("artifact").isJsonObject()) {
                    String relative = optString(downloads.getAsJsonObject("artifact"), "path");
                    if (relative != null) {
                        artifact = librariesDir.resolve(relative.replace('/', File.separatorChar));
                    }
                }
            }
            if (artifact == null && name != null) {
                artifact = mavenPath(librariesDir, name);
            }
            if (artifact != null && Files.isRegularFile(artifact)) {
                classpath.add(artifact);
            }

            if (library.has("natives") && library.get("natives").isJsonObject()) {
                String classifier = classifierForOs(library.getAsJsonObject("natives"));
                if (classifier != null) {
                    classifier = classifier.replace("${arch}", archBits());
                    Path nativePath = null;
                    if (library.has("downloads") && library.get("downloads").isJsonObject()) {
                        JsonObject downloads = library.getAsJsonObject("downloads");
                        if (downloads.has("classifiers") && downloads.get("classifiers").isJsonObject()) {
                            JsonObject classifiers = downloads.getAsJsonObject("classifiers");
                            if (classifiers.has(classifier) && classifiers.get(classifier).isJsonObject()) {
                                String relative = optString(classifiers.getAsJsonObject(classifier), "path");
                                if (relative != null) {
                                    nativePath = librariesDir.resolve(relative.replace('/', File.separatorChar));
                                }
                            }
                        }
                    }
                    if (nativePath == null && name != null) {
                        nativePath = mavenPath(librariesDir, withClassifier(name, classifier));
                    }
                    if (nativePath != null && Files.isRegularFile(nativePath)) {
                        nativeJars.add(nativePath);
                    }
                }
            }
        }
    }

    private void extractNatives(List<Path> jars, Path nativesDir, Consumer<String> log) throws IOException {
        Files.createDirectories(nativesDir);
        for (Path jar : jars) {
            try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(jar))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.isDirectory()) {
                        continue;
                    }
                    String name = entry.getName();
                    if (name.startsWith("META-INF/") || name.contains("..")) {
                        continue;
                    }
                    Path target = nativesDir.resolve(name).normalize();
                    if (!target.startsWith(nativesDir)) {
                        continue;
                    }
                    Path parent = target.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception e) {
                log.accept("Не удалось распаковать natives: " + jar.getFileName());
            }
        }
    }

    private void buildArgsFrom(JsonObject root, String section, List<String> out, Map<String, String> vars) {
        JsonObject arguments = root.getAsJsonObject("arguments");
        if (arguments == null || !arguments.has(section)) {
            return;
        }
        for (JsonElement element : arguments.getAsJsonArray(section)) {
            if (element.isJsonPrimitive()) {
                out.add(substitute(element.getAsString(), vars));
            } else if (element.isJsonObject()) {
                JsonObject object = element.getAsJsonObject();
                JsonArray rules = object.has("rules") ? object.getAsJsonArray("rules") : null;
                if (!rulesAllow(rules)) {
                    continue;
                }
                JsonElement value = object.get("value");
                if (value == null) {
                    continue;
                }
                if (value.isJsonArray()) {
                    for (JsonElement item : value.getAsJsonArray()) {
                        out.add(substitute(item.getAsString(), vars));
                    }
                } else {
                    out.add(substitute(value.getAsString(), vars));
                }
            }
        }
    }

    private boolean rulesAllow(JsonArray rules) {
        if (rules == null || rules.size() == 0) {
            return true;
        }
        boolean allowed = false;
        for (JsonElement element : rules) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject rule = element.getAsJsonObject();
            String action = optString(rule, "action");
            boolean matches = true;

            if (rule.has("os") && rule.get("os").isJsonObject()) {
                JsonObject os = rule.getAsJsonObject("os");
                String name = optString(os, "name");
                if (name != null && !osName().equals(name)) {
                    matches = false;
                }
                String arch = optString(os, "arch");
                if (arch != null && !arch.equals(System.getProperty("os.arch"))) {
                    matches = false;
                }
                String version = optString(os, "version");
                String osVersion = System.getProperty("os.version");
                if (version != null && osVersion != null && !osVersion.matches(version)) {
                    matches = false;
                }
            }

            if (rule.has("features") && rule.get("features").isJsonObject()) {
                for (Map.Entry<String, JsonElement> feature : rule.getAsJsonObject("features").entrySet()) {
                    if (feature.getValue().isJsonPrimitive() && feature.getValue().getAsBoolean()) {
                        matches = false;
                    }
                }
            }

            if (matches) {
                allowed = "allow".equals(action);
            }
        }
        return allowed;
    }

    private JsonObject loadMergedJson(Path jsonPath) throws IOException {
        JsonObject child;
        try (Reader reader = Files.newBufferedReader(jsonPath, StandardCharsets.UTF_8)) {
            child = JsonParser.parseReader(reader).getAsJsonObject();
        }
        if (child.has("inheritsFrom") && child.get("inheritsFrom").isJsonPrimitive()) {
            String parentId = child.get("inheritsFrom").getAsString();
            Path parentJson = jsonPath.getParent().getParent().resolve(parentId).resolve(parentId + ".json");
            if (Files.isRegularFile(parentJson)) {
                return merge(loadMergedJson(parentJson), child);
            }
        }
        return child;
    }

    private JsonObject merge(JsonObject parent, JsonObject child) {
        JsonObject result = parent.deepCopy();
        for (Map.Entry<String, JsonElement> entry : child.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            if ("libraries".equals(key) && result.has("libraries") && value.isJsonArray()) {
                JsonArray merged = new JsonArray();
                merged.addAll(result.getAsJsonArray("libraries"));
                merged.addAll(value.getAsJsonArray());
                result.add(key, merged);
            } else if ("arguments".equals(key) && result.has("arguments") && result.get("arguments").isJsonObject()) {
                JsonObject parentArgs = result.getAsJsonObject("arguments");
                JsonObject childArgs = value.getAsJsonObject();
                JsonObject merged = parentArgs.deepCopy();
                for (Map.Entry<String, JsonElement> arg : childArgs.entrySet()) {
                    if (merged.has(arg.getKey()) && merged.get(arg.getKey()).isJsonArray()) {
                        JsonArray array = new JsonArray();
                        array.addAll(merged.getAsJsonArray(arg.getKey()));
                        array.addAll(arg.getValue().getAsJsonArray());
                        merged.add(arg.getKey(), array);
                    } else {
                        merged.add(arg.getKey(), arg.getValue());
                    }
                }
                result.add(key, merged);
            } else {
                result.add(key, value);
            }
        }
        return result;
    }

    private Path mavenPath(Path librariesDir, String coordinate) {
        String[] parts = coordinate.split(":");
        if (parts.length < 3) {
            return null;
        }
        String group = parts[0].replace('.', '/');
        String artifact = parts[1];
        String version = parts[2];
        String classifier = parts.length > 3 ? parts[3] : null;
        String extension = "jar";
        if (version.contains("@")) {
            String[] split = version.split("@", 2);
            version = split[0];
            extension = split[1];
        }
        if (classifier != null && classifier.contains("@")) {
            String[] split = classifier.split("@", 2);
            classifier = split[0];
            extension = split[1];
        }
        String fileName = artifact + "-" + version
                + (classifier != null ? "-" + classifier : "") + "." + extension;
        return librariesDir.resolve(group).resolve(artifact).resolve(version).resolve(fileName);
    }

    private String withClassifier(String coordinate, String classifier) {
        String[] parts = coordinate.split(":");
        if (parts.length < 3) {
            return coordinate;
        }
        return parts[0] + ":" + parts[1] + ":" + parts[2] + ":" + classifier;
    }

    private String classifierForOs(JsonObject natives) {
        String key = osName();
        if (natives.has(key) && natives.get(key).isJsonPrimitive()) {
            return natives.get(key).getAsString();
        }
        return null;
    }

    private String substitute(String value, Map<String, String> vars) {
        String result = value;
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            result = result.replace("${" + entry.getKey() + "}",
                    entry.getValue() == null ? "" : entry.getValue());
        }
        return result;
    }

    private List<String> splitArgs(String args) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < args.length(); i++) {
            char c = args.charAt(i);
            if (c == '"') {
                quoted = !quoted;
            } else if (Character.isWhitespace(c) && !quoted) {
                if (current.length() > 0) {
                    result.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            result.add(current.toString());
        }
        return result;
    }

    private String offlineUuid(String name) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] data = digest.digest(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
            data[6] = (byte) ((data[6] & 0x0f) | 0x30);
            data[8] = (byte) ((data[8] & 0x3f) | 0x80);
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < 16; i++) {
                if (i == 4 || i == 6 || i == 8 || i == 10) {
                    builder.append('-');
                }
                builder.append(String.format("%02x", data[i]));
            }
            return builder.toString();
        } catch (Exception e) {
            return "00000000-0000-0000-0000-000000000000";
        }
    }

    private String osName() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return "windows";
        }
        if (os.contains("mac")) {
            return "osx";
        }
        return "linux";
    }

    private String archBits() {
        String arch = System.getProperty("os.arch", "");
        if (arch.contains("64")) {
            return "64";
        }
        if (arch.contains("arm") || arch.contains("aarch")) {
            return "arm64";
        }
        return "32";
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private String optString(JsonObject object, String key) {
        if (object != null && object.has(key) && object.get(key).isJsonPrimitive()) {
            return object.get(key).getAsString();
        }
        return null;
    }
}
