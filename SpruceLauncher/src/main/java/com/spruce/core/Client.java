package com.spruce.core;

public class Client {
    public final String id;
    public final String name;
    public final String version;
    public final String description;
    public final String iconKey;
    public final String accent;

    public Client(String id, String name, String version, String description, String iconKey, String accent) {
        this.id = id;
        this.name = name;
        this.version = version;
        this.description = description;
        this.iconKey = iconKey;
        this.accent = accent;
    }
}
