package com.spruce.ui.views;

import com.spruce.core.AppState;
import com.spruce.core.Config;
import com.spruce.core.MinecraftVersion;
import com.spruce.core.NotificationManager;
import com.spruce.core.VersionManager;
import com.spruce.ui.MainWindow;
import com.spruce.ui.components.ToggleSwitch;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

public class SettingsView extends VBox {
    private final AppState state;
    private final MainWindow shell;

    private final ComboBox<MinecraftVersion> versionCombo = new ComboBox<>();
    private final Spinner<Integer> maxMemory =
            new Spinner<>(Config.MIN_MEMORY, Config.MAX_MEMORY, 4096, 256);
    private final Spinner<Integer> minMemory =
            new Spinner<>(Config.MIN_MEMORY, Config.MAX_MEMORY, 1024, 256);
    private final TextField gameDirField = new TextField();
    private final TextField javaField = new TextField();
    private final ToggleSwitch particlesToggle = new ToggleSwitch();
    private final ToggleSwitch glowToggle = new ToggleSwitch();
    private final Label filesResult = new Label("Проверка не выполнялась.");
    private final Segment themeSegment;
    private final Segment animationSegment;

    private boolean loading;

    public SettingsView(AppState state, MainWindow shell) {
        this.state = state;
        this.shell = shell;

        getStyleClass().addAll("view", "settings-view");
        setPadding(new Insets(24, 34, 28, 30));
        setSpacing(16);

        VBox header = new VBox(2);
        Label title = new Label("Настройки");
        title.getStyleClass().add("view-title");
        Label subtitle = new Label("Внешний вид, Minecraft и обслуживание");
        subtitle.getStyleClass().add("view-subtitle");
        header.getChildren().addAll(title, subtitle);

        themeSegment = new Segment(state.config.theme,
                new String[][]{{"graphite", "Графит"}, {"obsidian", "Обсидиан"}, {"light", "Светлая"}},
                value -> {
                    state.config.theme = value;
                    if (!"light".equals(value)) {
                        state.config.darkTheme = value;
                    }
                    state.config.save();
                    shell.applyVisualSettings();
                    NotificationManager.success("Настройки", "Тема обновлена.");
                });
        animationSegment = new Segment(state.config.animationIntensity,
                new String[][]{{"normal", "Обычная"}, {"reduced", "Уменьшенная"}, {"off", "Отключена"}},
                value -> {
                    state.config.animationIntensity = value;
                    state.config.save();
                    shell.applyVisualSettings();
                    NotificationManager.success("Настройки", "Интенсивность анимаций обновлена.");
                });

        VBox body = new VBox(18,
                appearanceSection(),
                minecraftSection(),
                maintenanceSection());
        body.getStyleClass().add("settings-body");

        // Divider
        javafx.scene.layout.Region divider = new javafx.scene.layout.Region();
        divider.getStyleClass().add("header-divider");
        divider.setMaxWidth(Double.MAX_VALUE);
        VBox.setMargin(divider, new javafx.geometry.Insets(0, 0, 4, 0));

        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("transparent-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        getChildren().addAll(header, divider, scroll);

        applyConfigToControls();
    }

    private VBox appearanceSection() {
        VBox section = section("Внешний вид");

        particlesToggle.selectedProperty().addListener((o, a, b) -> {
            if (loading) {
                return;
            }
            state.config.particles = b;
            state.config.save();
            shell.applyVisualSettings();
            toast();
        });
        glowToggle.selectedProperty().addListener((o, a, b) -> {
            if (loading) {
                return;
            }
            state.config.backgroundGlow = b;
            state.config.save();
            shell.applyVisualSettings();
            toast();
        });

        section.getChildren().addAll(
                row("Тема оформления", "Тёмная графитовая, обсидиановая или светлая палитра.", themeSegment),
                row("Интенсивность анимаций", "Влияет на переходы и декоративные эффекты.", animationSegment),
                row("Фоновые частицы", "Ненавязчивые частицы фона.", particlesToggle),
                row("Световые эффекты", "Мягкие неоновые световые пятна фона.", glowToggle));
        return section;
    }

    private VBox minecraftSection() {
        VBox section = section("Minecraft");

        versionCombo.setItems(state.availableVersions());
        versionCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(MinecraftVersion version) {
                return version == null ? "Не выбрана" : version.id + " (" + version.type + ")";
            }

            @Override
            public MinecraftVersion fromString(String string) {
                return null;
            }
        });
        versionCombo.setPrefWidth(260);
        versionCombo.valueProperty().addListener((o, a, b) -> {
            if (loading || b == null) {
                return;
            }
            state.selectVersion(b);
            toast();
        });

