package com.example.vrminventory;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class VRMInventory extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(VRMInventory.class.getResource("log-entry.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 1366, 768);

        // Get controller reference
        LogEntryController controller = fxmlLoader.getController();

        // Set up close request handler
        stage.setOnCloseRequest(event -> {
            // Shutdown ExecutorService in controller
            if (controller != null) {
                controller.shutdown();
            }

            // Force exit the application
            Platform.exit();
            System.exit(0);
        });

        stage.setTitle("VRM Inventory System");
        stage.setResizable(false);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}