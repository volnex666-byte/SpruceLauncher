package com.spruce.ui.views;

import com.spruce.core.AppState;
import com.spruce.core.Client;
import com.spruce.core.ClientFolderScanner;
import com.spruce.core.ClientProfile;
import com.spruce.core.ClientRegistry;
import com.spruce.core.NotificationManager;
import com.spruce.core.merge.MergeProvider;
import com.spruce.core.merge.MergeRegistry;
import com.spruce.core.merge.MergeResult;
import com.spruce.ui.Icons;
import com.spruce.ui.MainWindow;
import com.spruce.ui.Theme;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class ClientsView extends VBox {
    private final AppState state;
    private final MainWindow shell;
    private final List<DetailCard> cards = new ArrayList<>();
    private final List<DetailCard> folderCards = new ArrayList<>();
    private final HBox folderRow = new HBox(24);
    private final Label folderPathLabel = new Label();
    private final VBox skippedBox = new VBox(6);
    private final Label comboComponents = new Label();
    private final Label comboStatus = new Label();
    private final Label compatLabel = new Label();
    private final Button mergeButton = new Button("Собрать объединение");

    public ClientsView(AppState state, MainWindow shell) {
        this.state = state;
        this.shell = shell;

        getStyleClass().addAll("view", "clients-view");
        setPadding(new Insets(22, 32, 24, 28));
        setSpacing(16);

        // Header
        VBox header = new VBox(2);
        Label title = new Label("Клиенты");
        title.getStyleClass().add("view-title");
        Label subtitle = new Label("Управление клиентами и объединением");
        subtitle.getStyleClass().add("view-subtitle");
        header.getChildren().addAll(title, subtitle);

        // Divider
        Region divider = new Region();
        divider.getStyleClass().add("header-divider");
        divider.setMaxWidth(Double.MAX_VALUE);

        // Cards row
        HBox cardRow = new HBox(24);
        cardRow.setAlignment(Pos.TOP_LEFT);
        for (Client client : ClientRegistry.builtIn()) {
            DetailCard card = new DetailCard(client);
            cards.add(card);
            cardRow.getChildren().add(card);
        }

        VBox folderSection = buildFolderSection();
        VBox combination = buildCombination();

        VBox content = new VBox(20, cardRow, folderSection, combination);
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("transparent-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        getChildren().addAll(header, divider, scroll);
        refresh();
    }

    private VBox buildCombination() {
        VBox panel = new VBox(12);
        panel.getStyleClass().addAll("panel", "combination-panel");

        Label title = new Label("Объединение клиентов");
        title.getStyleClass().add("panel-title");

        comboComponents.getStyleClass().add("muted-text");
        comboStatus.getStyleClass().add("muted-text");
        comboStatus.setWrapText(true);

        // Compatibility indicator
        compatLabel.setWrapText(true);
        compatLabel.getStyleClass().add("muted-text");

        mergeButton.getStyleClass().add("secondary-button");
        mergeButton.setOnAction(e -> onMerge());

        HBox mergeRow = new HBox(12, mergeButton, compatLabel);
        mergeRow.setAlignment(Pos.CENTER_LEFT);

        panel.getChildren().addAll(title, comboComponents, comboStatus, mergeRow);
        return panel;
    }

    private VBox buildFolderSection() {
        VBox panel = new VBox(10);
        panel.getStyleClass().add("panel");

        Label title = new Label("Клиенты из папки");
        title.getStyleClass().add("panel-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button rescan = new Button("Обновить");
        rescan.getStyleClass().add("secondary-button");
        rescan.setOnAction(e -> rescanFolder());

        HBox head = new HBox(12, title, spacer, rescan);
        head.setAlignment(Pos.CENTER_LEFT);

        folderPathLabel.getStyleClass().add("muted-text");
        folderPathLabel.setWrapText(true);
        folderRow.setAlignment(Pos.TOP_LEFT);

        panel.getChildren().addAll(head, folderPathLabel, folderRow, skippedBox);
        rescanFolder();
        return panel;
    }

    private void rescanFolder() {
        Path dir = Paths.get(state.config.clientsDir);
        ClientFolderScanner.Result result = ClientFolderScanner.scan(dir);

        folderPathLabel.setText("Папка: " + dir + (Files.isDirectory(dir) ? "" : "  (не найдена)"));

        for (DetailCard card : folderCards) {
            cards.remove(card);
        }
        folderCards.clear();
        folderRow.getChildren().clear();

        List<Client> registered = new ArrayList<>();
        for (ClientProfile profile : result.profiles) {
            DetailCard card = new DetailCard(profile.toClient(), profile);
            folderCards.add(card);
            cards.add(card);
            folderRow.getChildren().add(card);
            registered.add(card.client());
        }
        ClientRegistry.registerDynamic(registered);
        if (result.profiles.isEmpty()) {
            Label empty = new Label("профили не найдены");
            empty.getStyleClass().add("muted-text");
            folderRow.getChildren().add(empty);
        }

        skippedBox.getChildren().clear();
        if (!result.skipped.isEmpty()) {
            Label warn = new Label("Пропущено (обнаружены чит-модули):");
            warn.setStyle("-fx-text-fill: #D0574C; -fx-font-weight: bold;");
            skippedBox.getChildren().add(warn);
            for (ClientFolderScanner.Skipped skipped : result.skipped) {
                Label row = new Label("• " + skipped.folder + " — " + String.join(", ", skipped.markers));
                row.getStyleClass().add("muted-text");
                skippedBox.getChildren().add(row);
            }
        }

        refresh();
    }

    private void onMerge() {
        List<String> selected = state.config.selectedClients;
        if (selected.size() < 2) {
            NotificationManager.warning("Недостаточно компонентов",
                    "Выберите два клиента для объединения.");
            return;
        }
        Client first = ClientRegistry.byId(selected.get(0));
        Client second = ClientRegistry.byId(selected.get(1));
        MergeProvider provider = MergeRegistry.find(first, second);
        if (provider == null) {
            NotificationManager.warning("Объединение недоступно",
                    "Не установлены совместимые модули слияния.");
            return;
        }
        mergeButton.setDisable(true);
        Thread worker = new Thread(() -> {
            try {
                MergeResult result = provider.prepare(first, second, state.gameDir());
                Platform.runLater(() -> {
                    mergeButton.setDisable(false);
                    if (result.success) {
                        NotificationManager.success("Объединение готово", result.message);
                    } else {
                        NotificationManager.error("Ошибка объединения", result.message);
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    mergeButton.setDisable(false);
                    NotificationManager.error("Ошибка объединения", ex.getMessage());
                });
            }
        }, "spruce-merge");
        worker.setDaemon(true);
        worker.start();
    }

    public void refresh() {
        for (DetailCard card : cards) {
            card.refresh();
        }
        List<String> selected = state.config.selectedClients;
        if (selected.isEmpty()) {
            comboComponents.setText("Компоненты: не выбраны");
        } else {
            List<String> names = new ArrayList<>();
            for (String id : selected) {
                Client client = ClientRegistry.byId(id);
                names.add(client == null ? id : client.name);
            }
            comboComponents.setText("Компоненты: " + String.join(" + ", names));
        }

        if (selected.size() < 2) {
            comboStatus.setText("Выберите два клиента. Объединение требует совместимых модулей.");
            compatLabel.setText("");
            compatLabel.getStyleClass().removeAll("compat-ok", "compat-warn", "compat-error");
            mergeButton.setDisable(true);
        } else {
            MergeProvider provider = MergeRegistry.find(
                    ClientRegistry.byId(selected.get(0)),
                    ClientRegistry.byId(selected.get(1)));
            if (provider == null) {
                comboStatus.setText("Клиенты несовместимы: совместимые модули объединения не найдены.");
                compatLabel.setText("✕ Несовместимо");
                compatLabel.getStyleClass().removeAll("compat-ok", "compat-warn");
                if (!compatLabel.getStyleClass().contains("compat-error")) {
                    compatLabel.getStyleClass().add("compat-error");
                }
                mergeButton.setDisable(true);
            } else {
                comboStatus.setText("Доступен провайдер объединения: " + provider.displayName());
                compatLabel.setText("✓ Совместимо");
                compatLabel.getStyleClass().removeAll("compat-warn", "compat-error");
                if (!compatLabel.getStyleClass().contains("compat-ok")) {
                    compatLabel.getStyleClass().add("compat-ok");
                }
                mergeButton.setDisable(false);
            }
        }
    }

    public void playEntrance() {
        if (!shell.animationsEnabled()) {
            for (DetailCard card : cards) {
                card.setOpacity(1);
                card.setTranslateY(0);
            }
            return;
        }
        int index = 0;
        for (DetailCard card : cards) {
            card.setOpacity(0);
            card.setTranslateY(18);
            FadeTransition fade = new FadeTransition(Duration.millis(340), card);
            fade.setDelay(Duration.millis(index * 100));
            fade.setToValue(1);
            TranslateTransition move = new TranslateTransition(Duration.millis(340), card);
            move.setDelay(Duration.millis(index * 100));
            move.setToY(0);
            new ParallelTransition(fade, move).play();
            index++;
        }
    }

    public void setAnimationsEnabled(boolean enabled) {
        for (DetailCard card : cards) {
            card.setAnimationsEnabled(enabled);
        }
    }

    // ── Detail Card ───────────────────────────────────────────────────────────
    private final class DetailCard extends VBox {
        private final Client client;
        private final ClientProfile profile;
        private final Label statusChip = new Label();
        private final Region indicator = new Region();
        private final Button selectButton = new Button();
        private final Label pathLabel = new Label();
        private boolean animations = true;

        DetailCard(Client client) {
            this(client, null);
        }

        DetailCard(Client client, ClientProfile profile) {
            this.client = client;
            this.profile = profile;
            getStyleClass().add("client-detail-card");
            setSpacing(12);
            setPrefWidth(310);

            // Icon wrap
            StackPane iconWrap = new StackPane(
                    Icons.icon(Icons.byKey(client.iconKey), 1.65, Color.web(client.accent)));
            iconWrap.getStyleClass().add("client-icon-wrap");
            iconWrap.setMinSize(66, 66);
            iconWrap.setPrefSize(66, 66);

            // Headings
            Label name = new Label(client.name);
            name.getStyleClass().add("card-title");
            Label version = new Label(client.version);
            version.getStyleClass().add("card-version");
            VBox headings = new VBox(2, name, version);

            // Availability indicator dot
            indicator.setMinSize(12, 12);
            indicator.setPrefSize(12, 12);
            indicator.setMaxSize(12, 12);
            indicator.getStyleClass().add("select-indicator");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            HBox top = new HBox(14, iconWrap, headings, spacer, indicator);
            top.setAlignment(Pos.CENTER_LEFT);

            // Description
            Label description = new Label(client.description);
            description.getStyleClass().add("card-description");
            description.setWrapText(true);

            // Status chip
            statusChip.getStyleClass().add("status-chip");

            // Action buttons
            selectButton.getStyleClass().add("secondary-button");
            selectButton.setOnAction(e -> {
                state.toggleClient(client.id);
                refresh();
                shell.state().statusProperty().set(client.name + " обновлён");
                // Also refresh the home view combo status
                shell.refreshHome();
            });

            pathLabel.getStyleClass().add("muted-text");
            pathLabel.setWrapText(true);

            HBox actions;
            if (profile == null) {
                Button connect = new Button("Подключить файлы...");
                connect.getStyleClass().add("secondary-button");
                connect.setOnAction(e -> chooseFiles());
                actions = new HBox(10, selectButton, connect);
            } else {
                actions = new HBox(10, selectButton);
            }
            actions.setAlignment(Pos.CENTER_LEFT);

            // Accent bar
            Region accentBar = new Region();
            accentBar.setMaxWidth(Double.MAX_VALUE);
            accentBar.setPrefHeight(2);
            accentBar.setStyle("-fx-background-color: linear-gradient(to right,"
                    + " rgba(57,211,83,0.48), rgba(30,143,66,0.08));"
                    + "-fx-background-radius: 2;");

            getChildren().addAll(top, description, statusChip, actions, pathLabel, accentBar);

            setOnMouseEntered(e -> animate(-4));
            setOnMouseExited(e -> animate(0));
        }

        private void chooseFiles() {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Выберите файл клиента " + client.name);
            chooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("JAR файлы", "*.jar"),
                    new FileChooser.ExtensionFilter("Все файлы", "*.*"));
            File file = chooser.showOpenDialog(shell.stage());
            if (file != null) {
                Path path = file.toPath();
                state.setClientPath(client.id, path.toString());
                NotificationManager.success("Файл подключён", path.getFileName().toString());
                refresh();
            }
        }

        private void animate(double targetY) {
            if (!animations) {
                setTranslateY(targetY);
                return;
            }
            TranslateTransition t = new TranslateTransition(Duration.millis(160), this);
            t.setToY(targetY);
            t.play();
        }

        Client client() {
            return client;
        }

        void setAnimationsEnabled(boolean enabled) {
            this.animations = enabled;
        }

        void refresh() {
            boolean selected = state.isClientSelected(client.id);
            if (selected) {
                if (!getStyleClass().contains("selected")) getStyleClass().add("selected");
                indicator.setStyle("-fx-background-radius: 6; -fx-background-color: "
                        + client.accent + ";"
                        + "-fx-effect: dropshadow(gaussian, rgba(57,211,83,0.85), 8, 0.3, 0, 0);");
                statusChip.setText("Выбран");
                selectButton.setText("Убрать");
            } else {
                getStyleClass().remove("selected");
                indicator.setStyle("-fx-background-radius: 6; -fx-background-color: rgba(255,255,255,0.13);");
                statusChip.setText("Не выбран");
                selectButton.setText("Добавить");
            }
            if (profile != null) {
                pathLabel.setText("Папка: " + profile.dir);
            } else {
                String path = state.clientPath(client.id);
                pathLabel.setText(path == null ? "Локальные файлы не подключены" : "Файл: " + path);
            }
        }
    }
}
