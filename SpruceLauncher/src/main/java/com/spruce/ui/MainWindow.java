package com.spruce.ui;

import com.spruce.core.AppState;
import com.spruce.core.NotificationManager;
import com.spruce.ui.views.ClientsView;
import com.spruce.ui.views.HomeView;
import com.spruce.ui.views.SettingsView;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.net.URL;

public class MainWindow {
    private static final int MAX_TOASTS = 4;

    private final Stage stage;
    private final AppState state = new AppState();

    private final ParticleBackdrop backdrop = new ParticleBackdrop();
    private final StackPane root = new StackPane();
    private final StackPane contentHost = new StackPane();
    private final VBox toastLayer = new VBox(10);
    private final Sidebar sidebar;

    private final HomeView homeView;
    private final ClientsView clientsView;
    private final SettingsView settingsView;
    private final TopBar topBar;

    private Section active = Section.HOME;
    private boolean animations = true;
    private boolean reduceMotion = false;
    private Scene scene;

    public MainWindow(Stage stage) {
        this.stage = stage;

        sidebar = new Sidebar(this::navigate);
        clientsView = new ClientsView(state, this);
        homeView = new HomeView(state, this);
        settingsView = new SettingsView(state, this);
        topBar = new TopBar(state, this);

        contentHost.getStyleClass().add("content-host");
        contentHost.getChildren().addAll(homeView, clientsView, settingsView);
        for (Node node : contentHost.getChildren()) {
            node.setVisible(node == homeView);
            node.setOpacity(node == homeView ? 1 : 0);
        }

        VBox centerColumn = new VBox(topBar, contentHost);
        VBox.setVgrow(contentHost, Priority.ALWAYS);

        BorderPane shell = new BorderPane();
        shell.getStyleClass().add("shell");
        shell.setLeft(sidebar);
        shell.setCenter(centerColumn);

        toastLayer.setAlignment(Pos.TOP_RIGHT);
        toastLayer.setPickOnBounds(false);
        toastLayer.setMouseTransparent(true);
        StackPane.setAlignment(toastLayer, Pos.TOP_RIGHT);
        StackPane.setMargin(toastLayer, new Insets(20));

        root.getStyleClass().add("root-shell");
        root.getChildren().addAll(backdrop, shell, toastLayer);
        root.setPickOnBounds(true);

        NotificationManager.setSink(this::showToast);
        applyVisualSettings();
        sidebar.setActive(Section.HOME);
    }

    public void show() {
        scene = new Scene(root, 1280, 800);
        URL stylesheet = getClass().getResource("/com/spruce/styles.css");
        if (stylesheet != null) {
            scene.getStylesheets().add(stylesheet.toExternalForm());
        }
        applyTheme();
        stage.setScene(scene);
        stage.setTitle("SpruceLauncher");
        stage.setMinWidth(1080);
        stage.setMinHeight(720);
        stage.setOnCloseRequest(e -> shutdown());

        root.setOpacity(0);
        stage.show();

        FadeTransition fade = new FadeTransition(Duration.millis(480), root);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.play();
    }

    public AppState state() {
        return state;
    }

    public Stage stage() {
        return stage;
    }

    public void refreshHome() {
        homeView.refresh();
    }

    public boolean animationsEnabled() {
        return animations;
    }

    public boolean reduceMotion() {
        return reduceMotion;
    }

    public void navigate(Section section) {
        if (section == active) {
            return;
        }
        active = section;
        sidebar.setActive(section);
        Node target = viewFor(section);
        for (Node node : contentHost.getChildren()) {
            node.setVisible(node == target);
            if (node != target) {
                node.setOpacity(0);
            }
        }
        target.setVisible(true);
        if (section == Section.HOME) {
            homeView.refresh();
        } else if (section == Section.CLIENTS) {
            clientsView.refresh();
            clientsView.playEntrance();
        }
        if (animations) {
            target.setOpacity(0);
            target.setTranslateY(12);
            FadeTransition fade = new FadeTransition(Duration.millis(260), target);
            fade.setToValue(1);
            TranslateTransition move = new TranslateTransition(Duration.millis(260), target);
            move.setToY(0);
            new ParallelTransition(fade, move).play();
        } else {
            target.setOpacity(1);
            target.setTranslateY(0);
        }
    }

