package com.spruce.ui.components;

import com.spruce.core.AppState;
import com.spruce.core.Client;
import com.spruce.core.ClientRegistry;
import javafx.animation.AnimationTimer;
import javafx.animation.Interpolator;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MergeField extends StackPane {
    private static final double CONNECTOR_WIDTH = 200;
    private static final double CARD_SHIFT = 44;

    private final AppState state;
    private final HBox row = new HBox();
    private final Pane connector = new Pane();
    private final Canvas canvas = new Canvas();
    private final ClientCard leftCard;
    private final ClientCard rightCard;

    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private final List<double[]> sparks = new ArrayList<>();
    private final Random random = new Random();

    private AnimationTimer timer;
    private boolean merged;
    private boolean animations = true;
    private double phase;
    // Additional phases for orbital animation
    private double orbPhase;
    private Runnable onSelectionChanged;

    public MergeField(AppState state) {
        this.state = state;
        getStyleClass().add("merge-field");

        row.setAlignment(Pos.CENTER);
        row.setFillHeight(false);
        connector.getStyleClass().add("merge-connector");
        connector.setPrefWidth(CONNECTOR_WIDTH);
        connector.setMinWidth(CONNECTOR_WIDTH);
        connector.setMaxWidth(CONNECTOR_WIDTH);
        connector.setPrefHeight(280);
        connector.getChildren().add(canvas);

        List<Client> available = ClientRegistry.all();
        leftCard = available.size() >= 2 ? new ClientCard(available.get(0), this::onToggle) : null;
        rightCard = available.size() >= 2 ? new ClientCard(available.get(1), this::onToggle) : null;

        if (leftCard != null && rightCard != null) {
            row.getChildren().addAll(leftCard, connector, rightCard);
        } else {
            setVisible(false);
            setManaged(false);
        }
        getChildren().add(row);

        connector.widthProperty().addListener((o, a, b) -> canvas.setWidth(b.doubleValue()));
        connector.heightProperty().addListener((o, a, b) -> canvas.setHeight(b.doubleValue()));
        canvas.setWidth(CONNECTOR_WIDTH);
        canvas.setHeight(280);

        progress.addListener((o, a, b) -> applyProgress(b.doubleValue()));

        startTimer();
        refresh();
    }

    public void setOnSelectionChanged(Runnable callback) {
        this.onSelectionChanged = callback;
    }

    public void setAnimationsEnabled(boolean enabled) {
        this.animations = enabled;
        if (leftCard != null) leftCard.setAnimationsEnabled(enabled);
        if (rightCard != null) rightCard.setAnimationsEnabled(enabled);
    }

    public void refresh() {
        if (leftCard == null || rightCard == null) {
            if (onSelectionChanged != null) onSelectionChanged.run();
            return;
        }
        leftCard.setSelected(state.isClientSelected(leftCard.client().id));
        rightCard.setSelected(state.isClientSelected(rightCard.client().id));
        boolean both = leftCard.isSelected() && rightCard.isSelected();
        setMerged(both);
        if (onSelectionChanged != null) onSelectionChanged.run();
    }

    private void onToggle(Client client) {
        state.toggleClient(client.id);
        refresh();
    }

    private void setMerged(boolean value) {
        if (merged == value) return;
        merged = value;
        if (!animations) {
            progress.set(value ? 1 : 0);
            return;
        }
        Timeline timeline = new Timeline(new KeyFrame(
                Duration.millis(560),
                new KeyValue(progress, value ? 1 : 0, Interpolator.EASE_BOTH)));
        timeline.play();
    }

    private void applyProgress(double value) {
        leftCard.setTranslateX(value * CARD_SHIFT);
        rightCard.setTranslateX(-value * CARD_SHIFT);
        connector.setOpacity(0.20 + value * 0.80);
        if (value > 0.82 && sparks.isEmpty()) {
            for (int i = 0; i < 14; i++) {
                sparks.add(new double[]{random.nextDouble(), random.nextDouble() * 0.6 + 0.2,
                        random.nextDouble() * Math.PI * 2});
            }
        }
        if (value < 0.18) sparks.clear();
    }

    private void startTimer() {
        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (!merged && progress.get() < 0.01) {
                    GraphicsContext gc = canvas.getGraphicsContext2D();
                    gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
                    return;
                }
                phase += 0.026;
                orbPhase += 0.018;
                render();
            }
        };
        timer.start();
    }

    public void stop() {
        if (timer != null) timer.stop();
    }

    private void render() {
        double width = canvas.getWidth();
        double height = canvas.getHeight();
        if (width <= 0 || height <= 0) return;

        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, width, height);

        double cy = height / 2.0;
        double prog = progress.get();
        double spread = 12 + Math.sin(phase) * 3.5;

        // ── Light curves (green gradient) ──
        LinearGradient lineGrad = new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#39D353", 0.0)),
                new Stop(0.38, Color.web("#39D353", 0.75 * prog)),
                new Stop(0.5, Color.web("#7EF59A", 0.95 * prog)),
                new Stop(0.62, Color.web("#1E8F42", 0.75 * prog)),
                new Stop(1, Color.web("#1E8F42", 0.0)));

        gc.setStroke(lineGrad);
        gc.setLineWidth(1.5);
        drawCurve(gc, width, cy - spread, -16);
        drawCurve(gc, width, cy + spread, 16);

        // Second thinner lines
        gc.setLineWidth(0.7);
        LinearGradient thinGrad = new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#2AB847", 0.0)),
                new Stop(0.5, Color.web("#2AB847", 0.40 * prog)),
                new Stop(1, Color.web("#2AB847", 0.0)));
        gc.setStroke(thinGrad);
        drawCurve(gc, width, cy - spread * 1.7, -8);
        drawCurve(gc, width, cy + spread * 1.7, 8);

        // ── Core nucleus ──
        double coreRadius = (22 + Math.sin(phase * 1.4) * 5) * prog;
        // Outer glow
        RadialGradient outerGlow = new RadialGradient(0, 0, 0.5, 0.5, 0.5, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#39D353", 0.0)),
                new Stop(0.4, Color.web("#39D353", 0.12 * prog)),
                new Stop(1, Color.TRANSPARENT));
        double glowR = coreRadius * 2.8;
        gc.setFill(outerGlow);
        gc.fillOval(width / 2 - glowR, cy - glowR, glowR * 2, glowR * 2);

        // Inner core
        RadialGradient core = new RadialGradient(0, 0, 0.5, 0.5, 0.5, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#C8F5D2", 0.98 * prog)),
                new Stop(0.35, Color.web("#39D353", 0.75 * prog)),
                new Stop(0.70, Color.web("#1E8F42", 0.38 * prog)),
                new Stop(1, Color.TRANSPARENT));
        gc.setFill(core);
        gc.fillOval(width / 2 - coreRadius, cy - coreRadius, coreRadius * 2, coreRadius * 2);

        // ── Rotating orbital ring ──
        if (prog > 0.5) {
            double ringR = coreRadius * 1.9;
            double ringAlpha = (prog - 0.5) * 2;
            int segments = 24;
            for (int i = 0; i < segments; i++) {
                double angle = orbPhase + i * 2 * Math.PI / segments;
                double ox = width / 2 + Math.cos(angle) * ringR;
                double oy = cy + Math.sin(angle) * ringR * 0.35; // Elliptical
                double dotAlpha = (0.3 + 0.7 * Math.sin(angle + phase * 2)) * ringAlpha;
                double dotR = 1.2 + Math.sin(angle * 2) * 0.6;
                gc.setFill(Color.web("#39D353", Math.max(0, dotAlpha)));
                gc.fillOval(ox - dotR, oy - dotR, dotR * 2, dotR * 2);
            }
        }

        // ── Sparks ──
        for (double[] spark : sparks) {
            double sx = spark[0] * width;
            double sy = cy + (spark[1] - 0.5) * 50 + Math.sin(phase * 2 + spark[2]) * 5;
            double size = 1.2 + Math.abs(Math.sin(phase * 2.5 + spark[2])) * 1.6;
            double alpha = 0.55 + 0.45 * Math.abs(Math.sin(phase * 1.8 + spark[2]));
            gc.setFill(Color.web("#C8F5D2", alpha * prog));
            gc.fillOval(sx, sy, size, size);
        }
    }

    private void drawCurve(GraphicsContext gc, double width, double midY, double bend) {
        gc.beginPath();
        gc.moveTo(0, midY);
        gc.quadraticCurveTo(width / 2, midY + bend, width, midY);
        gc.stroke();
    }
}
