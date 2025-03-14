package com.example.vrminventory;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
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
    private Alert lockoutAlert;
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
    private Alert loadingAlert;

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

            // Show loading alert
            showLoadingAlert();

            // Store authenticated branch immediately
            currentBranch = USER_BRANCHES.get(username);

            // Load main application in background thread
            executorService.submit(() -> {
                try {
                    // Try to load the main view - check the actual filename in your project
                    // Try different possible filenames if needed
                    FXMLLoader loader = null;
                    try {
                        loader = new FXMLLoader(getClass().getResource("main-view.fxml"));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    if (loader == null) {
                        // Try alternative filenames if the first one fails
                        loader = new FXMLLoader(getClass().getResource("MainView.fxml"));
                    }

                    if (loader == null) {
                        throw new IOException("Cannot find main view FXML file");
                    }

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

                            // Close the loading alert
                            if (loadingAlert != null) {
                                loadingAlert.close();
                            }

                            // Close the login stage
                            ((Stage) loginButton.getScene().getWindow()).close();

                            // Show the main stage
                            mainStage.show();

                        } catch (Exception e) {
                            // Re-enable controls if something goes wrong
                            setControlsEnabled(true);
                            statusLabel.setText("Error loading application: " + e.getMessage());

                            // Close loading alert
                            if (loadingAlert != null) {
                                loadingAlert.close();
                            }

                            e.printStackTrace();
                        }
                    });
                } catch (IOException e) {
                    // Update UI in JavaFX thread
                    Platform.runLater(() -> {
                        // Re-enable controls
                        setControlsEnabled(true);
                        statusLabel.setText("Error loading application: " + e.getMessage());

                        // Close loading alert
                        if (loadingAlert != null) {
                            loadingAlert.close();
                        }

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
                            lockoutAlert.setContentText("For security reasons, login has been disabled for " +
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

    private void showTimeoutAlert() {
        Platform.runLater(() -> {
            if (lockoutAlert == null || !lockoutAlert.isShowing()) {
                lockoutAlert = new Alert(Alert.AlertType.WARNING);
                lockoutAlert.setTitle("Account Locked");
                lockoutAlert.setHeaderText("Too Many Failed Login Attempts");
                lockoutAlert.setContentText("For security reasons, login has been disabled for " +
                        remainingLockoutSeconds + " seconds. Please try again later.");

                // Make it non-modal so user can see the status label updates
                lockoutAlert.initModality(Modality.NONE);
                lockoutAlert.show();
            }
        });
    }

    private void showLoadingAlert() {
        Platform.runLater(() -> {
            loadingAlert = new Alert(Alert.AlertType.INFORMATION);
            loadingAlert.setTitle("Please Wait");
            loadingAlert.setHeaderText("Loading Application");
            loadingAlert.setContentText("Please wait while the application is loading...");
            loadingAlert.initModality(Modality.APPLICATION_MODAL);
            loadingAlert.initStyle(StageStyle.UNDECORATED);
            loadingAlert.show();
        });
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