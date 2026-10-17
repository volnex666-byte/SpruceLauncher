package com.spruce.core;

import java.nio.file.Path;
import java.util.Locale;

public final class ClientProfile {
    public final Path dir;
    public final String name;
    public final String version;
    public final String mcVersion;
    public final String runtime;
    public final String accent;

    private static final String[] PALETTE = {"#9D5CF6", "#8B5CF6", "#22C55E", "#39D353"};

    public ClientProfile(Path dir, String name, String version, String mcVersion, String runtime, int index) {
        this.dir = dir;
        this.name = name;
        this.version = version;
        this.mcVersion = mcVersion;
        this.runtime = runtime;
        this.accent = PALETTE[Math.floorMod(index, PALETTE.length)];
    }

    public String id() {
        return name.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
    }

    public Client toClient() {
        String description = "Fabric-профиль из папки · " + runtime;
        return new Client(id(), name, version + " · MC " + mcVersion, description, "diamond", accent);
    }
}
