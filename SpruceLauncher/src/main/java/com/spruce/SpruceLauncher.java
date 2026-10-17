package com.spruce;

import com.spruce.ui.MainWindow;
import javafx.application.Application;
import javafx.stage.Stage;

public class SpruceLauncher extends Application {
    @Override
    public void start(Stage stage) {
        MainWindow window = new MainWindow(stage);
        window.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
