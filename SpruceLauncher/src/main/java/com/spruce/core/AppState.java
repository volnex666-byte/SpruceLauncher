package com.spruce.core;

import java.nio.file.Path;
import java.nio.file.Paths;

public class AppState {
    public final Config config;
    public final VersionManager versions = new VersionManager();
    public final LaunchService launcher = new LaunchService();

    private final javafx.collections.ObservableList<MinecraftVersion> availableVersions =
            javafx.collections.FXCollections.observableArrayList();
    private final javafx.beans.property.ObjectProperty<MinecraftVersion> selectedVersion =
            new javafx.beans.property.SimpleObjectProperty<>();
    private final javafx.beans.property.StringProperty status =
            new javafx.beans.property.SimpleStringProperty("Готово");

    public AppState() {
        this.config = Config.load();
        refreshVersions();
    }

    public Path gameDir() {
        if (config.gameDir != null && !config.gameDir.isBlank()) {
            return Paths.get(config.gameDir);
        }
        return AppPaths.defaultGameDir();
    }

    public void refreshVersions() {
        availableVersions.setAll(versions.scan(gameDir()));
        MinecraftVersion selected = null;
        for (MinecraftVersion version : availableVersions) {
            if (version.id.equals(config.lastVersion)) {
                selected = version;
            }
        }
        if (selected == null && !availableVersions.isEmpty()) {
            selected = availableVersions.get(0);
        }
        selectedVersion.set(selected);
    }

    public javafx.collections.ObservableList<MinecraftVersion> availableVersions() {
        return availableVersions;
    }

    public javafx.beans.property.ObjectProperty<MinecraftVersion> selectedVersionProperty() {
        return selectedVersion;
    }

    public MinecraftVersion selectedVersion() {
        return selectedVersion.get();
    }

    public void selectVersion(MinecraftVersion version) {
        selectedVersion.set(version);
        config.lastVersion = version == null ? "" : version.id;
        config.save();
    }

    public javafx.beans.property.StringProperty statusProperty() {
        return status;
    }

    public boolean isClientSelected(String clientId) {
        return config.selectedClients.contains(clientId);
    }

    public void toggleClient(String clientId) {
        if (config.selectedClients.contains(clientId)) {
            config.selectedClients.remove(clientId);
        } else {
            config.selectedClients.add(clientId);
        }
        config.save();
    }

    public String clientPath(String clientId) {
        return config.clientPaths.get(clientId);
    }

    public void setClientPath(String clientId, String path) {
        if (path == null || path.isBlank()) {
            config.clientPaths.remove(clientId);
        } else {
            config.clientPaths.put(clientId, path);
        }
        config.save();
    }
}
