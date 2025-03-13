package com.example.vrminventory;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
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
    @FXML private Label welcomeText;
    @FXML private ComboBox<String> branchComboBox;
    @FXML private TextField SKUField;
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

    private ObservableList<InventoryItem> observableItemList;

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

        } catch (Exception e) {
            statusLabel.setText("Initialization error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void initializeData() throws IOException {
        if (sheetsService == null) {
            throw new IOException("Google Sheets service is not initialized");
        }

        itemList = sheetsService.getAllInventoryItems("Branch1");
        filteredItemList = new ArrayList<>(itemList);
        skuBST = new BST();
        addItemsToBST();
        observableItemList = FXCollections.observableArrayList(filteredItemList);
    }

    private void setupUIComponents() {
        setupListView();
        branchComboBox.getItems().addAll(BRANCH_LIST);
        activityComboBox.getItems().addAll(ACTIVITY_LIST);
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

        int sku = Integer.parseInt(SKUField.getText());

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                entryVBox.setDisable(true);

                // Prepare data
                List<Object> dataToWrite = Arrays.asList(
                        LocalDate.now().format(DATE_FORMATTER),
                        LocalTime.now().format(TIME_FORMATTER),
                        ACTIVITY_LIST.indexOf(activityComboBox.getValue()) + 1,
                        sku,
                        quantitySpinner.getValue(),
                        "TextFieldDesc"
                );

                // Find next row and write data
                String branch = branchComboBox.getValue() + "!";
                String range = sheetsService.findNextRow(branch);
                int cellsUpdated = sheetsService.writeData(range, dataToWrite);

                System.out.printf("%d cells updated.%n", cellsUpdated);

                return null;
            }
        };

        setupTaskHandlers(task);
        EXECUTOR.submit(task);
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

    private void setupTaskHandlers(Task<Void> task) {
        task.setOnSucceeded(event -> {
            statusLabel.setText("Success");
            entryVBox.setDisable(false);
        });

        task.setOnFailed(event -> {
            Throwable exception = task.getException();
            statusLabel.setText("Failed: " + exception.getMessage());
            exception.printStackTrace();
            entryVBox.setDisable(false);
        });

        task.setOnRunning(event -> {
            statusLabel.setText("Processing...");
        });
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

    // Make sure to shut down the executor when the application closes
    public void shutdown() {
        EXECUTOR.shutdown();
    }
}