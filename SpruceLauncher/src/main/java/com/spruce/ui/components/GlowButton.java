package com.spruce.ui.components;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.scene.control.Button;
import javafx.scene.effect.DropShadow;
import javafx.scene.paint.Color;
import javafx.util.Duration;

public class GlowButton extends Button {
    private final DropShadow glow = new DropShadow();
    private boolean animations = true;
    private Timeline pulse;
    private boolean pulsing;

    public GlowButton(String text) {
        super(text);
        getStyleClass().add("glow-button");
        // Green glow
        glow.setColor(Color.web("#39D353", 0.55));
        glow.setRadius(14);
        glow.setSpread(0.10);
        setEffect(glow);

        setOnMouseEntered(e -> {
            animateScale(1.04);
            animateGlow(28);
        });
        setOnMouseExited(e -> {
            animateScale(1.0);
            animateGlow(14);
        });

        setOnMousePressed(e -> {
            if (animations) {
                ScaleTransition t = new ScaleTransition(Duration.millis(80), this);
                t.setToX(0.97);
                t.setToY(0.97);
                t.setInterpolator(Interpolator.EASE_OUT);
                t.play();
            }
        });
        setOnMouseReleased(e -> {
            double target = isHover() ? 1.04 : 1.0;
            animateScale(target);
        });
    }

    public void setAnimationsEnabled(boolean enabled) {
        this.animations = enabled;
        if (!enabled) {
            stopPulse();
        } else if (pulsing) {
            startPulse();
        }
    }

    public void setGlowColor(Color color) {
        glow.setColor(color);
    }

    public void setPulsing(boolean value) {
        this.pulsing = value;
        if (value && animations) {
            startPulse();
        } else {
            stopPulse();
        }
    }

    private void startPulse() {
        stopPulse();
        pulse = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(glow.radiusProperty(), 14.0, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.millis(1400),
                        new KeyValue(glow.radiusProperty(), 30.0, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.millis(2800),
                        new KeyValue(glow.radiusProperty(), 14.0, Interpolator.EASE_BOTH)));
        pulse.setCycleCount(Timeline.INDEFINITE);
        pulse.play();
    }

    private void stopPulse() {
        if (pulse != null) {
            pulse.stop();
            pulse = null;
        }
        glow.setRadius(14);
    }

    private void animateGlow(double radius) {
        if (!animations) {
            glow.setRadius(radius);
            return;
        }
        Timeline timeline = new Timeline(new KeyFrame(
                Duration.millis(200),
                new KeyValue(glow.radiusProperty(), radius, Interpolator.EASE_BOTH)));
        timeline.play();
    }

    private void animateScale(double target) {
        if (animations) {
            ScaleTransition t = new ScaleTransition(Duration.millis(150), this);
            t.setToX(target);
            t.setToY(target);
            t.setInterpolator(Interpolator.EASE_BOTH);
            t.play();
        } else {
            setScaleX(target);
            setScaleY(target);
        }
    }
}
