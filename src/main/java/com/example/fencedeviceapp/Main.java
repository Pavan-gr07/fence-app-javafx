package com.example.fencedeviceapp;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public class Main extends Application {
    private Controller controller;

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(Main.class.getResource("layout.fxml"));
        Scene scene = new Scene(loader.load(), 1200, 700);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Device Status Monitor");

        // Set the window icon
        Image icon = new Image(Main.class.getResourceAsStream("Crown_logo.png"));
        primaryStage.getIcons().add(icon);

        // Get controller reference
        controller = loader.getController();

        // Disable resizing
        primaryStage.setResizable(false);

        // Set up close handler
        primaryStage.setOnCloseRequest(event -> {
            if (controller != null) {
                controller.shutdown();
            }
        });

        primaryStage.show();
    }

    @Override
    public void stop() {
        // Ensure background tasks are shut down
        if (controller != null) {
            controller.shutdown();
        }
        Platform.exit();
    }

    public static void main(String[] args) {
        launch(args);
    }
}