package com.spruce.ui;

import com.spruce.core.AppState;
import com.spruce.core.Config;
import com.spruce.core.NotificationManager;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;
import javafx.stage.Popup;
import javafx.util.Duration;

import java.net.URL;
import java.util.List;
import java.util.Optional;

public class TopBar extends HBox {
    private final AppState state;
    private final MainWindow shell;

    private final Button bellButton = new Button();
    private final Label badge = new Label();
    private final Button themeButton = new Button();
    private final HBox profileChip = new HBox(8);
    private final Label userLabel = new Label();
    private final Label avatarLetter = new Label();

    private Popup notifPopup;
    private boolean popupOpen = false;
    private int unread = 0;
    private boolean animations = true;

    public TopBar(AppState state, MainWindow shell) {
        this.state = state;
        this.shell = shell;

        getStyleClass().add("topbar");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(10);
        setPadding(new Insets(10, 22, 10, 22));

        getChildren().addAll(buildBrand(), spacer(), buildBell(), buildThemeButton(), buildProfileChip());

        NotificationManager.setListener(() -> Platform.runLater(() -> {
            unread++;
            updateBadge();
        }));
        updateBadge();
        updateThemeIcon();
        refreshProfile();
    }

    // ── Brand ───────────────────────────────────────────────────────────────
    private HBox buildBrand() {
        StackPane logoWrap = new StackPane(Icons.icon(Icons.SPRUCE, 0.95, Theme.ACCENT));
        logoWrap.getStyleClass().add("topbar-logo");
        logoWrap.setMinSize(30, 30);
        logoWrap.setPrefSize(30, 30);

        DropShadow glow = new DropShadow(4, Color.web("#39D353", 0.35));
        logoWrap.setEffect(glow);

        Label name = new Label("SpruceLauncher");
        name.getStyleClass().add("topbar-title");

        Label version = new Label("v" + Config.VERSION);
        version.getStyleClass().add("topbar-version");

        HBox brand = new HBox(9, logoWrap, name, version);
        brand.setAlignment(Pos.CENTER_LEFT);
        brand.setOnMouseEntered(e -> {
            animateScale(brand, 1.045);
            animateGlow(glow, 16);
        });
        brand.setOnMouseExited(e -> {
            animateScale(brand, 1.0);
            animateGlow(glow, 4);
        });
        return brand;
    }

    private Region spacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    // ── Notifications ───────────────────────────────────────────────────────
    private StackPane buildBell() {
        bellButton.getStyleClass().add("icon-button");
        bellButton.setGraphic(Icons.icon(Icons.BELL, 0.95, Theme.MUTED));
        Tooltip.install(bellButton, new Tooltip("Уведомления"));
        bellButton.setOnAction(e -> toggleNotifications());

        badge.getStyleClass().add("topbar-badge");
        badge.setMinSize(16, 16);
        badge.setAlignment(Pos.CENTER);
        badge.setMouseTransparent(true);
        badge.setTranslateX(3);
        badge.setTranslateY(-3);

        StackPane wrap = new StackPane(bellButton, badge);
        StackPane.setAlignment(badge, Pos.TOP_RIGHT);
        return wrap;
    }

    private void updateBadge() {
        badge.setText(unread > 9 ? "9+" : String.valueOf(unread));
        boolean show = unread > 0;
        badge.setVisible(show);
        badge.setManaged(show);
    }

    private void toggleNotifications() {
        if (popupOpen && notifPopup != null) {
            notifPopup.hide();
            return;
        }
        notifPopup = new Popup();
        notifPopup.setAutoHide(true);
        notifPopup.setHideOnEscape(true);

        VBox panel = new VBox(10);
        panel.getStyleClass().addAll("root-shell", Theme.classFor(state.config.theme), "notif-popup");
        URL stylesheet = getClass().getResource("/com/spruce/styles.css");
        if (stylesheet != null) {
            panel.getStylesheets().add(stylesheet.toExternalForm());
        }

        Label title = new Label("Уведомления");
        title.getStyleClass().add("notif-heading");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button clear = new Button("Очистить");
        clear.getStyleClass().add("link-button");
        clear.setOnAction(e -> {
            NotificationManager.clearHistory();
            unread = 0;
            updateBadge();
            notifPopup.hide();
        });
        HBox header = new HBox(8, title, spacer, clear);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox list = new VBox(8);
        List<NotificationManager.Notification> items = NotificationManager.history();
        if (items.isEmpty()) {
            Label empty = new Label("Пока уведомлений нет");
            empty.getStyleClass().add("notif-empty");
            list.getChildren().add(empty);
        } else {
            for (int i = 0; i < Math.min(8, items.size()); i++) {
                list.getChildren().add(notifRow(items.get(i)));
            }
        }
        panel.getChildren().addAll(header, list);
        notifPopup.getContent().add(panel);
        notifPopup.setOnHidden(e -> popupOpen = false);

        Bounds bounds = bellButton.localToScreen(bellButton.getBoundsInLocal());
        if (bounds != null) {
            notifPopup.show(bellButton, bounds.getMaxX() - 330, bounds.getMaxY() + 8);
        }
        popupOpen = true;
        unread = 0;
        updateBadge();
    }

