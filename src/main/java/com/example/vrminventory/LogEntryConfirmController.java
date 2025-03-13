package com.example.vrminventory;

import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.stage.Stage;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LogEntryConfirmController {
    // Constants
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final List<String> ACTIVITY_LIST = List.of("Sale", "Transfer-In", "Transfer-Out", "Return/Refund");
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(1);

    // FXML components
    @FXML private Label branchInputLabel;
    @FXML private Label skuInputLabel;
    @FXML private Label activityInputLabel;
    @FXML private Label quantityInputLabel;
    @FXML private Label descInputLabel;
    @FXML private Label statusLabel;

    // Service and data fields
    private final GoogleSheetsService sheetsService;
    private final String branch;
    private final int sku;
    private final String activity;
    private final int quantity;
    private final String description;
    private final LogEntryController parentController;

    // State
    private boolean isConfirmed = false;

    public LogEntryConfirmController(GoogleSheetsService sheetsService,
                                     String branch,
                                     int sku,
                                     String activity,
                                     int quantity,
                                     String description,
                                     LogEntryController parentController) {
        this.sheetsService = sheetsService;
        this.branch = branch;
        this.sku = sku;
        this.activity = activity;
        this.quantity = quantity;
        this.description = description;
        this.parentController = parentController;
    }

    @FXML
    private void initialize() {
        // Set the input values to the labels
        branchInputLabel.setText(branch);
        skuInputLabel.setText(String.valueOf(sku));
        activityInputLabel.setText(activity);
        quantityInputLabel.setText(String.valueOf(quantity));
        descInputLabel.setText(description);

        // Clear status label
        statusLabel.setText("");
    }

    @FXML
    private void onHelloButtonClick(ActionEvent event) throws IOException {
        // Determine which button was clicked based on its text
        String buttonText = ((javafx.scene.control.Button) event.getSource()).getText();

        if ("CANCEL".equals(buttonText)) {
            closeWindow(1);
        } else if ("CONFIRM".equals(buttonText)) {
            confirmLogEntry();
        }
    }

    private void confirmLogEntry() {
        // Create processing alert
        Alert processingAlert = new Alert(Alert.AlertType.INFORMATION);
        processingAlert.setTitle("Processing");
        processingAlert.setHeaderText(null);
        processingAlert.setContentText("Processing...");
        processingAlert.show();

        Task<Integer> task = new Task<>() {
            @Override
            protected Integer call() throws Exception {
                // Prepare data
                List<Object> dataToWrite = Arrays.asList(
                        LocalDate.now().format(DATE_FORMATTER),
                        LocalTime.now().format(TIME_FORMATTER),
                        ACTIVITY_LIST.indexOf(activity) + 1,
                        sku,
                        quantity,
                        description
                );

                // Find next row and write data
                String branchPrefix = branch + "!";
                String range = sheetsService.findNextRow(branchPrefix);
                return sheetsService.writeData(range, dataToWrite);
            }
        };

        task.setOnSucceeded(event -> {
            int cellsUpdated = task.getValue();
            processingAlert.close();

            // Show success or failure alert
            Alert resultAlert = new Alert(Alert.AlertType.INFORMATION);
            resultAlert.setTitle("Log Entry Result");
            resultAlert.setHeaderText(null);

            if (cellsUpdated > 0) {
                resultAlert.setContentText("Success");
                isConfirmed = true;
            } else {
                resultAlert.setContentText("Failed to update log");
            }

            // Set action for when alert is closed
            resultAlert.setOnHidden(e -> {
                try {
                    closeWindow(0);
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            });
            resultAlert.show();
        });

        task.setOnFailed(event -> {
            processingAlert.close();

            // Show failure alert with exception info
            Alert failAlert = new Alert(Alert.AlertType.ERROR);
            failAlert.setTitle("Log Entry Failed");
            failAlert.setHeaderText(null);
            failAlert.setContentText("Failed: " + task.getException().getMessage());

            // Set action for when alert is closed
            failAlert.setOnHidden(e -> {
                try {
                    closeWindow(1);
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            });
            failAlert.show();
        });

        // Start the task
        EXECUTOR.submit(task);
    }

    public boolean isConfirmed() {
        return isConfirmed;
    }

    private void closeWindow(int indicator) throws IOException {
        // Get the current stage and close it
        Stage stage = (Stage) branchInputLabel.getScene().getWindow();
        stage.close();

        if (indicator == 0) {
            // Only refresh data if confirmation was successful
            if (isConfirmed && parentController != null) {
                // Call the refreshData method on the parent controller to update the ListView
                parentController.refreshData();
            }
        }
    }

    // Shutdown the executor service when no longer needed
    public void shutdown() {
        EXECUTOR.shutdown();
    }
}