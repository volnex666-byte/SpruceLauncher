package com.spruce.ui.components;

import com.spruce.core.Client;
import com.spruce.ui.Icons;
import com.spruce.ui.Theme;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

import java.util.function.Consumer;

public class ClientCard extends VBox {
    private final Client client;
    private final Consumer<Client> onToggle;
    private final Label statusLabel = new Label();
    private final Region availDot = new Region();
    private final StackPane iconWrap = new StackPane();
    private final SVGPath icon;
    private final DropShadow hoverGlow = new DropShadow();
    private boolean selected;
    private boolean animations = true;

    public ClientCard(Client client, Consumer<Client> onToggle) {
        this.client = client;
        this.onToggle = onToggle;

        getStyleClass().add("client-card");
        setPrefWidth(280);
        setSpacing(11);
        setAlignment(Pos.TOP_LEFT);

        // Glow effect
        hoverGlow.setColor(Color.web("#39D353", 0));
        hoverGlow.setRadius(0);
        hoverGlow.setSpread(0);
        setEffect(hoverGlow);

        // Icon
        icon = Icons.icon(Icons.byKey(client.iconKey), 1.6, Theme.ACCENT);
        iconWrap.getStyleClass().add("client-icon-wrap");
        iconWrap.setMinSize(60, 60);
        iconWrap.setPrefSize(60, 60);
        iconWrap.setMaxSize(60, 60);
        iconWrap.getChildren().add(icon);

        // Availability dot
        availDot.setMinSize(8, 8);
        availDot.setPrefSize(8, 8);
        availDot.setMaxSize(8, 8);
        availDot.setStyle("-fx-background-radius: 4; -fx-background-color: #39D353;"
                + "-fx-effect: dropshadow(gaussian, rgba(57,211,83,0.9), 6, 0.3, 0, 0);");

        HBox iconRow = new HBox(10, iconWrap);
        iconRow.setAlignment(Pos.CENTER_LEFT);

        Label name = new Label(client.name);
        name.getStyleClass().add("card-title");
        name.setStyle("-fx-font-size: 20px;");

        Label version = new Label(client.version);
        version.getStyleClass().add("card-version");

        Label description = new Label(client.description);
        description.getStyleClass().add("card-description");
        description.setWrapText(true);
        description.setMaxWidth(220);

        // Status + availability row
        statusLabel.getStyleClass().add("status-chip");
        HBox statusRow = new HBox(8, statusLabel, availDot);
        statusRow.setAlignment(Pos.CENTER_LEFT);

        // Decorative accent bar at the bottom
        Region accent = new Region();
        accent.setMaxWidth(Double.MAX_VALUE);
        accent.setPrefHeight(2);
        accent.setStyle("-fx-background-color: linear-gradient(to right, rgba(57,211,83,0.55), rgba(30,143,66,0.10));"
                + "-fx-background-radius: 2;");

        getChildren().addAll(iconRow, name, version, description, statusRow, accent);

        setOnMouseClicked(e -> onToggle.accept(client));
        setOnMouseEntered(e -> onHoverEnter());
        setOnMouseExited(e -> onHoverExit());

        setSelected(false);
    }

    public Client client() {
        return client;
    }

    public void setAnimationsEnabled(boolean enabled) {
        this.animations = enabled;
    }

    private void onHoverEnter() {
        if (animations) {
            TranslateTransition move = new TranslateTransition(Duration.millis(170), this);
            move.setToY(-5);
            move.setInterpolator(Interpolator.EASE_BOTH);
            move.play();
            // Brighten glow
            animateGlow(selected ? 26 : 16, selected ? 0.55 : 0.28);
        }
    }

    private void onHoverExit() {
        if (animations) {
            TranslateTransition move = new TranslateTransition(Duration.millis(170), this);
            move.setToY(0);
            move.setInterpolator(Interpolator.EASE_BOTH);
            move.play();
            animateGlow(selected ? 18 : 0, selected ? 0.38 : 0);
        } else {
            setTranslateY(0);
        }
    }

    private void animateGlow(double radius, double alpha) {
        if (!animations) {
            hoverGlow.setRadius(radius);
            hoverGlow.setColor(Color.web("#39D353", alpha));
            return;
        }
        Timeline t = new Timeline(new KeyFrame(Duration.millis(200),
                new KeyValue(hoverGlow.radiusProperty(), radius, Interpolator.EASE_BOTH)));
        t.play();
        hoverGlow.setColor(Color.web("#39D353", alpha));
    }

    public void setSelected(boolean value) {
        this.selected = value;
        if (value) {
            if (!getStyleClass().contains("selected")) getStyleClass().add("selected");
            statusLabel.setText("Выбран");
            icon.setFill(Theme.ACCENT);
            animateGlow(18, 0.38);
        } else {
            getStyleClass().remove("selected");
            statusLabel.setText("Не выбран");
            icon.setFill(Theme.MUTED);
            animateGlow(0, 0);
        }
    }

    public boolean isSelected() {
        return selected;
    }
}