    private HBox notifRow(NotificationManager.Notification notification) {
        Region dot = new Region();
        dot.getStyleClass().addAll("notif-dot", notification.level.name().toLowerCase());
        dot.setMinSize(8, 8);
        dot.setPrefSize(8, 8);
        dot.setMaxSize(8, 8);

        Label title = new Label(notification.title);
        title.getStyleClass().add("notif-row-title");
        Label message = new Label(notification.message);
        message.getStyleClass().add("notif-row-message");
        message.setWrapText(true);
        message.setMaxWidth(250);

        VBox text = new VBox(2, title, message);
        HBox.setHgrow(text, Priority.ALWAYS);

        HBox row = new HBox(10, dot, text);
        row.setAlignment(Pos.TOP_LEFT);
        HBox.setMargin(dot, new Insets(4, 0, 0, 0));
        row.getStyleClass().add("notif-row");
        return row;
    }

    // ── Theme toggle ────────────────────────────────────────────────────────
    private Button buildThemeButton() {
        themeButton.getStyleClass().add("icon-button");
        Tooltip.install(themeButton, new Tooltip("Сменить тему"));
        themeButton.setOnAction(e -> toggleTheme());
        return themeButton;
    }

    public void refreshTheme() {
        updateThemeIcon();
    }

    private void updateThemeIcon() {
        boolean light = Theme.isLight(state.config.theme);
        themeButton.setGraphic(Icons.icon(light ? Icons.MOON : Icons.SUN, 0.95, Theme.MUTED));
    }

    private void toggleTheme() {
        if (Theme.isLight(state.config.theme)) {
            state.config.theme = state.config.darkTheme;
        } else {
            state.config.darkTheme = state.config.theme;
            state.config.theme = Theme.MODE_LIGHT;
        }
        state.config.save();
        shell.applyVisualSettings();
        updateThemeIcon();
    }

    // ── Profile chip ────────────────────────────────────────────────────────
    private HBox buildProfileChip() {
        profileChip.getStyleClass().add("profile-chip");
        profileChip.setAlignment(Pos.CENTER_LEFT);

        StackPane avatar = new StackPane();
        Circle circle = new Circle(13);
        circle.setFill(Color.web("#39D353", 0.16));
        circle.setStroke(Color.web("#39D353", 0.7));
        circle.setStrokeWidth(1.2);
        avatarLetter.getStyleClass().add("avatar-letter");
        avatar.getChildren().addAll(circle, avatarLetter);

        userLabel.getStyleClass().add("profile-name");
        SVGPath chevron = Icons.icon(Icons.CHEVRON_DOWN, 0.8, Theme.MUTED);

        profileChip.getChildren().addAll(avatar, userLabel, chevron);
        profileChip.setOnMouseClicked(e -> showProfileMenu());
        return profileChip;
    }

    public void refreshProfile() {
        String name = state.config.username == null || state.config.username.isBlank()
                ? "Player" : state.config.username;
        userLabel.setText(name);
        avatarLetter.setText(name.substring(0, 1).toUpperCase());
    }

    private void showProfileMenu() {
        ContextMenu menu = new ContextMenu();
        menu.getStyleClass().add("app-menu");

        MenuItem nick = new MenuItem("Сменить ник");
        nick.setOnAction(e -> changeNick());
        MenuItem clients = new MenuItem("Клиенты");
        clients.setOnAction(e -> shell.navigate(Section.CLIENTS));
        MenuItem settings = new MenuItem("Настройки");
        settings.setOnAction(e -> shell.navigate(Section.SETTINGS));

        menu.getItems().addAll(nick, clients, new SeparatorMenuItem(), settings);
        menu.show(profileChip, Side.BOTTOM, 0, 6);
    }

    private void changeNick() {
        TextInputDialog dialog = new TextInputDialog(state.config.username);
        dialog.setTitle("Профиль");
        dialog.setHeaderText("Введите никнейм");
        dialog.setContentText("Ник:");
        dialog.initOwner(shell.stage());
        URL stylesheet = getClass().getResource("/com/spruce/styles.css");
        if (stylesheet != null) {
            dialog.getDialogPane().getStylesheets().add(stylesheet.toExternalForm());
        }
        dialog.getDialogPane().getStyleClass().addAll("root-shell", Theme.classFor(state.config.theme));
        javafx.scene.Node okButton = dialog.getDialogPane().lookupButton(ButtonType.OK);
        if (okButton != null) {
            okButton.getStyleClass().add("secondary-button");
        }

        Optional<String> result = dialog.showAndWait();
        result.map(String::trim).filter(value -> !value.isEmpty()).ifPresent(value -> {
            state.config.username = value.length() > 16 ? value.substring(0, 16) : value;
            state.config.save();
            refreshProfile();
            NotificationManager.success("Профиль", "Ник обновлён: " + state.config.username);
        });
    }

    // ── Animations ──────────────────────────────────────────────────────────
    public void setAnimationsEnabled(boolean enabled) {
        this.animations = enabled;
    }

    private void animateScale(javafx.scene.Node node, double target) {
        if (animations) {
            ScaleTransition transition = new ScaleTransition(Duration.millis(180), node);
            transition.setToX(target);
            transition.setToY(target);
            transition.setInterpolator(Interpolator.EASE_BOTH);
            transition.play();
        } else {
            node.setScaleX(target);
            node.setScaleY(target);
        }
    }

    private void animateGlow(DropShadow glow, double radius) {
        if (animations) {
            Timeline timeline = new Timeline(new KeyFrame(
                    Duration.millis(200),
                    new KeyValue(glow.radiusProperty(), radius, Interpolator.EASE_BOTH)));
            timeline.play();
        } else {
            glow.setRadius(radius);
        }
    }
}
