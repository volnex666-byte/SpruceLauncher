package com.spruce.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

public class Sidebar extends VBox {
    private final Map<Section, StackPane> items = new EnumMap<>(Section.class);
    private final Map<Section, SVGPath> icons = new EnumMap<>(Section.class);

    public Sidebar(Consumer<Section> onNavigate) {
        getStyleClass().add("sidebar");
        setPrefWidth(200);
        setMinWidth(200);
        setMaxWidth(200);

        // Brand
        VBox brand = new VBox(3);
        brand.getStyleClass().add("brand");
        brand.setAlignment(Pos.CENTER_LEFT);

        SVGPath logo = Icons.icon(Icons.SPRUCE, 1.3, Theme.ACCENT);
        Label name = new Label("SpruceLauncher");
        name.getStyleClass().add("brand-name");
        Label tagline = new Label("MINECRAFT LAUNCHER");
        tagline.getStyleClass().add("brand-tagline");
        brand.getChildren().addAll(logo, name, tagline);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Label footer = new Label("v" + com.spruce.core.Config.VERSION);
        footer.getStyleClass().add("sidebar-footer");

        getChildren().add(brand);
        for (Section section : Section.values()) {
            StackPane item = buildItem(section, onNavigate);
            items.put(section, item);
            getChildren().add(item);
        }
        getChildren().addAll(spacer, footer);
    }

    private StackPane buildItem(Section section, Consumer<Section> onNavigate) {
        SVGPath icon = Icons.icon(section.icon, 1.12, Theme.MUTED);
        icons.put(section, icon);
        Label label = new Label(section.title);
        label.getStyleClass().add("nav-label");

        StackPane box = new StackPane();
        box.getStyleClass().add("nav-item");
        box.setAlignment(Pos.CENTER_LEFT);
        box.getChildren().addAll(icon, label);
        StackPane.setAlignment(icon, Pos.CENTER_LEFT);
        StackPane.setAlignment(label, Pos.CENTER_LEFT);
        icon.setTranslateX(16);
        label.setTranslateX(44);
        box.setOnMouseClicked(e -> onNavigate.accept(section));
        return box;
    }

    public void setActive(Section active) {
        for (Map.Entry<Section, StackPane> entry : items.entrySet()) {
            StackPane box = entry.getValue();
            boolean isActive = entry.getKey() == active;
            if (isActive) {
                if (!box.getStyleClass().contains("active")) box.getStyleClass().add("active");
            } else {
                box.getStyleClass().remove("active");
            }
            SVGPath icon = icons.get(entry.getKey());
            if (icon != null) {
                icon.setFill(isActive ? Theme.ACCENT : Theme.MUTED);
            }
        }
    }
}
