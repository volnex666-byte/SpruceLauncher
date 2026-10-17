package com.spruce.ui;

import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ParticleBackdrop extends Pane {
    private final Canvas canvas = new Canvas();
    private final List<Particle> particles = new ArrayList<>();
    private final Random random = new Random();
    private AnimationTimer timer;
    private boolean particlesEnabled = true;
    private boolean glowEnabled = true;
    private boolean reduceMotion = false;
    private long lastNanos = 0;
    private double phase = 0;

    // Grid drift
    private double gridOffsetX = 0;
    private double gridOffsetY = 0;

    // Theme palette
    private Color gridColor = Color.web("#39D353", 0.028);
    private Color blobA = Color.web("#39D353");
    private Color blobB = Color.web("#1E8F42");
    private double blobAlpha = 0.16;
    private Color particleA = Color.web("#39D353");
    private Color particleB = Color.web("#2AB847");
    private double particleAlphaScale = 1.0;

    private static final class Particle {
        double x, y;
        double vx, vy;
        double radius;
        double alpha;
        double twinkleSeed;
        // All green-toned
    }

    public ParticleBackdrop() {
        getChildren().add(canvas);
        setPickOnBounds(false);
        canvas.setMouseTransparent(true);
        widthProperty().addListener((o, a, b) -> rebuild());
        heightProperty().addListener((o, a, b) -> rebuild());
        rebuild();
        start();
    }

    public void setParticlesEnabled(boolean enabled) {
        this.particlesEnabled = enabled;
    }

    public void setGlowEnabled(boolean enabled) {
        this.glowEnabled = enabled;
    }

    public void setReduceMotion(boolean reduce) {
        this.reduceMotion = reduce;
    }

    public void setTheme(String theme) {
        if (Theme.isLight(theme)) {
            gridColor = Color.web("#1E8F42", 0.07);
            blobA = Color.web("#39D353");
            blobB = Color.web("#7ED99A");
            blobAlpha = 0.13;
            particleA = Color.web("#1E8F42");
            particleB = Color.web("#2AB847");
            particleAlphaScale = 0.9;
        } else {
            gridColor = Color.web("#39D353", 0.028);
            blobA = Color.web("#39D353");
            blobB = Color.web("#1E8F42");
            blobAlpha = 0.16;
            particleA = Color.web("#39D353");
            particleB = Color.web("#2AB847");
            particleAlphaScale = 1.0;
        }
    }

    private void rebuild() {
        canvas.setWidth(getWidth());
        canvas.setHeight(getHeight());
        particles.clear();
        if (getWidth() <= 0 || getHeight() <= 0) return;
        int count = reduceMotion ? 12 : 30;
        for (int i = 0; i < count; i++) {
            Particle p = new Particle();
            p.x = random.nextDouble() * getWidth();
            p.y = random.nextDouble() * getHeight();
            p.vx = (random.nextDouble() - 0.5) * 0.20;
            p.vy = -(random.nextDouble() * 0.18 + 0.03);
            p.radius = random.nextDouble() * 1.6 + 0.5;
            p.alpha = random.nextDouble() * 0.32 + 0.10;
            p.twinkleSeed = random.nextDouble() * Math.PI * 2;
            particles.add(p);
        }
    }

    private void start() {
        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (lastNanos == 0) lastNanos = now;
                double delta = (now - lastNanos) / 1_000_000_000.0;
                lastNanos = now;
                if (delta > 0.1) delta = 0.1;
                update(delta);
            }
        };
        timer.start();
    }

    public void stop() {
        if (timer != null) timer.stop();
    }

    private void update(double delta) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double width = canvas.getWidth();
        double height = canvas.getHeight();
        if (width <= 0 || height <= 0) return;

        gc.clearRect(0, 0, width, height);

        // Slowly drifting grid
        if (!reduceMotion) {
            gridOffsetX = (gridOffsetX + delta * 3) % 64;
            gridOffsetY = (gridOffsetY + delta * 1.5) % 64;
        }
        drawGrid(gc, width, height);

        // Soft green glow blobs
        if (glowEnabled) {
            double pulse = 1.0 + 0.06 * Math.sin(phase * 0.7);
            drawGlow(gc, width * 0.14, height * 0.18, width * 0.38 * pulse, blobA);
            drawGlow(gc, width * 0.85, height * 0.78, width * 0.42 * pulse, blobB);
        }

        if (!particlesEnabled) return;

        phase += delta;
        double speed = reduceMotion ? 0.20 : 1.0;
        for (Particle p : particles) {
            if (!reduceMotion) {
                p.x += p.vx * speed * delta * 60;
                p.y += p.vy * speed * delta * 60;
                if (p.y < -10) {
                    p.y = height + 10;
                    p.x = random.nextDouble() * width;
                }
                if (p.x < -10) p.x = width + 10;
                else if (p.x > width + 10) p.x = -10;
            }
            double twinkle = 0.65 + 0.35 * Math.sin(phase * 1.8 + p.twinkleSeed + p.x * 0.02);
            double greenShift = 0.5 + 0.5 * Math.sin(p.twinkleSeed);
            Color base = greenShift > 0.5 ? particleA : particleB;
            gc.setFill(Color.color(base.getRed(), base.getGreen(), base.getBlue(),
                    p.alpha * twinkle * particleAlphaScale));
            gc.fillOval(p.x, p.y, p.radius, p.radius);
        }
    }

    private void drawGrid(GraphicsContext gc, double width, double height) {
        double step = 64;
        gc.setStroke(gridColor);
        gc.setLineWidth(1);
        for (double x = -gridOffsetX; x <= width; x += step) {
            gc.strokeLine(x, 0, x, height);
        }
        for (double y = -gridOffsetY; y <= height; y += step) {
            gc.strokeLine(0, y, width, y);
        }
    }

    private void drawGlow(GraphicsContext gc, double cx, double cy, double radius, Color color) {
        RadialGradient gradient = new RadialGradient(
                0, 0, 0.5, 0.5, 0.5, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.color(color.getRed(), color.getGreen(), color.getBlue(), blobAlpha)),
                new Stop(1, Color.TRANSPARENT));
        gc.setFill(gradient);
        gc.fillOval(cx - radius, cy - radius, radius * 2, radius * 2);
    }

    @Override
    protected void layoutChildren() {
        canvas.setWidth(getWidth());
        canvas.setHeight(getHeight());
    }
}
