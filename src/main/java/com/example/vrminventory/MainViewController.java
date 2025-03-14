package com.example.vrminventory;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class MainViewController {
    // Constants
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);

    // Lists
    private static final List<String> SEARCH_FILTERS = List.of("SKU", "Description");
    private static final List<String> TYPE_FILTERS = List.of("Date and Time", "SKU", "Quantity");
    private static final List<String> ASC_DESC_FILTERS = List.of("Ascending", "Descending");

    // Fields
    private GoogleSheetsService sheetsService;
    private List<LogEntry> logEntries;
    private List<LogEntry> filteredLogEntries;

    // FXML components
    @FXML
    private Label branchLabel;
    @FXML
    private Label mainLabel;
    @FXML
    private Label statusLabel;
    @FXML
    private VBox listViewContainer;
    @FXML
    private ListView<LogEntry> logListView;
    @FXML
    private ComboBox<String> searchFilterComboBox;
    @FXML
    private TextField searchField;
    @FXML
    private ComboBox<String> typeFilterComboBox;
    @FXML
    private ComboBox<String> ascOrDescComboBox;

    private ObservableList<LogEntry> observableLogList;
    private String currentBranch;

    public MainViewController() {
        try {
            // Initialize Google Sheets service
            sheetsService = new GoogleSheetsService();
        } catch (GeneralSecurityException | IOException e) {
            System.err.println("Failed to initialize Google Sheets service: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void initialize() {
        try {
            // Get branch from login controller
            String loginBranch = LoginController.getCurrentBranch();
            if (loginBranch != null && !loginBranch.isEmpty()) {
                currentBranch = loginBranch;
                branchLabel.setText(currentBranch);
                mainLabel.setText(currentBranch.toUpperCase() + " Log Sheet");
            } else {
                currentBranch = "Branch1"; // Default branch
            }

            // Initialize data
            initializeData();

            // Set up UI components
            setupUIComponents();

            // Set up event listeners
            setupEventListeners();

            // Initialize filter at program start
            typeFilterComboBox.setValue("Date and Time");
            ascOrDescComboBox.setValue("Descending");
            applyFilters();

        } catch (Exception e) {
            if (statusLabel != null) {
                statusLabel.setText("Initialization error: " + e.getMessage());
            }
            e.printStackTrace();
        }
    }

    private void initializeData() throws IOException {
        if (sheetsService == null) {
            throw new IOException("Google Sheets service is not initialized");
        }

        logEntries = getLogEntries(currentBranch);
        filteredLogEntries = new ArrayList<>(logEntries);
        observableLogList = FXCollections.observableArrayList(filteredLogEntries);
    }

    // Method to get log entries from Google Sheets
    private List<LogEntry> getLogEntries(String branch) throws IOException {
        List<LogEntry> entries = new ArrayList<>();

        // Get sheet data from range I21:N (Date, Time, Activity, SKU, Quantity, Description)
        String range = branch + "!I21:N";
        var response = sheetsService.getSheetsService().spreadsheets().values()
                .get(sheetsService.getSpreadsheetId(), range)
                .execute();

        if (response.getValues() != null) {
            for (List<Object> row : response.getValues()) {
                // Skip empty rows
                if (row.isEmpty()) continue;

                try {
                    // Ensure we have enough columns
                    if (row.size() >= 6) {
                        String date = row.get(0).toString();
                        String time = row.get(1).toString();
                        String activity = convertActivityCodeToString(row.get(2).toString(), branch);
                        int sku = Integer.parseInt(row.get(3).toString());
                        int quantity = Integer.parseInt(row.get(4).toString());
                        String description = row.size() > 5 ? row.get(5).toString() : "";

                        // Create LogEntry and add to list
                        LogEntry logEntry = new LogEntry(date, time, activity, sku, quantity, description);

                        // Get additional item details
                        enrichLogEntryWithItemDetails(logEntry, branch);

                        entries.add(logEntry);
                    }
                } catch (NumberFormatException e) {
                    System.err.println("Invalid number format in log entry: " + e.getMessage());
                } catch (Exception e) {
                    System.err.println("Error processing log entry: " + e.getMessage());
                }
            }
        }

        return entries;
    }

    // Method to convert activity codes to their string equivalents
    private String convertActivityCodeToString(String activityCode, String branch) {
        if (branch.toLowerCase().contains("warehouse")) {
            // Warehouse activity codes (1-2)
            switch (activityCode) {
                case "1": return "Supply";
                case "2": return "Transfer-Out";
                default: return activityCode; // Return original if unrecognized
            }
        } else {
            // Branch activity codes (1-4)
            switch (activityCode) {
                case "1": return "Sale";
                case "2": return "Transfer-In";
                case "3": return "Transfer-Out";
                case "4": return "Return/Refund";
                default: return activityCode; // Return original if unrecognized
            }
        }
    }

    // Method to enrich log entry with item details (name, category, price)
    private void enrichLogEntryWithItemDetails(LogEntry logEntry, String branch) {
        try {
            // Get inventory items for the branch
            List<InventoryItem> items = sheetsService.getAllInventoryItems(branch);

            // Find the item that matches the SKU
            for (InventoryItem item : items) {
                if (item.getSku() == logEntry.getSku()) {
                    logEntry.setItemName(item.getName());
                    logEntry.setItemCategory(item.getCategory());
                    logEntry.setItemPrice(item.getPrice());
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to get item details: " + e.getMessage());
        }
    }

    // Method to refresh data from Google Sheets
    public void refreshData() {
        try {
            // Retrieve fresh data from Google Sheets
            logEntries = getLogEntries(currentBranch);

            // Update filtered list
            String searchFilter = searchFilterComboBox.getValue();
            String searchText = searchField.getText();

            if (searchFilter != null && !searchText.isEmpty()) {
                // Apply search filter if active
                handleSearch(searchText);
            } else {
                // Otherwise reset to full list
                filteredLogEntries.clear();
                filteredLogEntries.addAll(logEntries);
                applyFilters();
            }

            // Update status
            if (statusLabel != null) {
                statusLabel.setText("Data refreshed successfully");
            }
        } catch (IOException e) {
            if (statusLabel != null) {
                statusLabel.setText("Error refreshing data: " + e.getMessage());
            }
            e.printStackTrace();
        }
    }

    private void setupUIComponents() {
        searchFilterComboBox.getItems().addAll(SEARCH_FILTERS);
        typeFilterComboBox.getItems().addAll(TYPE_FILTERS);
        ascOrDescComboBox.getItems().addAll(ASC_DESC_FILTERS);

        searchField.setDisable(true);
        searchFilterComboBox.setValue(null);

        setupListView();
    }

    private void setupEventListeners() {
        searchFilterComboBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && !newValue.isEmpty()) {
                searchField.setDisable(false);
                searchField.clear();
                // Apply the search for an empty string to reset the filtered list
                handleSearch("");
            } else {
                searchField.setDisable(true);
                resetToFullList();
            }
        });

        searchField.textProperty().addListener((observable, oldValue, newValue) ->
                handleSearch(newValue));

        typeFilterComboBox.valueProperty().addListener((obs, oldVal, newVal) -> applyFilters());
        ascOrDescComboBox.valueProperty().addListener((obs, oldVal, newVal) -> applyFilters());

        // Add mouse click listener to handle double-click on list items
        logListView.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                LogEntry selectedEntry = logListView.getSelectionModel().getSelectedItem();
                if (selectedEntry != null) {
                    showLogEntryDetails(selectedEntry);
                }
            }
        });
    }

    @FXML
    private void setupListView() {
        // Create and configure header GridPane
        GridPane headers = createHeaderGridPane();
        listViewContainer.getChildren().add(0, headers);

        // Calculate column widths
        double totalWidth = logListView.getPrefWidth();
        double[] columnWidths = {
                totalWidth * 0.22,  // Date column
                totalWidth * 0.28,  // Time column
                totalWidth * 0.20,  // Activity column
                totalWidth * 0.27,  // SKU column
                totalWidth * 0.23,  // Quantity column
                totalWidth * 0.20   // Description column
        };

        // Configure ListView with custom cell factory
        logListView.setCellFactory(createLogCellFactory(columnWidths));

        // Remove fixed cell size to allow dynamic height based on content
        logListView.setFixedCellSize(Region.USE_COMPUTED_SIZE);

        // Populate the ListView
        logListView.setItems(observableLogList);
    }

    private GridPane createHeaderGridPane() {
        GridPane headers = new GridPane();
        headers.setHgap(10);
        headers.setPrefWidth(logListView.getPrefWidth());

        // Add header labels
        addHeaderLabel(headers, "Date", 0);
        addHeaderLabel(headers, "Time", 1);
        addHeaderLabel(headers, "Activity", 2);
        addHeaderLabel(headers, "SKU", 3);
        addHeaderLabel(headers, "Quantity", 4);
        addHeaderLabel(headers, "Description", 5);

        // Set column constraints
        for (int i = 0; i < 6; i++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(i == 5 ? 25 : 15); // Make Description column wider
            headers.getColumnConstraints().add(column);
        }

        return headers;
    }

    private void addHeaderLabel(GridPane grid, String text, int column) {
        Label label = new Label(text);
        label.setTextFill(Color.WHITE);
        label.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        grid.add(label, column, 0);
    }

    private javafx.util.Callback<ListView<LogEntry>, ListCell<LogEntry>> createLogCellFactory(double[] columnWidths) {
        return param -> new ListCell<>() {
            private final GridPane gridPane = new GridPane();
            private final Label dateLabel = new Label();
            private final Label timeLabel = new Label();
            private final Label activityLabel = new Label();
            private final Label skuLabel = new Label();
            private final Label quantityLabel = new Label();
            private final Label descriptionLabel = new Label();

            {
                // Initialize GridPane and constraints once
                gridPane.setHgap(10);

                // Set column constraints
                for (int i = 0; i < 6; i++) {
                    ColumnConstraints column = new ColumnConstraints();
                    column.setPrefWidth(columnWidths[i]);
                    column.setMinWidth(columnWidths[i]);
                    column.setMaxWidth(columnWidths[i]);
                    gridPane.getColumnConstraints().add(column);
                }

                // Add labels to GridPane
                gridPane.add(dateLabel, 0, 0);
                gridPane.add(timeLabel, 1, 0);
                gridPane.add(activityLabel, 2, 0);
                gridPane.add(skuLabel, 3, 0);
                gridPane.add(quantityLabel, 4, 0);
                gridPane.add(descriptionLabel, 5, 0);

                // Set font size for all labels
                String fontStyle = "-fx-font-size: 14px;";
                dateLabel.setStyle(fontStyle);
                timeLabel.setStyle(fontStyle);
                activityLabel.setStyle(fontStyle);
                skuLabel.setStyle(fontStyle);
                quantityLabel.setStyle(fontStyle);
                descriptionLabel.setStyle(fontStyle);

                // Configure text wrapping for description
                descriptionLabel.setWrapText(true);
                descriptionLabel.setMaxWidth(columnWidths[5] - 10);
                descriptionLabel.setMinHeight(Label.USE_COMPUTED_SIZE);
                descriptionLabel.setPrefHeight(Label.USE_COMPUTED_SIZE);
            }

            @Override
            protected void updateItem(LogEntry entry, boolean empty) {
                super.updateItem(entry, empty);

                if (empty || entry == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    // Update labels with entry data
                    dateLabel.setText(entry.getDate());
                    timeLabel.setText(entry.getTime());
                    activityLabel.setText(entry.getActivity());
                    skuLabel.setText(String.valueOf(entry.getSku()));
                    quantityLabel.setText(String.valueOf(entry.getQuantity()));

                    // Truncate description if needed
                    String desc = entry.getDescription();
                    if (desc != null && desc.length() > 20) {
                        descriptionLabel.setText(desc.substring(0, 20) + "...");
                    } else {
                        descriptionLabel.setText(desc);
                    }

                    // Set the cell height to grow as needed based on content
                    setPrefHeight(USE_COMPUTED_SIZE);
                    setGraphic(gridPane);
                }
            }
        };
    }

    private void showLogEntryDetails(LogEntry entry) {
        try {
            // Create a new stage for details
            Stage detailStage = new Stage();
            detailStage.initModality(Modality.APPLICATION_MODAL);
            detailStage.initStyle(StageStyle.DECORATED);
            detailStage.setTitle("Log Entry Details");

            // Create the content pane
            VBox content = new VBox(10);
            content.setPadding(new javafx.geometry.Insets(20));
            content.setStyle("-fx-background-color: white;");

            // Create and add detail fields
            GridPane detailsGrid = new GridPane();
            detailsGrid.setHgap(10);
            detailsGrid.setVgap(10);
            detailsGrid.setPadding(new javafx.geometry.Insets(10));

            // Add field labels and values
            addDetailField(detailsGrid, "Date:", entry.getDate(), 0);
            addDetailField(detailsGrid, "Time:", entry.getTime(), 1);
            addDetailField(detailsGrid, "Activity:", entry.getActivity(), 2);
            addDetailField(detailsGrid, "SKU:", String.valueOf(entry.getSku()), 3);
            addDetailField(detailsGrid, "Item Name:", entry.getItemName(), 4);
            addDetailField(detailsGrid, "Category:", entry.getItemCategory(), 5);
            addDetailField(detailsGrid, "Price:", String.format("₱%.2f", entry.getItemPrice()), 6);
            addDetailField(detailsGrid, "Quantity:", String.valueOf(entry.getQuantity()), 7);

            // Add description with wrapping text area
            Label descLabel = new Label("Description:");
            descLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

            TextArea descArea = new TextArea(entry.getDescription());
            descArea.setWrapText(true);
            descArea.setEditable(false);
            descArea.setPrefHeight(100);
            descArea.setPrefWidth(300);

            // Add close button
            Button closeButton = new Button("Close");
            closeButton.setStyle("-fx-font-size: 14px; -fx-padding: 5 15 5 15;");
            closeButton.setOnAction(e -> detailStage.close());

            // Add all components to content pane
            content.getChildren().addAll(detailsGrid, descLabel, descArea, closeButton);

            // Set alignment for button
            javafx.geometry.Insets buttonMargin = new javafx.geometry.Insets(15, 0, 0, 0);
            VBox.setMargin(closeButton, buttonMargin);
            closeButton.setAlignment(javafx.geometry.Pos.CENTER);

            // Create scene and display
            Scene scene = new Scene(content, 400, 500);
            detailStage.setScene(scene);
            detailStage.showAndWait();

        } catch (Exception e) {
            statusLabel.setText("Error showing details: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void addDetailField(GridPane grid, String label, String value, int row) {
        Label labelField = new Label(label);
        labelField.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        Label valueField = new Label(value != null ? value : "N/A");
        valueField.setStyle("-fx-font-size: 14px;");

        grid.add(labelField, 0, row);
        grid.add(valueField, 1, row);
    }

    @FXML
    private void applyFilters() {
        String typeFilter = typeFilterComboBox.getValue();
        String ascDescFilter = ascOrDescComboBox.getValue();

        if (typeFilter == null || ascDescFilter == null) {
            return;
        }

        // First, apply the primary sorting criteria
        Comparator<LogEntry> primaryComparator = null;

        switch (typeFilter) {
            case "Date and Time":
                // Combined date and time comparison
                primaryComparator = Comparator.comparing(entry -> {
                    try {
                        LocalDate date = LocalDate.parse(entry.getDate(), DATE_FORMATTER);
                        LocalTime time = LocalTime.parse(entry.getTime(), TIME_FORMATTER);
                        return LocalDateTime.of(date, time);
                    } catch (Exception e) {
                        // Fallback to string comparison if parsing fails
                        return LocalDateTime.now(); // Default value
                    }
                });
                break;
            case "SKU":
                primaryComparator = Comparator.comparingInt(LogEntry::getSku);
                break;
            case "Quantity":
                primaryComparator = Comparator.comparingInt(LogEntry::getQuantity);
                break;
        }

        // Apply the sort
        if (primaryComparator != null) {
            filteredLogEntries.sort(primaryComparator);

            // Apply descending order if selected
            if ("Descending".equals(ascDescFilter)) {
                Collections.reverse(filteredLogEntries);
            }
        }

        updateObservableList();
    }

    private void updateObservableList() {
        observableLogList.setAll(filteredLogEntries);
    }

    private void handleSearch(String searchText) {
        String filter = searchFilterComboBox.getValue();
        searchText = searchText.trim().toLowerCase();

        // Reset to full list first to ensure we're searching all items
        List<LogEntry> baseList = new ArrayList<>(logEntries);

        // If no filter or empty search, use full list
        if (filter == null || searchText.isEmpty()) {
            filteredLogEntries.clear();
            filteredLogEntries.addAll(baseList);
        } else {
            // Apply filter based on search criteria
            String finalSearchText = searchText;
            List<LogEntry> searchResults = baseList.stream()
                    .filter(entry -> matchesSearchCriteria(entry, filter, finalSearchText))
                    .collect(Collectors.toList());

            filteredLogEntries.clear();
            filteredLogEntries.addAll(searchResults);
        }

        // Apply current sort filters to maintain consistency
        applyFilters();
    }

    private boolean matchesSearchCriteria(LogEntry entry, String filter, String searchText) {
        switch (filter) {
            case "SKU":
                return String.valueOf(entry.getSku()).contains(searchText);
            case "Description":
                return entry.getDescription() != null &&
                        entry.getDescription().toLowerCase().contains(searchText);
            default:
                return false;
        }
    }

    private void resetToFullList() {
        filteredLogEntries.clear();
        filteredLogEntries.addAll(logEntries);
        applyFilters();
    }

    @FXML
    private void onAddLogEntryButtonClick() throws IOException {
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

        Label loadingLabel = new Label("Loading Log Entry Screen...");
        loadingLabel.setStyle("-fx-font-size: 14px;");

        ProgressIndicator progress = new ProgressIndicator();
        progress.setProgress(-1.0f); // Indeterminate progress

        loadingBox.getChildren().addAll(loadingLabel, progress);

        // Set scene for loading stage
        Scene loadingScene = new Scene(loadingBox, 400, 150);
        loadingStage.setScene(loadingScene);

        // Get current stage to disable it
        Stage currentStage = (Stage) mainLabel.getScene().getWindow();

        // Disable the main window before showing loading dialog
        currentStage.setOnCloseRequest(event -> event.consume()); // Prevent closing while loading
        currentStage.getScene().getRoot().setDisable(true);

        // Show the loading dialog
        loadingStage.show();

        // Use a background thread to load the new screen
        EXECUTOR.submit(() -> {
            try {
                // Load the log entry view in background
                FXMLLoader loader = new FXMLLoader(getClass().getResource("log-entry.fxml"));
                Parent root = loader.load();

                // Get controller and set the branch
                LogEntryController controller = loader.getController();
                controller.setAuthenticatedBranch(currentBranch);

                // Update UI on JavaFX thread
                Platform.runLater(() -> {
                    try {
                        // Close the loading alert
                        loadingStage.close();

                        // Re-enable main window in case of any issue
                        currentStage.setOnCloseRequest(event -> {}); // Reset close handler
                        currentStage.getScene().getRoot().setDisable(false);

                        // Create and show the stage
                        Stage logEntryStage = new Stage();
                        logEntryStage.setTitle("Add Log Entry - " + currentBranch);
                        logEntryStage.setScene(new Scene(root, 1366, 768));
                        logEntryStage.setResizable(false);

                        // Close current window
                        currentStage.close();

                        // Show new window
                        logEntryStage.show();
                    } catch (Exception e) {
                        // Close loading alert in case of error
                        loadingStage.close();

                        // Re-enable main window
                        currentStage.setOnCloseRequest(event -> {}); // Reset close handler
                        currentStage.getScene().getRoot().setDisable(false);

                        // Show error alert
                        showErrorAlert("Failed to load log entry screen", e.getMessage());
                        e.printStackTrace();
                    }
                });
            } catch (IOException e) {
                // Close loading alert in case of error
                Platform.runLater(() -> {
                    loadingStage.close();

                    // Re-enable main window
                    currentStage.setOnCloseRequest(event -> {}); // Reset close handler
                    currentStage.getScene().getRoot().setDisable(false);

                    // Show error alert
                    showErrorAlert("Failed to load log entry screen", e.getMessage());
                });

                e.printStackTrace();
            }
        });
    }

    private void showErrorAlert(String header, String message) {
        Alert errorAlert = new Alert(Alert.AlertType.ERROR);
        errorAlert.setTitle("Error");
        errorAlert.setHeaderText(header);
        errorAlert.setContentText("Error: " + message);
        errorAlert.showAndWait();
    }

    @FXML
    private void onLogoutButtonClick() throws IOException {
        // Load the login screen
        FXMLLoader loader = new FXMLLoader(getClass().getResource("login.fxml"));
        Parent root = loader.load();

        // Create a new stage for the login screen
        Stage loginStage = new Stage();
        loginStage.setTitle("VRM Inventory System - Login");
        loginStage.setScene(new Scene(root, 720, 720));
        loginStage.setResizable(false);

        // Close current window
        Stage currentStage = (Stage) mainLabel.getScene().getWindow();
        currentStage.close();

        // Show login window
        loginStage.show();
    }

    // Add this method to MainViewController.java
    public void setBranch(String branch) {
        this.currentBranch = branch;
        if (branchLabel != null) {
            branchLabel.setText(branch);
        }
        if (mainLabel != null) {
            mainLabel.setText(branch.toUpperCase() + " Log Sheet");
        }

        // Refresh data with the new branch
        try {
            if (sheetsService != null) {
                logEntries = getLogEntries(currentBranch);
                filteredLogEntries = new ArrayList<>(logEntries);
                if (observableLogList != null) {
                    observableLogList.setAll(filteredLogEntries);
                }
            }
        } catch (IOException e) {
            if (statusLabel != null) {
                statusLabel.setText("Error loading data for branch: " + e.getMessage());
            }
            e.printStackTrace();
        }
    }

    // Add this method to MainViewController.java
    public void shutdown() {
        // Shutdown any background tasks
        if (EXECUTOR != null && !EXECUTOR.isShutdown()) {
            EXECUTOR.shutdownNow();
        }

        // Add any other cleanup needed
        // ...
    }
}