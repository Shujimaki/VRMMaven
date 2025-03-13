package com.example.vrmmaven;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.UpdateValuesResponse;
import com.google.api.services.sheets.v4.model.ValueRange;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Callback;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

public class HelloController {
    // Constants
    private static final String APPLICATION_NAME = "VRM Inventory System";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";
    private static final List<String> SCOPES = Collections.singletonList(SheetsScopes.SPREADSHEETS);
    private static final String CREDENTIALS_FILE_PATH = "/credentials.json";
    private static final String SPREADSHEET_ID = "1ztEDIb6npREKC6a9uJxjlQFkFGGgwDfL_v2Lpo2aSdc";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int START_ROW = 21;
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);

    // Lists
    private static final List<String> BRANCH_LIST = List.of("Branch1", "Branch2", "Branch3", "Warehouse");
    private static final List<String> ACTIVITY_LIST = List.of("Sale", "Transfer-In", "Transfer-Out", "Return/Refund");
    private static final List<String> SEARCH_FILTERS = List.of("SKU", "Name", "Category");
    private static final List<String> TYPE_FILTERS = List.of(
            "SKU Ascending", "SKU Descending",
            "Alphabetical Ascending", "Alphabetical Descending",
            "Price Ascending", "Price Descending",
            "Quantity Ascending", "Quantity Descending");


    // Fields
    private final Sheets service;
    private List<InventoryItem> itemList; //Original list, don't modify this
    private List<InventoryItem> filteredItemList;  //For holding the items the search yields
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


    private ObservableList<InventoryItem> observableItemList;

    public HelloController() throws GeneralSecurityException, IOException {
        NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        service = new Sheets.Builder(httpTransport, JSON_FACTORY, getCredentials(httpTransport))
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    @FXML
    private void initialize() {
        welcomeText.setText("Hello World!");

        try {
            itemList = getAllInventoryItems();
            filteredItemList = new ArrayList<>(itemList);  // initialize here with everything from list
            skuBST = new BST();
            addItemsToBST();

            // Setup UI components
            observableItemList = FXCollections.observableArrayList(filteredItemList);  // IMPORTANT Use FILITERED ITEM LIST
            setupListView();
            branchComboBox.getItems().addAll(BRANCH_LIST);
            activityComboBox.getItems().addAll(ACTIVITY_LIST);
            searchFilterComboBox.getItems().addAll(SEARCH_FILTERS);
            typeFilterComboBox.getItems().addAll(TYPE_FILTERS);
            initializeQuantitySpinner();

            // Initially disable the search field
            searchField.setDisable(true);


            // Set default selected value when code starts instead. And also run action listener trigger function on
            searchFilterComboBox.setValue(null);        // Added so action below trigger


            // Listen for changes in the search combo box
            searchFilterComboBox.valueProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue != null && !newValue.isEmpty()) {
                    searchField.setDisable(false);     // Enable when a search type is selected
                    searchField.clear();             // Clear the search field
                    handleSearch(searchField.getText());// Added force action as no searchtext yet, to avoid empty issues at beginning/action
                    // Reset list to original state by pushing ALL the original data
                    //filteredItemList.clear();          // REMOVED
                    //filteredItemList.addAll(itemList);       // Rmoved those

                } else {
                    searchField.setDisable(true);         // Disable if nothing is selected
                    filteredItemList.clear();          // Re added for non seatch selection filter as per user request

                    filteredItemList.addAll(itemList); // Adds everything to

                    updateObservableList();                // Make it true


                }
            });


            // Search Functionality - Listener on the Search Text Field
            searchField.textProperty().addListener((observable, oldValue, newValue) -> handleSearch(newValue));


            // Type Filter Combo Box Listener
            typeFilterComboBox.valueProperty().addListener((obs, oldVal, newVal) -> handleTypeFilter(newVal));

        } catch (Exception e) {
            statusLabel.setText("Initialization error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void initializeQuantitySpinner() {
        // Set up spinner value factory
        SpinnerValueFactory.IntegerSpinnerValueFactory valueFactory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, Integer.MAX_VALUE, 1);
        quantitySpinner.setValueFactory(valueFactory);

        // Filter to only allow positive integers
        UnaryOperator<TextFormatter.Change> filter = change ->
                change.getControlNewText().matches("\\d*") ? change : null;

        quantitySpinner.getEditor().setTextFormatter(new TextFormatter<>(filter));
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

        // Populate the ListView
        itemListView.setItems(observableItemList); // Use the observable list


    }

    private GridPane createHeaderGridPane() {
        GridPane headers = new GridPane();
        headers.setHgap(10);
        headers.setPrefWidth(itemListView.getPrefWidth());

        // Add header labels with consistent style
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

                // Configure text wrapping
                nameLabel.setMaxWidth(columnWidths[1] - 10);
                nameLabel.setWrapText(true);
                categoryLabel.setMaxWidth(columnWidths[2] - 10);
                categoryLabel.setWrapText(true);
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
                    setGraphic(gridPane);
                }
            }
        };
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
                String range = findNextRow(branch, service, SPREADSHEET_ID);
                writeData(service, SPREADSHEET_ID, range, dataToWrite);

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

    private List<InventoryItem> getAllInventoryItems() throws IOException {
        List<InventoryItem> items = new ArrayList<>();

        // Get sheet metadata
        Spreadsheet spreadsheet = service.spreadsheets().get(SPREADSHEET_ID).execute();
        Sheet inventorySheet = spreadsheet.getSheets().stream()
                .filter(sheet -> "Branch1".equals(sheet.getProperties().getTitle()))
                .findFirst()
                .orElseThrow(() -> new IOException("Branch1 sheet not found"));

        // Fetch data from sheet
        String range = "Branch1!B21:F";
        ValueRange response = service.spreadsheets().values()
                .get(SPREADSHEET_ID, range)
                .execute();

        if (response.getValues() != null) {
            for (List<Object> row : response.getValues()) {
                if (row.size() >= 5) {
                    try {
                        int sku = Integer.parseInt(row.get(0).toString());
                        String name = row.get(1).toString();
                        String category = row.get(2).toString();
                        double price = Double.parseDouble(row.get(3).toString());
                        int quantity = Integer.parseInt(row.get(4).toString());

                        items.add(new InventoryItem(sku, name, category, price, quantity));
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid number format: " + e.getMessage());
                    }
                }
            }
        }

        return items;
    }

    private void addItemsToBST() {
        for (InventoryItem item : itemList) {
            skuBST.insert(item.getSku());
        }
    }

    private boolean isValidSKU(int sku) {
        return skuBST.search(sku);
    }

    /**
     * Creates an authorized Credential object.
     *
     * @param httpTransport The network HTTP Transport.
     * @return An authorized Credential object.
     * @throws IOException If the credentials.json file cannot be found.
     */
    private static Credential getCredentials(final NetHttpTransport httpTransport) throws IOException {
        // Load client secrets
        InputStream in = SheetsQuickstart.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
        if (in == null) {
            throw new FileNotFoundException("Resource not found: " + CREDENTIALS_FILE_PATH);
        }

        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        // Build flow and trigger user authorization request
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();

        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();

        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }

    public static String findNextRow(String branch, Sheets service, String spreadsheetId) throws IOException {
        String range = branch + "I" + START_ROW + ":N";
        ValueRange response = service.spreadsheets().values()
                .get(spreadsheetId, range)
                .execute();

        if (response.getValues() == null || response.getValues().isEmpty()) {
            return branch + "I" + START_ROW + ":N" + START_ROW;
        }

        // Find first empty row
        for (int i = 0; i < response.getValues().size(); i++) {
            if (response.getValues().get(i).isEmpty()) {
                int rowNum = START_ROW + i;
                return branch + "I" + rowNum + ":N" + rowNum;
            }
        }

        // If no empty row found, return next row after last fetched row
        int nextRow = START_ROW + response.getValues().size();
        return branch + "I" + nextRow + ":N" + nextRow;
    }

    public static void writeData(Sheets service, String spreadsheetId, String range, List<Object> data) throws IOException {
        ValueRange body = new ValueRange().setValues(Collections.singletonList(data));

        UpdateValuesResponse result = service.spreadsheets().values()
                .update(spreadsheetId, range, body)
                .setValueInputOption("USER_ENTERED")
                .execute();

        System.out.printf("%d cells updated.%n", result.getUpdatedCells());
    }

    // Overload the handleSearch to receive newText from listener
    @FXML
    private void handleSearch(String newText) {
        String filter = searchFilterComboBox.getValue();
        String searchText = newText.trim().toLowerCase(); // Take the updated value

        List<InventoryItem> searchResult;


        if (filter == null || searchText.isEmpty()) {             // Re added full set here from source

            searchResult = new ArrayList<>(itemList);


        } else {            // Important so works and pulls if google code still works, can be separate call to separate that too to prevent slowdown, we can push it down for even easier understanding later with some calls

            searchResult = itemList.stream()

                    .filter(item -> {

                        switch (filter) {

                            case "SKU":

                                try {

                                    return String.valueOf(item.getSku()).startsWith(searchText);   // Partial character matching

                                } catch (NumberFormatException e) {

                                    return false;                                                   // Handle invalid number format if needed

                                }

                            case "Name":

                                return item.getName().toLowerCase().contains(searchText);          // Partial String matching

                            case "Category":

                                return item.getCategory().toLowerCase().contains(searchText);     // Partial String matching

                            default:

                                return false;

                        }

                    })

                    .collect(Collectors.toList());
        }



        filteredItemList.clear();           // clean up previous action

        filteredItemList.addAll(searchResult);           // re set data from

        applyTypeFilter();                            // Important so sort actions keeps doing

    }


    @FXML
    private void handleTypeFilter(String filterType) {            // IMPORTANT THIS FUNCTION WAS FOR INITIAL OR USER SELECTION ACTION

        if (filterType == null || filterType.isEmpty()) {

            updateObservableList();            // Update so data updates no stop here or break is required

            return;

        }


        switch (filterType) {

            case "SKU Ascending":

                filteredItemList.sort(Comparator.comparingInt(InventoryItem::getSku));

                break;

            case "SKU Descending":

                filteredItemList.sort((a, b) -> Integer.compare(b.getSku(), a.getSku()));

                break;

            case "Alphabetical Ascending":

                filteredItemList.sort(Comparator.comparing(InventoryItem::getName));

                break;

            case "Alphabetical Descending":

                filteredItemList.sort((a, b) -> b.getName().compareTo(a.getName()));

                break;

            case "Price Ascending":

                filteredItemList.sort(Comparator.comparingDouble(InventoryItem::getPrice));

                break;

            case "Price Descending":

                filteredItemList.sort((a, b) -> Double.compare(b.getPrice(), a.getPrice()));

                break;

            case "Quantity Ascending":

                filteredItemList.sort(Comparator.comparingInt(InventoryItem::getQuantity));

                break;

            case "Quantity Descending":

                filteredItemList.sort((a, b) -> Integer.compare(b.getQuantity(), a.getQuantity()));

                break;

        }


        updateObservableList();         // MUST push again
    }


    private void applyTypeFilter() {                                  // FUNCTION for calling action

        String selectedType = typeFilterComboBox.getValue();        // set the parameters used at selected/user preference or for any call/call/function/etc calls from list instead and is the goal of using data sets.

        handleTypeFilter(selectedType);

    }


    private void updateObservableList() {
        observableItemList.setAll(filteredItemList);  // VERY Important for updating values on function for action as these run a single actions at point, must push with full. If its setall must clean from 0, if adding only 0
    }

    // Make sure to shut down the executor when the application closes
    public void shutdown() {
        EXECUTOR.shutdown();
    }
}