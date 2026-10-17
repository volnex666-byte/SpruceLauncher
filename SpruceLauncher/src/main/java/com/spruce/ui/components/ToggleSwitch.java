package com.spruce.ui.components;

import javafx.animation.Interpolator;
import javafx.animation.TranslateTransition;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.util.Duration;

public class ToggleSwitch extends Pane {
    private static final double WIDTH = 46;
    private static final double HEIGHT = 26;
    private static final double KNOB = 20;

    private final BooleanProperty selected = new SimpleBooleanProperty(false);
    private final Region track = new Region();
    private final Region knob = new Region();
    private boolean animations = true;

    public ToggleSwitch() {
        setPrefSize(WIDTH, HEIGHT);
        setMinSize(WIDTH, HEIGHT);
        setMaxSize(WIDTH, HEIGHT);
        setFocusTraversable(false);

        track.resizeRelocate(0, 0, WIDTH, HEIGHT);
        track.getStyleClass().add("toggle-track");

        knob.resizeRelocate(3, 3, KNOB, KNOB);
        knob.getStyleClass().add("toggle-knob");

        getChildren().addAll(track, knob);
        setOnMouseClicked(e -> selected.set(!selected.get()));
        selected.addListener((o, a, b) -> apply(b, true));
        apply(false, false);
    }

    public void setAnimationsEnabled(boolean enabled) {
        this.animations = enabled;
    }

    public BooleanProperty selectedProperty() {
        return selected;
    }

    public boolean isSelected() {
        return selected.get();
    }

    public void setSelected(boolean value) {
        selected.set(value);
    }

    private void apply(boolean on, boolean animate) {
        double target = on ? (WIDTH - KNOB - 3) : 3;
        if (on) {
            if (!track.getStyleClass().contains("on")) {
                track.getStyleClass().add("on");
            }
        } else {
            track.getStyleClass().remove("on");
        }
        if (animate && animations) {
            TranslateTransition transition = new TranslateTransition(Duration.millis(170), knob);
            transition.setToX(target);
            transition.setInterpolator(Interpolator.EASE_BOTH);
            transition.play();
        } else {
            knob.setTranslateX(target);
        }
    }
}
