package com.spruce.ui.views;

import com.spruce.core.AppState;
import com.spruce.core.Client;
import com.spruce.core.ClientRegistry;
import com.spruce.core.Config;
import com.spruce.core.LaunchResult;
import com.spruce.core.MinecraftVersion;
import com.spruce.core.NotificationManager;
import com.spruce.core.merge.MergeProvider;
import com.spruce.core.merge.MergeRegistry;
import com.spruce.ui.Icons;
import com.spruce.ui.MainWindow;
import com.spruce.ui.Theme;
import com.spruce.ui.components.GlowButton;
import com.spruce.ui.components.MergeField;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;

import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class HomeView extends VBox {
    private final AppState state;
    private final MainWindow shell;
    private final MergeField mergeField;
    private final GlowButton playButton = new GlowButton("ИГРАТЬ");
    private final Label statusLabel = new Label("Готово к запуску");
    private final Label comboLabel = new Label();
    private final ProgressBar progress = new ProgressBar(0);

    // Info panel labels
    private final Label infoVersionVal = new Label("—");
    private final Label infoFilesVal = new Label("—");
    private final Label infoClientVal = new Label("—");
    private final Label infoLastLaunchVal = new Label("не было");

    // Status bar
    private final Region netDot = new Region();
    private final Label netLabel = new Label("Проверка сети...");
    private final Label pingLabel = new Label("Пинг: —");
    private ScheduledExecutorService netExecutor;

    private SVGPath playIcon;
    private PrintWriter logWriter;

    public HomeView(AppState state, MainWindow shell) {
        this.state = state;
        this.shell = shell;
        this.mergeField = new MergeField(state);

        getStyleClass().addAll("view", "home-view");
        setPadding(new Insets(22, 32, 24, 28));
        setSpacing(0);

        getChildren().addAll(buildCenter(), buildFooter(), buildStatusBar());

        mergeField.setOnSelectionChanged(this::updateComboLabel);
        state.selectedVersionProperty().addListener((o, a, b) -> updateVersionChip());
        updateVersionChip();
        updateComboLabel();
        updateLastLaunch();
    }

    // ── Center ──────────────────────────────────────────────────────────────
    private StackPane buildCenter() {
        // Merge field (two cards + animated core)
        StackPane centerStack = new StackPane(mergeField);
        centerStack.setAlignment(Pos.CENTER);
        centerStack.getStyleClass().add("merge-center");
        VBox.setVgrow(centerStack, Priority.ALWAYS);

        // Combo label at bottom of center
        comboLabel.getStyleClass().add("combo-label");
        StackPane.setAlignment(comboLabel, Pos.BOTTOM_CENTER);
        StackPane.setMargin(comboLabel, new Insets(0, 0, 8, 0));

        // Info panel at top-right of center
        VBox infoPanel = buildInfoPanel();
        StackPane.setAlignment(infoPanel, Pos.TOP_RIGHT);
        StackPane.setMargin(infoPanel, new Insets(6, 6, 0, 0));

        centerStack.getChildren().addAll(comboLabel, infoPanel);
        return centerStack;
    }

    private VBox buildInfoPanel() {
        VBox panel = new VBox(6);
        panel.getStyleClass().add("home-info-panel");
        panel.setMaxWidth(200);

        panel.getChildren().addAll(
                infoRow("Версия", infoVersionVal),
                infoRow("Файлы", infoFilesVal),
                infoRow("Клиент", infoClientVal),
                infoRow("Запуск", infoLastLaunchVal)
        );
        return panel;
    }

    private HBox infoRow(String key, Label val) {
        Label keyLbl = new Label(key + ":");
        keyLbl.getStyleClass().add("home-info-label");
        keyLbl.setMinWidth(52);
        val.getStyleClass().add("home-info-value");
        val.setMaxWidth(120);
        HBox row = new HBox(6, keyLbl, val);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    // ── Footer ──────────────────────────────────────────────────────────────
    private VBox buildFooter() {
        VBox footer = new VBox(8);
        footer.setAlignment(Pos.CENTER);
        footer.setPadding(new Insets(12, 0, 0, 0));

        playButton.getStyleClass().add("play-button");
        playIcon = Icons.icon(Icons.PLAY, 1.1, Color.web("#080D09"));
        playButton.setGraphic(playIcon);
        playButton.setOnAction(e -> onPlay());
        playButton.setPulsing(true);
        playButton.disabledProperty().addListener((o, a, b) -> playButton.setPulsing(!b));

        statusLabel.getStyleClass().add("status-label");

        progress.getStyleClass().add("launch-progress");
        progress.setPrefWidth(400);
        progress.setMaxWidth(400);
        progress.setVisible(false);
        progress.setManaged(false);

        // Footer info line
        Label footerInfo = new Label("SpruceLauncher " + Config.VERSION + "  ·  Локальный режим");
        footerInfo.getStyleClass().add("home-info-label");
        footerInfo.setStyle("-fx-font-size: 10.5px;");

        footer.getChildren().addAll(playButton, statusLabel, progress, footerInfo);
        return footer;
    }

    // ── Status bar ──────────────────────────────────────────────────────────
    private HBox buildStatusBar() {
        netDot.getStyleClass().addAll("status-dot", "pending");
        netDot.setMinSize(7, 7);
        netDot.setPrefSize(7, 7);
        netDot.setMaxSize(7, 7);
        netLabel.getStyleClass().add("status-chip-text");
        pingLabel.getStyleClass().add("status-chip-text");

        HBox netChip = new HBox(7, netDot, netLabel);
        netChip.setAlignment(Pos.CENTER_LEFT);
        netChip.getStyleClass().add("status-chip");

        HBox pingChip = new HBox(7, pingLabel);
        pingChip.setAlignment(Pos.CENTER_LEFT);
        pingChip.getStyleClass().add("status-chip");

        HBox bar = new HBox(10, netChip, pingChip);
        bar.setAlignment(Pos.CENTER);
        bar.getStyleClass().add("status-bar");
        bar.setPadding(new Insets(10, 0, 0, 0));

        netExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "spruce-net-probe");
            thread.setDaemon(true);
            return thread;
        });
        netExecutor.scheduleWithFixedDelay(this::probeNetwork, 1, 15, TimeUnit.SECONDS);
        return bar;
    }

    private void probeNetwork() {
        long start = System.nanoTime();
        boolean online = false;
        long millis = 0;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("api.mojang.com", 443), 2500);
            millis = (System.nanoTime() - start) / 1_000_000;
            online = true;
        } catch (Exception ignored) {
        }
        final boolean reachable = online;
        final long ping = millis;
        Platform.runLater(() -> {
            netDot.getStyleClass().removeAll("ok", "offline", "pending");
            netLabel.getStyleClass().removeAll("ok", "offline");
            if (reachable) {
                netDot.getStyleClass().add("ok");
                netLabel.getStyleClass().add("ok");
                netLabel.setText("Сеть: онлайн");
                pingLabel.setText("Пинг: " + ping + " мс");
            } else {
                netDot.getStyleClass().add("offline");
                netLabel.getStyleClass().add("offline");
                netLabel.setText("Сеть: офлайн");
                pingLabel.setText("Пинг: —");
            }
        });
    }

    // ── Launch logic ─────────────────────────────────────────────────────────
    private void onPlay() {
        MinecraftVersion version = state.selectedVersion();
        if (version == null) {
            NotificationManager.warning("Версия не выбрана",
                    "Откройте настройки и выберите установленную версию Minecraft.");
            statusLabel.setText("Версия Minecraft не выбрана");
            return;
        }
        if (!java.nio.file.Files.isDirectory(state.gameDir())) {
            NotificationManager.error("Каталог игры не найден",
                    "Проверьте путь к игровым файлам в настройках.");
            statusLabel.setText("Каталог игры не найден");
            return;
        }
        startLaunch(version);
    }

    private void startLaunch(MinecraftVersion version) {
        playButton.setDisable(true);
        progress.setVisible(true);
        progress.setManaged(true);
        progress.setProgress(0);
        statusLabel.setText("Подготовка " + version.id + "...");

        openLog();

        Thread worker = new Thread(() -> {
            LaunchResult result = state.launcher.launch(version, state.config,
                    this::onLog,
                    value -> Platform.runLater(() -> progress.setProgress(value)));
            Platform.runLater(() -> onResult(result));
        }, "spruce-launch");
        worker.setDaemon(true);
        worker.start();
    }

    private void onLog(String line) {
        if (logWriter != null) logWriter.println(line);
        if (line != null
                && (line.toLowerCase().contains("exception")
                || line.toLowerCase().contains("error")
                || line.toLowerCase().contains("не найден"))
                && line.length() < 120) {
            Platform.runLater(() -> statusLabel.setText(line));
        }
    }

    private void onResult(LaunchResult result) {
        closeLog();
        playButton.setDisable(false);
        progress.setVisible(false);
        progress.setManaged(false);

        if (result.started) {
            statusLabel.setText("Minecraft запущен");
            state.statusProperty().set("Minecraft запущен");
            NotificationManager.success("Запуск",
                    "Minecraft запущен (версия " + state.selectedVersion().id + ").");
            state.config.lastLaunchAt = System.currentTimeMillis();
            state.config.lastLaunchVersion = state.selectedVersion().id;
            state.config.save();
            updateLastLaunch();
            monitorProcess(result.process);
        } else {
            statusLabel.setText("Ошибка запуска");
            NotificationManager.error("Не удалось запустить", result.error);
        }
    }

    private void monitorProcess(Process process) {
        Thread watcher = new Thread(() -> {
            try {
                int code = process.waitFor();
                Platform.runLater(() -> {
                    statusLabel.setText(code == 0 ? "Игра закрыта" : "Игра завершилась (код " + code + ")");
                    NotificationManager.info("Minecraft закрыт",
                            "Процесс завершился с кодом " + code + ".");
                });
            } catch (Exception ignored) {
            }
        }, "spruce-process-watch");
        watcher.setDaemon(true);
        watcher.start();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────
    private void openLog() {
        try {
            String name = "launch-" + System.currentTimeMillis() + ".log";
            logWriter = new PrintWriter(Files.newBufferedWriter(
                    com.spruce.core.AppPaths.logsDir().resolve(name), StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            logWriter = null;
        }
    }

    private void closeLog() {
        if (logWriter != null) {
            logWriter.flush();
            logWriter.close();
            logWriter = null;
        }
    }

    private void updateVersionChip() {
        MinecraftVersion version = state.selectedVersion();
        infoVersionVal.setText(version == null ? "не выбрана" : version.id);
        // Quick file check
        if (version != null && java.nio.file.Files.isDirectory(state.gameDir())) {
            infoFilesVal.setText("найдены");
            infoFilesVal.setStyle("-fx-text-fill: #39D353; -fx-font-weight: bold;");
        } else {
            infoFilesVal.setText("не найдены");
            infoFilesVal.setStyle("-fx-text-fill: #D0574C; -fx-font-weight: bold;");
        }
    }

    private void updateComboLabel() {
        var clients = state.config.selectedClients;
        if (clients.isEmpty()) {
            comboLabel.setText("Выберите один или два клиента для комбинации");
            infoClientVal.setText("не выбран");
            infoClientVal.setStyle("-fx-text-fill: -sl-muted;");
            applyPlayAccent(null);
            return;
        }
        if (clients.size() == 1) {
            String name = getClientName(clients.get(0));
            comboLabel.setText("Выбран клиент " + name + ". Добавьте второй для объединения.");
            infoClientVal.setText(name);
            infoClientVal.setStyle("-fx-text-fill: #39D353; -fx-font-weight: bold;");
            applyPlayAccent(clients.get(0));
            return;
        }
        applyPlayAccent(null);
        MergeProvider provider = MergeRegistry.find(
                com.spruce.core.ClientRegistry.byId(clients.get(0)),
                com.spruce.core.ClientRegistry.byId(clients.get(1)));
        String c1 = getClientName(clients.get(0));
        String c2 = getClientName(clients.get(1));
        infoClientVal.setText(c1 + " + " + c2);
        infoClientVal.setStyle("-fx-text-fill: #39D353; -fx-font-weight: bold;");
        if (provider != null) {
            comboLabel.setText("Комбинация готова: " + provider.displayName());
        } else {
            comboLabel.setText("Объединение недоступно: не зарегистрированы совместимые модули.");
        }
    }

    private String getClientName(String id) {
        Client c = ClientRegistry.byId(id);
        return c == null ? id : c.name;
    }

    private void applyPlayAccent(String clientId) {
        Client client = clientId == null ? null : ClientRegistry.byId(clientId);
        if (client == null || client.accent == null || client.accent.isBlank()) {
            playButton.setStyle(null);
            playButton.setGlowColor(Color.web("#39D353", 0.55));
            if (playIcon != null) {
                playIcon.setFill(Color.web("#080D09"));
            }
            return;
        }
        Color accent;
        try {
            accent = Color.web(client.accent);
        } catch (Exception e) {
            accent = Theme.ACCENT;
        }
        String hex = toHex(accent);
        double luminance = 0.2126 * accent.getRed() + 0.7152 * accent.getGreen() + 0.0722 * accent.getBlue();
        String textColor = luminance > 0.55 ? "#0C140D" : "#F4F9F5";
        playButton.setStyle("-fx-background-color: linear-gradient(to right, derive(" + hex + ", 24%), " + hex + ");"
                + " -fx-text-fill: " + textColor + ";");
        playButton.setGlowColor(Color.color(accent.getRed(), accent.getGreen(), accent.getBlue(), 0.6));
        if (playIcon != null) {
            playIcon.setFill(Color.web(textColor));
        }
    }

    private static String toHex(Color color) {
        return String.format("#%02X%02X%02X",
                (int) Math.round(color.getRed() * 255),
                (int) Math.round(color.getGreen() * 255),
                (int) Math.round(color.getBlue() * 255));
    }

    private void updateLastLaunch() {
        long stamp = state.config.lastLaunchAt;
        if (stamp <= 0) {
            infoLastLaunchVal.setText("не было");
            infoLastLaunchVal.setStyle("-fx-text-fill: -sl-muted;");
            return;
        }
        String value = DateTimeFormatter.ofPattern("dd.MM HH:mm")
                .withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(stamp));
        if (state.config.lastLaunchVersion != null && !state.config.lastLaunchVersion.isBlank()) {
            value += " · " + state.config.lastLaunchVersion;
        }
        infoLastLaunchVal.setText(value);
        infoLastLaunchVal.setStyle("-fx-text-fill: #39D353; -fx-font-weight: bold;");
    }

    // ── Public API ────────────────────────────────────────────────────────────
    public void setAnimationsEnabled(boolean enabled) {
        mergeField.setAnimationsEnabled(enabled);
        playButton.setAnimationsEnabled(enabled);
    }

    public void refresh() {
        mergeField.refresh();
        updateVersionChip();
        updateComboLabel();
    }

    public void stop() {
        mergeField.stop();
        closeLog();
        if (netExecutor != null) {
            netExecutor.shutdownNow();
        }
    }
}
