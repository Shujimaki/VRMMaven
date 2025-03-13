package com.example.vrminventory;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class VRMInventory extends Application {
    private LoginController loginController;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(VRMInventory.class.getResource("login.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 720, 720);

        // Get controller reference for the LoginController
        loginController = fxmlLoader.getController();

        // Set up close request handler
        stage.setOnCloseRequest(event -> {
            // Shutdown ExecutorService in controller
            if (loginController != null) {
                loginController.shutdownExecutor();
            }

            // Force exit the application
            Platform.exit();
            System.exit(0);
        });

        stage.setTitle("VRM Inventory System - Login");
        stage.setResizable(false);
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() {
        // Ensure all resources are properly cleaned up
        if (loginController != null) {
            loginController.shutdownExecutor();
        }

        // Force exit to ensure complete termination
        System.exit(0);
    }

    public static void main(String[] args) {
        launch();
    }
}