        Button refresh = new Button("Обновить");
        refresh.getStyleClass().add("secondary-button");
        refresh.setOnAction(e -> {
            state.refreshVersions();
            versionCombo.setValue(state.selectedVersion());
            NotificationManager.info("Версии", "Найдено версий: " + state.availableVersions().size());
        });

        HBox versionRow = new HBox(10, versionCombo, refresh);
        versionRow.setAlignment(Pos.CENTER_LEFT);

        maxMemory.setEditable(true);
        minMemory.setEditable(true);
        maxMemory.valueProperty().addListener((o, a, b) -> onMemoryChanged());
        minMemory.valueProperty().addListener((o, a, b) -> onMemoryChanged());

        HBox memoryRow = new HBox(10,
                new Label("Мин:"), minMemory, new Label("Макс:"), maxMemory,
                new Label("МБ"));
        memoryRow.setAlignment(Pos.CENTER_LEFT);

        gameDirField.setPrefWidth(320);
        Button browseDir = new Button("Обзор...");
        browseDir.getStyleClass().add("secondary-button");
        browseDir.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Каталог игровых файлов");
            File initial = new File(state.config.gameDir == null || state.config.gameDir.isBlank()
                    ? state.gameDir().toString() : state.config.gameDir);
            if (initial.isDirectory()) {
                chooser.setInitialDirectory(initial);
            }
            File dir = chooser.showDialog(shell.stage());
            if (dir != null) {
                state.config.gameDir = dir.getAbsolutePath();
                state.config.save();
                gameDirField.setText(state.config.gameDir);
                state.refreshVersions();
                versionCombo.setValue(state.selectedVersion());
                NotificationManager.success("Настройки", "Каталог игры обновлён.");
            }
        });
        HBox gameDirRow = new HBox(10, gameDirField, browseDir);
        gameDirRow.setAlignment(Pos.CENTER_LEFT);

        javaField.setPrefWidth(320);
        Button browseJava = new Button("Обзор...");
        browseJava.getStyleClass().add("secondary-button");
        browseJava.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Выберите java");
            chooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Исполняемый файл", "*.exe"),
                    new FileChooser.ExtensionFilter("Все файлы", "*.*"));
            File file = chooser.showOpenDialog(shell.stage());
            if (file != null) {
                state.config.javaPath = file.getAbsolutePath();
                state.config.save();
                javaField.setText(state.config.javaPath);
                NotificationManager.success("Настройки", "Путь к Java обновлён.");
            }
        });
        HBox javaRow = new HBox(10, javaField, browseJava);
        javaRow.setAlignment(Pos.CENTER_LEFT);

        section.getChildren().addAll(
                row("Версия Minecraft", "Установленные версии в каталоге игры.", versionRow),
                row("Память JVM (МБ)", "Минимум и максимум выделяемой памяти.", memoryRow),
                row("Каталог игровых файлов", "Расположение .minecraft.", gameDirRow),
                row("Java", "Путь к java.exe. Пусто — определяется автоматически.", javaRow));
        return section;
    }

    private VBox maintenanceSection() {
        VBox section = section("Дополнительно");

        Button check = new Button("Проверить игровые файлы");
        check.getStyleClass().add("secondary-button");
        check.setOnAction(e -> checkFiles());

        Button reset = new Button("Сбросить настройки");
        reset.getStyleClass().add("danger-button");
        reset.setOnAction(e -> resetSettings());

        filesResult.getStyleClass().add("muted-text");
        filesResult.setWrapText(true);

        Label about = new Label("SpruceLauncher " + Config.VERSION
                + " — JavaFX, локальный запуск без онлайн-авторизации.");
        about.getStyleClass().add("muted-text");
        about.setWrapText(true);

        HBox actions = new HBox(10, check, reset);
        actions.setAlignment(Pos.CENTER_LEFT);

        section.getChildren().addAll(actions, filesResult, about);
        return section;
    }

    private void onMemoryChanged() {
        if (loading) {
            return;
        }
        Integer maxValue = maxMemory.getValue();
        Integer minValue = minMemory.getValue();
        if (maxValue == null || minValue == null) {
            return;
        }
        if (minValue > maxValue) {
            loading = true;
            minMemory.getValueFactory().setValue(maxValue);
            loading = false;
            minValue = maxValue;
        }
        state.config.memoryMb = maxValue;
        state.config.minMemoryMb = minValue;
        state.config.save();
        toast();
    }

    private void checkFiles() {
        Path gameDir = state.gameDir();
        VersionManager manager = state.versions;
        StringBuilder report = new StringBuilder();
        boolean ok = true;

        if (Files.isDirectory(gameDir)) {
            report.append("Каталог игры: найден. ");
        } else {
            report.append("Каталог игры: НЕ найден (").append(gameDir).append("). ");
            ok = false;
        }
        int count = state.availableVersions().size();
        report.append("Версий: ").append(count).append(". ");
        if (count == 0) {
            ok = false;
        }
        report.append(manager.librariesPresent(gameDir) ? "Библиотеки: ок. " : "Библиотеки: отсутствуют. ");
        report.append(manager.assetsPresent(gameDir) ? "Ресурсы: ок. " : "Ресурсы: отсутствуют. ");

        filesResult.setText(report.toString());
        if (ok) {
            NotificationManager.success("Проверка", "Игровые файлы в порядке.");
        } else {
            NotificationManager.warning("Проверка", "Обнаружены проблемы с игровыми файлами.");
        }
    }

    private void resetSettings() {
        Config defaults = new Config();
        defaults.normalize();
        state.config.theme = defaults.theme;
        state.config.darkTheme = defaults.darkTheme;
        state.config.animationIntensity = defaults.animationIntensity;
        state.config.particles = defaults.particles;
        state.config.backgroundGlow = defaults.backgroundGlow;
        state.config.memoryMb = defaults.memoryMb;
        state.config.minMemoryMb = defaults.minMemoryMb;
        state.config.username = defaults.username;
        state.config.javaPath = defaults.javaPath;
        state.config.save();
        state.refreshVersions();
        applyConfigToControls();
        shell.applyVisualSettings();
        NotificationManager.success("Сброс", "Настройки восстановлены по умолчанию.");
    }

    private void applyConfigToControls() {
        loading = true;
        themeSegment.select(state.config.theme);
        animationSegment.select(state.config.animationIntensity);
        particlesToggle.setSelected(state.config.particles);
        glowToggle.setSelected(state.config.backgroundGlow);
        minMemory.getValueFactory().setValue(state.config.minMemoryMb);
        maxMemory.getValueFactory().setValue(state.config.memoryMb);
        versionCombo.setValue(state.selectedVersion());
        gameDirField.setText(state.config.gameDir == null || state.config.gameDir.isBlank()
                ? state.gameDir().toString() : state.config.gameDir);
        javaField.setText(state.config.javaPath == null ? "" : state.config.javaPath);
        loading = false;
    }

    private void toast() {
        NotificationManager.success("Настройки", "Параметры сохранены.");
    }

    private VBox section(String title) {
        VBox section = new VBox(12);
        section.getStyleClass().add("panel");
        Label label = new Label(title);
        label.getStyleClass().add("panel-title");
        section.getChildren().add(label);
        return section;
    }

    private HBox row(String title, String description, Node control) {
        VBox text = new VBox(2);
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("setting-title");
        Label descriptionLabel = new Label(description);
        descriptionLabel.getStyleClass().add("setting-description");
        descriptionLabel.setWrapText(true);
        text.getChildren().addAll(titleLabel, descriptionLabel);
        HBox.setHgrow(text, Priority.ALWAYS);

        HBox row = new HBox(16, text, control);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("setting-row");
        return row;
    }

    public void setAnimationsEnabled(boolean enabled) {
        particlesToggle.setAnimationsEnabled(enabled);
        glowToggle.setAnimationsEnabled(enabled);
    }

    public void stop() {
    }

    private final class Segment extends HBox {
        private final Map<String, Button> buttons = new LinkedHashMap<>();

        Segment(String current, String[][] options, Consumer<String> onChange) {
            getStyleClass().add("segment");
            setSpacing(4);
            for (String[] option : options) {
                Button button = new Button(option[1]);
                button.getStyleClass().add("segment-button");
                button.setOnAction(e -> {
                    onChange.accept(option[0]);
                    select(option[0]);
                });
                buttons.put(option[0], button);
                getChildren().add(button);
            }
            select(current);
        }

        void select(String key) {
            for (Map.Entry<String, Button> entry : buttons.entrySet()) {
                Button button = entry.getValue();
                if (entry.getKey().equals(key)) {
                    if (!button.getStyleClass().contains("active")) {
                        button.getStyleClass().add("active");
                    }
                } else {
                    button.getStyleClass().remove("active");
                }
            }
        }
    }
}
