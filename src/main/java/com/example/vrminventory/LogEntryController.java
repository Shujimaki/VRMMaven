package com.example.vrminventory;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
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
            }

            mainLabel.setText(loginBranch.toUpperCase() + " Inventory List");

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
                primaryComparator = Comparator.comparing(InventoryItem::getName);
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

        try {
            // Load confirmation dialog
            FXMLLoader loader = new FXMLLoader(getClass().getResource("log-entry-confirm.fxml"));
            LogEntryConfirmController confirmController = new LogEntryConfirmController(
                    sheetsService,
                    branchComboBox.getValue(),
                    Integer.parseInt(SKUField.getText()),
                    activityComboBox.getValue(),
                    quantitySpinner.getValue(),
                    descriptionField != null ? descriptionField.getText() : "TextFieldDesc",
                    this // Pass reference to this controller for callback
            );
            loader.setController(confirmController);

            Parent root = loader.load();

            // Create and show the confirmation stage
            Stage confirmStage = new Stage();
            confirmStage.initModality(Modality.APPLICATION_MODAL);
            confirmStage.initStyle(StageStyle.UNDECORATED);
            confirmStage.setScene(new Scene(root));
            confirmStage.showAndWait();

            // Clear fields if confirmed successfully
            if (confirmController.isConfirmed()) {
                clearFields();
                // No need to call refreshData here, as it's called from confirm controller
            }
        } catch (IOException e) {
            statusLabel.setText("Error loading confirmation dialog: " + e.getMessage());
            e.printStackTrace();
        }
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