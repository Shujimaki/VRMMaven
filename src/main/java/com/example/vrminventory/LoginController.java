package com.example.vrminventory;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class LoginController {
    // Account credentials mapping
    private static final Map<String, String> CREDENTIALS = new HashMap<>();
    private static final Map<String, String> USER_BRANCHES = new HashMap<>();

    // Login attempt tracking
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final int TIMEOUT_SECONDS = 5;
    private int failedLoginAttempts = 0;
    private boolean isLocked = false;
    private int remainingLockoutSeconds = 0;

    // Lockout persistence
    private static final String LOCKOUT_FILE = "lockout.properties";
    private static final String ATTEMPTS_KEY = "failedAttempts";
    private static final String LOCKOUT_TIME_KEY = "lockoutUntil";

    private ScheduledExecutorService lockoutTimer;
    private ScheduledExecutorService countdownTimer;
    private Alert lockoutAlert; // Keep this for updating its content
    private AtomicBoolean countdownActive = new AtomicBoolean(false);

    static {
        // Initialize credentials map
        CREDENTIALS.put("branch1", "vrmbranch1");
        CREDENTIALS.put("branch2", "vrmbranch2");
        CREDENTIALS.put("branch3", "vrmbranch3");
        CREDENTIALS.put("warehouse", "vrmwarehouse");

        // Map users to their respective branches
        USER_BRANCHES.put("branch1", "Branch1");
        USER_BRANCHES.put("branch2", "Branch2");
        USER_BRANCHES.put("branch3", "Branch3");
        USER_BRANCHES.put("warehouse", "Warehouse");
    }

    // FXML components
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label statusLabel;
    @FXML private Button loginButton;

    // Current authenticated branch
    private static String currentBranch;

    // For background loading
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private Stage loadingStage; // Add this to store the loading stage


    @FXML
    private void initialize() {
        // Clear the status label
        statusLabel.setText("");
        lockoutTimer = Executors.newSingleThreadScheduledExecutor();

        // Only create countdownTimer when needed (in startCountdown)

        // Check for existing lockout state
        checkPersistentLockout();
    }

    private void checkPersistentLockout() {
        Properties props = loadLockoutProperties();
        if (props != null) {
            // Get saved values
            String attemptsStr = props.getProperty(ATTEMPTS_KEY, "0");
            String lockoutTimeStr = props.getProperty(LOCKOUT_TIME_KEY, "");

            try {
                failedLoginAttempts = Integer.parseInt(attemptsStr);

                // If we have a lockout time, check if it's still valid
                if (!lockoutTimeStr.isEmpty()) {
                    LocalDateTime lockoutUntil = LocalDateTime.parse(lockoutTimeStr);
                    LocalDateTime now = LocalDateTime.now();

                    if (now.isBefore(lockoutUntil)) {
                        // Lockout is still valid, calculate remaining time
                        long remainingSeconds = java.time.Duration.between(now, lockoutUntil).getSeconds();
                        remainingLockoutSeconds = (int) remainingSeconds;

                        // Set locked state and start countdown
                        isLocked = true;
                        setControlsEnabled(false);
                        statusLabel.setText("Login locked for " + remainingLockoutSeconds + " seconds");
                        startCountdown();
                    } else {
                        // Lockout expired, reset attempts
                        failedLoginAttempts = 0;
                        saveLockoutState(false);
                    }
                } else if (failedLoginAttempts >= MAX_LOGIN_ATTEMPTS) {
                    // We have max attempts but no lockout time (shouldn't happen, but just in case)
                    lockLogin();
                }
            } catch (Exception e) {
                e.printStackTrace();
                // Reset on error
                failedLoginAttempts = 0;
                saveLockoutState(false);
            }
        }
    }

    @FXML
    protected void onHelloButtonClick() {
        // Check if login is locked
        if (isLocked) {
            showTimeoutAlert();
            return;
        }

        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        // Validate fields
        if (username.isEmpty() || password.isEmpty()) {
            statusLabel.setText("Please fill all fields");
            return;
        }

        // Check credentials
        if (authenticate(username, password)) {
            // Reset failed login attempts on successful login
            failedLoginAttempts = 0;
            saveLockoutState(false);

            // Disable input controls
            setControlsEnabled(false);
            statusLabel.setText("Logging in...");

            // Show loading alert, and get a reference to the stage
            loadingStage = showLoadingAlert("Please wait while the application is loading...");


            // Store authenticated branch immediately
            currentBranch = USER_BRANCHES.get(username);

            // Load main application in background thread
            executorService.submit(() -> {
                try {

                    FXMLLoader loader = new FXMLLoader(getClass().getResource("main-view.fxml"));


                    Parent root = loader.load();
                    MainViewController controller = loader.getController();

                    // Update UI in JavaFX thread
                    Platform.runLater(() -> {
                        try {
                            // Create and show the main stage
                            Stage mainStage = new Stage();
                            mainStage.setTitle("VRM Inventory - " + currentBranch);
                            mainStage.setScene(new Scene(root, 1366, 768));
                            mainStage.setMaximized(false);
                            mainStage.setResizable(false);

                            // Set up close request handler
                            mainStage.setOnCloseRequest(event -> {
                                // Shutdown this controller's executors
                                shutdownExecutor();

                                // Force exit the application
                                Platform.exit();
                                System.exit(0);
                            });

                            // Close the loading stage *before* showing the main stage
                            if (loadingStage != null && loadingStage.isShowing()) {
                                loadingStage.close();
                            }

                            // Close the login stage
                            ((Stage) loginButton.getScene().getWindow()).close();

                            // Show the main stage
                            mainStage.show();

                        } catch (Exception e) {
                            // Re-enable controls if something goes wrong
                            setControlsEnabled(true);
                            statusLabel.setText("Error loading application: " + e.getMessage());
                            // Close the loading stage in case of an error
                            if (loadingStage != null && loadingStage.isShowing()) {
                                loadingStage.close();
                            }

                            showErrorAlert("Failed to load main view screen", e.getMessage()); // Use consistent error alert
                            e.printStackTrace();
                        }
                    });
                } catch (IOException e) {
                    // Update UI in JavaFX thread
                    Platform.runLater(() -> {
                        // Re-enable controls
                        setControlsEnabled(true);
                        statusLabel.setText("Error loading application: " + e.getMessage());

                        // Close the loading stage in case of an error
                        if (loadingStage != null && loadingStage.isShowing()) {
                            loadingStage.close();
                        }

                        showErrorAlert("Failed to load the application", e.getMessage());// Use consistent error alert

                        e.printStackTrace();
                    });
                }
            });
        } else {
            // Increment failed login attempts
            failedLoginAttempts++;

            // Save the current state
            saveLockoutState(false);

            // Check if max attempts reached
            if (failedLoginAttempts >= MAX_LOGIN_ATTEMPTS) {
                lockLogin();
            } else {
                int attemptsLeft = MAX_LOGIN_ATTEMPTS - failedLoginAttempts;
                statusLabel.setText("Invalid username or password. " + attemptsLeft +
                        (attemptsLeft == 1 ? " attempt" : " attempts") + " remaining.");
            }
        }
    }

    private void lockLogin() {
        isLocked = true;
        remainingLockoutSeconds = TIMEOUT_SECONDS;

        // Save lockout state with end time
        saveLockoutState(true);

        // Update UI to show locked state
        Platform.runLater(() -> {
            setControlsEnabled(false);
            statusLabel.setText("Login locked for " + remainingLockoutSeconds + " seconds");

            // Start the countdown timer
            startCountdown();

            // Show the initial alert
            showTimeoutAlert();
        });
    }

    private void startCountdown() {
        // Only start if not already active
        if (countdownActive.compareAndSet(false, true)) {
            // Always create a new executor to avoid RejectedExecutionException
            if (countdownTimer != null && !countdownTimer.isShutdown()) {
                countdownTimer.shutdownNow();
            }
            countdownTimer = Executors.newSingleThreadScheduledExecutor();

            // Schedule the countdown timer to update every second
            countdownTimer.scheduleAtFixedRate(() -> {
                if (remainingLockoutSeconds > 0) {
                    remainingLockoutSeconds--;

                    Platform.runLater(() -> {
                        // Update the status label with the countdown
                        statusLabel.setText("Login locked for " + remainingLockoutSeconds + " seconds");

                        // Update the alert content if it's showing
                        if (lockoutAlert != null && lockoutAlert.isShowing()) {
                            updateLockoutAlert("Account Locked", "Too Many Failed Login Attempts", "For security reasons, login has been disabled for " +
                                    remainingLockoutSeconds + " seconds. Please try again later.");

                        }
                    });

                    // Save the updated state periodically (every 5 seconds)
                    if (remainingLockoutSeconds % 5 == 0) {
                        saveLockoutState(true);
                    }
                } else {
                    // Time's up, unlock
                    isLocked = false;

                    Platform.runLater(() -> {
                        setControlsEnabled(true);
                        statusLabel.setText("Login unlocked. You may try again.");

                        // Close the alert if it's still showing
                        if (lockoutAlert != null && lockoutAlert.isShowing()) {
                            lockoutAlert.close();
                        }
                    });

                    // Reset failed attempts
                    failedLoginAttempts = 0;
                    saveLockoutState(false);

                    // Stop the countdown timer
                    stopCountdown();
                }
            }, 0, 1, TimeUnit.SECONDS);
        }
    }

    private void stopCountdown() {
        countdownActive.set(false);
        if (countdownTimer != null && !countdownTimer.isShutdown()) {
            countdownTimer.shutdownNow();
            countdownTimer = null;
        }
    }

    private void saveLockoutState(boolean isLocked) {
        Properties props = new Properties();
        props.setProperty(ATTEMPTS_KEY, String.valueOf(failedLoginAttempts));

        if (isLocked) {
            // Calculate and save the end time of the lockout
            LocalDateTime lockoutUntil = LocalDateTime.now().plusSeconds(remainingLockoutSeconds);
            props.setProperty(LOCKOUT_TIME_KEY, lockoutUntil.toString());
        } else {
            // Clear the lockout time
            props.setProperty(LOCKOUT_TIME_KEY, "");
        }

        // Save to file
        try (FileOutputStream fos = new FileOutputStream(LOCKOUT_FILE)) {
            props.store(fos, "Login Lockout State");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Properties loadLockoutProperties() {
        Properties props = new Properties();
        File lockoutFile = new File(LOCKOUT_FILE);

        if (lockoutFile.exists()) {
            try (FileInputStream fis = new FileInputStream(lockoutFile)) {
                props.load(fis);
                return props;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return null;
    }
    // Reusable method for showing alerts with consistent style
    private void showAlert(Alert.AlertType alertType, String title, String header, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(alertType);
            alert.setTitle(title);
            alert.setHeaderText(header);
            alert.setContentText(content);
            alert.initModality(Modality.APPLICATION_MODAL);
            alert.initStyle(StageStyle.UNDECORATED);
            alert.showAndWait();
        });
    }
    private Stage showLoadingAlert( String message) {

        // Create a new stage for modal loading alert
        Stage loadingStage = new Stage();
        loadingStage.initModality(Modality.APPLICATION_MODAL);
        loadingStage.initStyle(StageStyle.UNDECORATED);
        loadingStage.setResizable(false);

        // Create the loading alert content
        VBox loadingBox = new VBox(10);
        loadingBox.setAlignment(javafx.geometry.Pos.CENTER);
        loadingBox.setPadding(new javafx.geometry.Insets(20));
        loadingBox.setStyle("-fx-background-color: white; -fx-border-color: #cccccc; -fx-border-width: 1px;");

        Label loadingLabel = new Label(message);
        loadingLabel.setStyle("-fx-font-size: 14px;");

        ProgressIndicator progress = new ProgressIndicator();
        progress.setProgress(-1.0f); // Indeterminate progress

        loadingBox.getChildren().addAll(loadingLabel, progress);

        // Set scene for loading stage
        Scene loadingScene = new Scene(loadingBox, 400, 150);
        loadingStage.setScene(loadingScene);
        loadingStage.show(); //show

        return loadingStage; // Return the stage

    }
    private void showErrorAlert(String header, String message) {
        Alert errorAlert = new Alert(Alert.AlertType.ERROR);
        errorAlert.setTitle("Error");
        errorAlert.setHeaderText(header);
        errorAlert.setContentText("Error: " + message);
        errorAlert.initModality(Modality.APPLICATION_MODAL); // Make sure it's modal
        errorAlert.initStyle(StageStyle.UNDECORATED);       // Remove decorations
        errorAlert.showAndWait();
    }

    private void updateLockoutAlert(String title, String header, String content){
        if (lockoutAlert == null || !lockoutAlert.isShowing()){
            lockoutAlert = new Alert(Alert.AlertType.WARNING);
            lockoutAlert.setTitle(title);
            lockoutAlert.setHeaderText(header);
            lockoutAlert.initModality(Modality.NONE);
            lockoutAlert.initStyle(StageStyle.UNDECORATED);
            lockoutAlert.show(); //show and continue

        }
        lockoutAlert.setContentText(content); //update content

    }

    private void showTimeoutAlert() {

        updateLockoutAlert("Account Locked", "Too Many Failed Login Attempts", "For security reasons, login has been disabled for " +
                remainingLockoutSeconds + " seconds. Please try again later.");
    }



    private void setControlsEnabled(boolean enabled) {
        usernameField.setDisable(!enabled);
        passwordField.setDisable(!enabled);
        loginButton.setDisable(!enabled);
    }

    private boolean authenticate(String username, String password) {
        // Check if username exists and password matches
        return CREDENTIALS.containsKey(username) &&
                CREDENTIALS.get(username).equals(password);
    }

    // Static getter for branch information
    public static String getCurrentBranch() {
        return currentBranch;
    }

    // Static method to check if user is warehouse
    public static boolean isWarehouse() {
        return "Warehouse".equals(currentBranch);
    }

    // Clean shutdown of resources
    public void shutdownExecutor() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }
        if (lockoutTimer != null && !lockoutTimer.isShutdown()) {
            lockoutTimer.shutdownNow();
        }
        stopCountdown();
    }
}