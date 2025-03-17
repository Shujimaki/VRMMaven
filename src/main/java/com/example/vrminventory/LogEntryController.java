package com.example.vrminventory;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Callback;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

public class LogEntryController {
    // Constants
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);


    // Lists
    private static final List<String> BRANCH_LIST = List.of("Branch1", "Branch2", "Branch3", "Warehouse");
    private static final List<String> ACTIVITY_LIST = List.of("Sale", "Transfer-In", "Transfer-Out", "Return/Refund");
    private static final List<String> SEARCH_FILTERS = List.of("SKU", "Name", "Category");
    private static final List<String> TYPE_FILTERS = List.of("SKU", "Alphabetical", "Price", "Quantity");
    private static final List<String> ASC_DESC_FILTERS = List.of("Ascending", "Descending");

    // Fields
    private GoogleSheetsService sheetsService;
    private List<InventoryItem> itemList;
    private List<InventoryItem> filteredItemList;
    private BST skuBST;

    // FXML components
    @FXML public static Stage logEntryStage;
    @FXML private ComboBox<String> branchComboBox;
    @FXML private TextField SKUField;
    @FXML private Label mainLabel;
    @FXML private Label statusLabel;
    @FXML private ComboBox<String> activityComboBox;
    @FXML private Spinner<Integer> quantitySpinner;
    @FXML private VBox entryVBox;
    @FXML private ListView<InventoryItem> itemListView;
    @FXML private VBox listViewContainer;
    @FXML private ComboBox<String> searchFilterComboBox;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> typeFilterComboBox;
    @FXML private ComboBox<String> ascOrDescComboBox;
    @FXML private TextField descriptionField; // Added for description input

    private ObservableList<InventoryItem> observableItemList;
    private String currentBranch = "Branch1"; // Default branch

    public LogEntryController() {
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
            // Initialize data
            initializeData();

            // Set up UI components
            setupUIComponents();

            // Set up event listeners
            setupEventListeners();

            // Initialize filter at program start
            typeFilterComboBox.setValue("Alphabetical");
            ascOrDescComboBox.setValue("Ascending");
            applyFilters();

            // Check if we're coming from login
            String loginBranch = LoginController.getCurrentBranch();
            if (loginBranch != null && !loginBranch.isEmpty()) {
                setAuthenticatedBranch(loginBranch);
                mainLabel.setText(loginBranch.toUpperCase() + " Inventory List");
            }

        } catch (Exception e) {
            statusLabel.setText("Initialization error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void initializeData() throws IOException {
        if (sheetsService == null) {
            throw new IOException("Google Sheets service is not initialized");
        }

        itemList = sheetsService.getAllInventoryItems(currentBranch);
        filteredItemList = new ArrayList<>(itemList);
        skuBST = new BST();
        addItemsToBST();
        observableItemList = FXCollections.observableArrayList(filteredItemList);
    }

    // New method to refresh data from Google Sheets
    public void refreshData() {
        try {
            // Clear cache to ensure fresh data
            sheetsService.clearCache();

            // Get current branch selection
            String selectedBranch = branchComboBox.getValue();
            if (selectedBranch != null) {
                currentBranch = selectedBranch;
            }

            // Retrieve fresh data from Google Sheets
            itemList = sheetsService.getAllInventoryItems(currentBranch);

            // Clear and rebuild the BST
            skuBST = new BST();
            addItemsToBST();

            // Update filtered list
            String searchFilter = searchFilterComboBox.getValue();
            String searchText = searchField.getText();

            if (searchFilter != null && !searchText.isEmpty()) {
                // Apply search filter if active
                handleSearch(searchText);
            } else {
                // Otherwise reset to full list
                filteredItemList.clear();
                filteredItemList.addAll(itemList);
                applyFilters();
            }

            // Update status
            statusLabel.setText("Data refreshed successfully");
        } catch (IOException e) {
            statusLabel.setText("Error refreshing data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void setupUIComponents() {
        setupListView();
        branchComboBox.getItems().addAll(BRANCH_LIST);

        // If not set from login, use default
        if (LoginController.getCurrentBranch() == null) {
            branchComboBox.setValue(currentBranch); // Set default branch
        }

        // Set up activity combo box - will be populated in setAuthenticatedBranch
        searchFilterComboBox.getItems().addAll(SEARCH_FILTERS);
        typeFilterComboBox.getItems().addAll(TYPE_FILTERS);
        ascOrDescComboBox.getItems().addAll(ASC_DESC_FILTERS);
        initializeQuantitySpinner();

        searchField.setDisable(true);
        searchFilterComboBox.setValue(null);
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

        // Add listener for branch change
        branchComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.equals(oldVal)) {
                currentBranch = newVal;
                refreshData();
            }
        });
    }

    // Method to set the authenticated branch and configure UI accordingly
    public void setAuthenticatedBranch(String branch) {
        if (branch != null && !branch.isEmpty()) {
            currentBranch = branch;

            // Set branch in combo box and disable editing
            branchComboBox.setValue(branch);
            branchComboBox.setDisable(true);

            // Update activity list based on branch
            activityComboBox.getItems().clear();
            if ("Warehouse".equals(branch)) {
                activityComboBox.getItems().addAll("Supply", "Transfer-Out");
            } else {
                activityComboBox.getItems().addAll(ACTIVITY_LIST);
            }

            // Refresh data for the selected branch
            refreshData();
        }
    }

    @FXML
    private void initializeQuantitySpinner() {
        SpinnerValueFactory.IntegerSpinnerValueFactory valueFactory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, Integer.MAX_VALUE, 1);
        quantitySpinner.setValueFactory(valueFactory);

        UnaryOperator<TextFormatter.Change> filter = change ->
                change.getControlNewText().matches("\\d*") ? change : null;

        quantitySpinner.getEditor().setTextFormatter(new TextFormatter<>(filter));

        // Set the font size and family for the spinner's editor TextField
        quantitySpinner.getEditor().setStyle("-fx-font-size: 20px; -fx-font-family: 'Arial';");

        // Optional: Adjust the spinner's overall style for better appearance with the new font
        quantitySpinner.setStyle("-fx-font-size: 20px;");
    }

    @FXML
    private void setupListView() {
        // Create and configure header GridPane
        GridPane headers = createHeaderGridPane();
        listViewContainer.getChildren().add(0, headers);

        // Calculate column widths
        double totalWidth = itemListView.getPrefWidth();
        double[] columnWidths = {
                totalWidth * 0.22,  // SKU column
                totalWidth * 0.25,  // Name column
                totalWidth * 0.24,  // Category column
                totalWidth * 0.25,  // Price column
                totalWidth * 0.14   // Quantity column
        };

        // Configure ListView with custom cell factory
        itemListView.setCellFactory(createItemCellFactory(columnWidths));

        // Remove fixed cell size to allow dynamic height based on content
        itemListView.setFixedCellSize(Region.USE_COMPUTED_SIZE);

        // Populate the ListView
        itemListView.setItems(observableItemList);
    }

    private GridPane createHeaderGridPane() {
        GridPane headers = new GridPane();
        headers.setHgap(10);
        headers.setPrefWidth(itemListView.getPrefWidth());

        // Add header labels
        addHeaderLabel(headers, "SKU", 0);
        addHeaderLabel(headers, "Name", 1);
        addHeaderLabel(headers, "Category", 2);
        addHeaderLabel(headers, "Price", 3);
        addHeaderLabel(headers, "Quantity", 4);

        // Set column constraints
        for (int i = 0; i < 5; i++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(20);
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

    private Callback<ListView<InventoryItem>, ListCell<InventoryItem>> createItemCellFactory(double[] columnWidths) {
        return param -> new ListCell<>() {
            private final GridPane gridPane = new GridPane();
            private final Label skuLabel = new Label();
            private final Label nameLabel = new Label();
            private final Label categoryLabel = new Label();
            private final Label priceLabel = new Label();
            private final Label quantityLabel = new Label();

            {
                // Initialize GridPane and constraints once
                gridPane.setHgap(10);

                // Set column constraints
                for (int i = 0; i < 5; i++) {
                    ColumnConstraints column = new ColumnConstraints();
                    column.setPrefWidth(columnWidths[i]);
                    column.setMinWidth(columnWidths[i]);
                    column.setMaxWidth(columnWidths[i]);
                    gridPane.getColumnConstraints().add(column);
                }

                // Add labels to GridPane
                gridPane.add(skuLabel, 0, 0);
                gridPane.add(nameLabel, 1, 0);
                gridPane.add(categoryLabel, 2, 0);
                gridPane.add(priceLabel, 3, 0);
                gridPane.add(quantityLabel, 4, 0);

                // Set font size for all labels
                String fontStyle = "-fx-font-size: 14px;";
                skuLabel.setStyle(fontStyle);
                nameLabel.setStyle(fontStyle);
                categoryLabel.setStyle(fontStyle);
                priceLabel.setStyle(fontStyle);
                quantityLabel.setStyle(fontStyle);

                // Configure text wrapping
                nameLabel.setWrapText(true);
                nameLabel.setMaxWidth(columnWidths[1] - 10);
                // Ensure the label doesn't get clipped
                nameLabel.setMinHeight(Label.USE_COMPUTED_SIZE);
                nameLabel.setPrefHeight(Label.USE_COMPUTED_SIZE);

                categoryLabel.setWrapText(true);
                categoryLabel.setMaxWidth(columnWidths[2] - 10);
                // Ensure the label doesn't get clipped
                categoryLabel.setMinHeight(Label.USE_COMPUTED_SIZE);
                categoryLabel.setPrefHeight(Label.USE_COMPUTED_SIZE);
            }

            @Override
            protected void updateItem(InventoryItem item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    // Update labels with item data
                    skuLabel.setText(String.valueOf(item.getSku()));
                    nameLabel.setText(item.getName());
                    categoryLabel.setText(item.getCategory());
                    priceLabel.setText(String.format("₱%.2f", item.getPrice()));
                    quantityLabel.setText(String.valueOf(item.getQuantity()));

                    // Set the cell height to grow as needed based on content
                    setPrefHeight(USE_COMPUTED_SIZE);
                    setGraphic(gridPane);
                }
            }
        };
    }

    @FXML
    private void applyFilters() {
        String typeFilter = typeFilterComboBox.getValue();
        String ascDescFilter = ascOrDescComboBox.getValue();

        if (typeFilter == null || ascDescFilter == null) {
            return;
        }

        // First, apply the primary sorting criteria
        Comparator<InventoryItem> primaryComparator = null;

        switch (typeFilter) {
            case "SKU":
                primaryComparator = Comparator.comparingInt(InventoryItem::getSku);
                break;
            case "Alphabetical":
                primaryComparator = Comparator.comparing(item -> item.getName().trim().toLowerCase());
                break;
            case "Price":
                primaryComparator = Comparator.comparingDouble(InventoryItem::getPrice);
                break;
            case "Quantity":
                primaryComparator = Comparator.comparingInt(InventoryItem::getQuantity);
                break;
        }

        // Add secondary sorting by name for Price and Quantity
        if ("Price".equals(typeFilter) || "Quantity".equals(typeFilter)) {
            primaryComparator = primaryComparator.thenComparing(InventoryItem::getName);
        }

        // Apply the sorted comparator
        filteredItemList.sort(primaryComparator);

        // Apply descending order if selected
        if ("Descending".equals(ascDescFilter)) {
            Collections.reverse(filteredItemList);
        }

        updateObservableList();
    }

    @FXML
    protected void onHelloButtonClick() {
        // Validate input
        if (!validateInput()) {
            return;
        }

        // Prepare data and call a method to handle the log entry
        handleLogEntry();

    }

    @FXML
    private void handleLogEntry() {
        // Logic for handling log entry, including API calls
        try {
            int sku = Integer.parseInt(SKUField.getText());
            String branch = branchComboBox.getValue();
            String activity = activityComboBox.getValue();
            int quantity = quantitySpinner.getValue();
            String description = descriptionField != null ? descriptionField.getText() : "";

            // Find item details for the SKU
            InventoryItem item = findItemBySku(sku);
            if (item == null) {
                statusLabel.setText("SKU not found in inventory.");
                return;
            }

            // Create confirmation alert with custom content
            Alert confirmationAlert = new Alert(Alert.AlertType.CONFIRMATION);
            confirmationAlert.setTitle("Confirm Log Entry");
            confirmationAlert.setHeaderText("Please confirm the following details:");

            // Create grid pane for organized data display
            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(10);
            grid.setPadding(new Insets(20, 150, 10, 10));

            // Add entry details
            addDetailRow(grid, "Branch:", branch, 0);
            addDetailRow(grid, "SKU:", String.valueOf(sku), 1);
            addDetailRow(grid, "Name:", item.getName(), 2);
            addDetailRow(grid, "Category:", item.getCategory(), 3);
            addDetailRow(grid, "Price:", String.format("₱%.2f", item.getPrice()), 4);
            addDetailRow(grid, "Activity:", activity, 5);
            addDetailRow(grid, "Quantity:", String.valueOf(quantity), 6);
            addDetailRow(grid, "Description:", description, 7);

            // Add the grid to dialog pane
            confirmationAlert.getDialogPane().setContent(grid);

            // Show confirmation dialog
            Optional<ButtonType> result = confirmationAlert.showAndWait();

            if (result.isPresent() && result.get() == ButtonType.OK) {
                // Proceed with log entry
                confirmLogEntry(branch, sku, activity, quantity, description);
            }

        } catch (Exception e) {
            Logger.logError("Failed to handle log entry", e);
            showErrorAlert("Log Entry Error", "An error occurred while processing the log entry.");
        }
    }

    // Helper method to add detail rows to the grid
    private void addDetailRow(GridPane grid, String label, String value, int row) {
        grid.add(new Label(label), 0, row);
        Label valueLabel = new Label(value);
        valueLabel.setWrapText(true);
        grid.add(valueLabel, 1, row);
    }

    private InventoryItem findItemBySku(int sku) {
        for (InventoryItem item : itemList) {
            if (item.getSku() == sku) {
                return item;
            }
        }
        return null; // SKU not found
    }

    private void confirmLogEntry(String branch, int sku, String activity, int quantity, String description) {
        // Create processing alert
        Alert processingAlert = new Alert(Alert.AlertType.INFORMATION);
        processingAlert.setTitle("Processing");
        processingAlert.setHeaderText(null);
        processingAlert.setContentText("Processing...");
        processingAlert.show();

        // Create a task for background processing
        Task<Integer> task = new Task<>() {
            @Override
            protected Integer call() throws Exception {
                // Prepare data
                List<Object> dataToWrite;

                if (branch.equals("Warehouse")) {
                    int activityCode = "Supply".equals(activity) ? 1 : 2; // 1 for Supply, 2 for Transfer-Out
                    dataToWrite = Arrays.asList(
                            LocalDate.now().format(DATE_FORMATTER),
                            LocalTime.now().format(TIME_FORMATTER),
                            activityCode,
                            sku,
                            quantity,
                            description
                    );
                } else {
                    int activityCode = switch (activity) {
                        case "Sale" -> 1;
                        case "Transfer-In" -> 2;
                        case "Transfer-Out" -> 3;
                        case "Return/Refund" -> 4;
                        default -> throw new IllegalArgumentException("Invalid activity");
                    };
                    dataToWrite = Arrays.asList(
                            LocalDate.now().format(DATE_FORMATTER),
                            LocalTime.now().format(TIME_FORMATTER),
                            activityCode,
                            sku,
                            quantity,
                            description
                    );
                }

                // Find next row and write data
                String branchPrefix = branch + "!";
                String range = sheetsService.findNextRow(branchPrefix);
                return sheetsService.writeData(range, dataToWrite);
            }
        };

        task.setOnSucceeded(event -> {
            processingAlert.close();
            int cellsUpdated = task.getValue();
            Alert resultAlert = new Alert(Alert.AlertType.INFORMATION);
            resultAlert.setTitle("Log Entry Result");
            resultAlert.setHeaderText(null);
            resultAlert.setContentText(cellsUpdated > 0 ? "Log entry added successfully." : "Failed to update log.");
            resultAlert.show();
            refreshData(); // Refresh data after adding log
            clearFields();
        });

        task.setOnFailed(event -> {
            processingAlert.close();
            Alert failAlert = new Alert(Alert.AlertType.ERROR);
            failAlert.setTitle("Log Entry Failed");
            failAlert.setHeaderText(null);
            failAlert.setContentText("Failed to update log: " + task.getException().getMessage());
            failAlert.show();
        });

        // Start the task in a background thread
        new Thread(task).start();
    }



    @FXML
    protected void onBackButtonClick() throws IOException {

        Stage loadingStage = new Stage();
        showLoadingAlert(loadingStage, "Loading Main View...");
        Stage currentStage = (Stage) entryVBox.getScene().getWindow();
        currentStage.setOnCloseRequest(event -> event.consume()); // Prevent closing
        currentStage.getScene().getRoot().setDisable(true);
        loadingStage.show();

        // Load in BG
        EXECUTOR.submit(()-> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("main-view.fxml"));
                Parent root = loader.load();

                MainViewController mainViewController = loader.getController();
                mainViewController.setBranch(currentBranch);

                //Update UI
                Platform.runLater(()-> {
                    try {
                        loadingStage.close();
                        currentStage.setOnCloseRequest(event -> {}); // Reset
                        currentStage.getScene().getRoot().setDisable(false);

                        Stage mainStage = new Stage();
                        mainStage.setTitle("VRM Inventory - " + currentBranch);
                        mainStage.setScene(new Scene(root, 1366, 768));
                        mainStage.setMaximized(false);
                        mainStage.setResizable(false);
                        mainStage.setOnCloseRequest(e -> {
                            mainViewController.shutdown();
                            Platform.exit();
                            System.exit(0);
                        });
                        currentStage.close();
                        mainStage.show();



                    } catch (Exception e) {
                        loadingStage.close();
                        currentStage.setOnCloseRequest(event -> {}); // Reset close handler
                        currentStage.getScene().getRoot().setDisable(false);
                        showErrorAlert("Failed to load main view screen", e.getMessage());
                        e.printStackTrace();

                    }

                });

            } catch (IOException e) {
                Platform.runLater(() -> {
                    loadingStage.close();

                    // Re-enable main window
                    currentStage.setOnCloseRequest(event -> {}); // Reset close handler
                    currentStage.getScene().getRoot().setDisable(false);

                    // Show error alert
                    showErrorAlert("Failed to load main view screen", e.getMessage());
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
        errorAlert.initModality(Modality.APPLICATION_MODAL); // Make sure it's modal
        errorAlert.initStyle(StageStyle.UNDECORATED);       // Remove decorations
        errorAlert.showAndWait();
    }
    private void showLoadingAlert(Stage loadingStage, String message) {
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
    }


    private void clearFields() {
        SKUField.clear();
        quantitySpinner.getValueFactory().setValue(1);
        if (descriptionField != null) {
            descriptionField.clear();
        }
        statusLabel.setText("Log entry complete");
    }

    private boolean validateInput() {
        // Validate SKU
        if (SKUField.getText().isEmpty()) {
            statusLabel.setText("SKU is empty");
            return false;
        }

        int sku;
        try {
            sku = Integer.parseInt(SKUField.getText());
            if (!isValidSKU(sku)) {
                statusLabel.setText("Invalid SKU");
                return false;
            }
        } catch (NumberFormatException e) {
            statusLabel.setText("Invalid SKU");
            return false;
        }

        // Validate branch and activity
        if (branchComboBox.getValue() == null || activityComboBox.getValue() == null) {
            statusLabel.setText("Please select branch and activity");
            return false;
        }

        return true;
    }

    private void addItemsToBST() {
        for (InventoryItem item : itemList) {
            skuBST.insert(item.getSku());
        }
    }

    private boolean isValidSKU(int sku) {
        return skuBST.search(sku);
    }

    // Reset to the full list
    private void resetToFullList() {
        filteredItemList.clear();
        filteredItemList.addAll(itemList);
        applyFilters();
    }

    // Perform search based on filter and search text
    @FXML
    private void handleSearch(String searchText) {
        String filter = searchFilterComboBox.getValue();
        searchText = searchText.trim().toLowerCase();

        // Reset to full list first to ensure we're searching all items
        List<InventoryItem> baseList = new ArrayList<>(itemList);

        // If no filter or empty search, use full list
        if (filter == null || searchText.isEmpty()) {
            filteredItemList.clear();
            filteredItemList.addAll(baseList);
        } else {
            // Apply filter based on search criteria
            String finalSearchText = searchText;
            List<InventoryItem> searchResults = baseList.stream()
                    .filter(item -> matchesSearchCriteria(item, filter, finalSearchText))
                    .collect(Collectors.toList());

            filteredItemList.clear();
            filteredItemList.addAll(searchResults);
        }

        // Apply current sort filters to maintain consistency
        applyFilters();
    }

    // Helper method to check if an item matches search criteria
    private boolean matchesSearchCriteria(InventoryItem item, String filter, String searchText) {
        switch (filter) {
            case "SKU":
                return String.valueOf(item.getSku()).startsWith(searchText);
            case "Name":
                return item.getName().toLowerCase().contains(searchText);
            case "Category":
                return item.getCategory().toLowerCase().contains(searchText);
            default:
                return false;
        }
    }

    private void updateObservableList() {
        observableItemList.setAll(filteredItemList);
    }

    // In LogEntryController.java
    public void shutdown() {
        // Shutdown this controller's executor
        if (!EXECUTOR.isShutdown()) {
            EXECUTOR.shutdownNow(); // Force immediate shutdown
        }

        // Also try to shutdown any executor in the Google Sheets service
        // This is important as HTTP client pools might still be running
        if (sheetsService != null && sheetsService.getSheetsService() != null) {
            try {
                sheetsService.getSheetsService().getRequestFactory().getTransport().shutdown();
            } catch (Exception e) {
                System.err.println("Error shutting down HTTP transport: " + e.getMessage());
            }
        }
    }
}