    private Node viewFor(Section section) {
        switch (section) {
            case CLIENTS:
                return clientsView;
            case SETTINGS:
                return settingsView;
            default:
                return homeView;
        }
    }

    public void applyVisualSettings() {
        animations = !"off".equals(state.config.animationIntensity);
        reduceMotion = "reduced".equals(state.config.animationIntensity) || !animations;

        backdrop.setParticlesEnabled(state.config.particles);
        backdrop.setGlowEnabled(state.config.backgroundGlow);
        backdrop.setReduceMotion(reduceMotion);
        backdrop.setTheme(state.config.theme);

        homeView.setAnimationsEnabled(animations && !reduceMotion);
        clientsView.setAnimationsEnabled(animations && !reduceMotion);
        settingsView.setAnimationsEnabled(animations);
        topBar.setAnimationsEnabled(animations && !reduceMotion);
        topBar.refreshTheme();

        applyTheme();
    }

    private void applyTheme() {
        root.getStyleClass().removeAll(Theme.GRAPHITE, Theme.OBSIDIAN, Theme.LIGHT);
        root.getStyleClass().add(Theme.classFor(state.config.theme));
        if (scene == null) {
            return;
        }
        URL lightStylesheet = getClass().getResource("/com/spruce/styles-light.css");
        if (lightStylesheet == null) {
            return;
        }
        String url = lightStylesheet.toExternalForm();
        boolean present = scene.getStylesheets().contains(url);
        if (Theme.isLight(state.config.theme) && !present) {
            scene.getStylesheets().add(url);
        } else if (!Theme.isLight(state.config.theme) && present) {
            scene.getStylesheets().remove(url);
        }
    }

    private void showToast(NotificationManager.Notification notification) {
        HBox toast = new HBox(12);
        toast.getStyleClass().add("toast");
        toast.getStyleClass().add(notification.level.name().toLowerCase());
        toast.setAlignment(Pos.CENTER_LEFT);

        Region dot = new Region();
        dot.getStyleClass().add("toast-dot");
        dot.setMinSize(8, 8);
        dot.setPrefSize(8, 8);
        dot.setMaxSize(8, 8);

        VBox text = new VBox(2);
        Label title = new Label(notification.title);
        title.getStyleClass().add("toast-title");
        Label message = new Label(notification.message);
        message.getStyleClass().add("toast-message");
        message.setWrapText(true);
        message.setMaxWidth(280);
        text.getChildren().addAll(title, message);

        toast.getChildren().addAll(dot, text);
        toast.setOpacity(0);
        toast.setTranslateX(30);
        toastLayer.getChildren().add(0, toast);
        while (toastLayer.getChildren().size() > MAX_TOASTS) {
            toastLayer.getChildren().remove(toastLayer.getChildren().size() - 1);
        }

        if (animations) {
            FadeTransition fade = new FadeTransition(Duration.millis(220), toast);
            fade.setToValue(1);
            TranslateTransition move = new TranslateTransition(Duration.millis(220), toast);
            move.setToX(0);
            ParallelTransition enter = new ParallelTransition(fade, move);

            PauseTransition hold = new PauseTransition(Duration.millis(3200));
            FadeTransition out = new FadeTransition(Duration.millis(260), toast);
            out.setToValue(0);
            TranslateTransition slide = new TranslateTransition(Duration.millis(260), toast);
            slide.setToX(30);
            ParallelTransition exit = new ParallelTransition(out, slide);
            exit.setOnFinished(e -> toastLayer.getChildren().remove(toast));

            new SequentialTransition(enter, hold, exit).play();
        } else {
            toast.setOpacity(1);
            toast.setTranslateX(0);
            PauseTransition hold = new PauseTransition(Duration.millis(3200));
            hold.setOnFinished(e -> toastLayer.getChildren().remove(toast));
            hold.play();
        }
    }

    private void shutdown() {
        backdrop.stop();
        homeView.stop();
        settingsView.stop();
    }
}
