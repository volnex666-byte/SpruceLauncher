package com.spruce.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class VersionManager {

    public List<MinecraftVersion> scan(Path gameDir) {
        List<MinecraftVersion> result = new ArrayList<>();
        Path versionsDir = AppPaths.versionsDir(gameDir);
        if (!Files.isDirectory(versionsDir)) {
            return result;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(versionsDir)) {
            for (Path dir : stream) {
                if (!Files.isDirectory(dir)) {
                    continue;
                }
                String id = dir.getFileName().toString();
                Path json = dir.resolve(id + ".json");
                if (!Files.isRegularFile(json)) {
                    continue;
                }
                Path jar = dir.resolve(id + ".jar");
                result.add(new MinecraftVersion(id, dir, json, jar, readType(json)));
            }
        } catch (Exception ignored) {
        }
        result.sort(Comparator.comparing((MinecraftVersion v) -> v.id).reversed());
        return result;
    }

    public boolean librariesPresent(Path gameDir) {
        return Files.isDirectory(AppPaths.librariesDir(gameDir));
    }

    public boolean assetsPresent(Path gameDir) {
        return Files.isDirectory(AppPaths.assetsDir(gameDir));
    }

    private String readType(Path json) {
        try (Reader reader = Files.newBufferedReader(json, StandardCharsets.UTF_8)) {
            JsonObject object = JsonParser.parseReader(reader).getAsJsonObject();
            if (object.has("type") && object.get("type").isJsonPrimitive()) {
                return object.get("type").getAsString();
            }
        } catch (Exception ignored) {
        }
        return "release";
    }
}
