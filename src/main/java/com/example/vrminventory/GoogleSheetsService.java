package com.example.vrminventory;

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
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.UpdateValuesResponse;
import com.google.api.services.sheets.v4.model.ValueRange;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Service class for Google Sheets API operations
 */
public class GoogleSheetsService {
    // Constants
    private static final String APPLICATION_NAME = "VRM Inventory System";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";
    private static final List<String> SCOPES = Collections.singletonList(SheetsScopes.SPREADSHEETS);
    private static final String CREDENTIALS_FILE_PATH = "/credentials.json";

    // Default values
    private static final int DEFAULT_START_ROW = 21;

    // Fields
    private final Sheets sheetsService;
    private final String spreadsheetId;

    // Cache for inventory items
    private List<InventoryItem> cachedInventoryItems = null;

    /**
     * Constructs a GoogleSheetsService with the default spreadsheet ID.
     */
    public GoogleSheetsService() throws GeneralSecurityException, IOException {
        this("1ztEDIb6npREKC6a9uJxjlQFkFGGgwDfL_v2Lpo2aSdc");
    }

    /**
     * Constructs a GoogleSheetsService with a custom spreadsheet ID.
     *
     * @param spreadsheetId The ID of the Google Spreadsheet to interact with
     */
    public GoogleSheetsService(String spreadsheetId) throws GeneralSecurityException, IOException {
        this.spreadsheetId = spreadsheetId;
        NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        this.sheetsService = new Sheets.Builder(httpTransport, JSON_FACTORY, getCredentials(httpTransport))
                .setApplicationName(APPLICATION_NAME)
                .build();
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
        InputStream in = GoogleSheetsService.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
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

    /**
     * Finds the next available row in a specific branch sheet.
     *
     * @param branch The branch sheet name including the trailing exclamation mark (e.g., "Branch1!")
     * @return A string representing the range for the next available row
     * @throws IOException If an API error occurs
     */
    public String findNextRow(String branch) throws IOException {
        return findNextRow(branch, DEFAULT_START_ROW);
    }

    /**
     * Finds the next available row in a specific branch sheet, starting from a specified row.
     *
     * @param branch The branch sheet name including the trailing exclamation mark (e.g., "Branch1!")
     * @param startRow The row to start searching from
     * @return A string representing the range for the next available row
     * @throws IOException If an API error occurs
     */
    public String findNextRow(String branch, int startRow) throws IOException {
        String range = branch + "I" + startRow + ":N";
        ValueRange response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, range)
                .execute();

        if (response.getValues() == null || response.getValues().isEmpty()) {
            return branch + "I" + startRow + ":N" + startRow;
        }

        // Find first empty row
        for (int i = 0; i < response.getValues().size(); i++) {
            if (response.getValues().get(i).isEmpty()) {
                int rowNum = startRow + i;
                return branch + "I" + rowNum + ":N" + rowNum;
            }
        }

        // If no empty row found, return next row after last fetched row
        int nextRow = startRow + response.getValues().size();
        return branch + "I" + nextRow + ":N" + nextRow;
    }

    /**
     * Writes data to a specified range in the spreadsheet.
     *
     * @param range The range to write to
     * @param data The data to write
     * @return The number of cells updated
     * @throws IOException If an API error occurs
     */
    public int writeData(String range, List<Object> data) throws IOException {
        ValueRange body = new ValueRange().setValues(Collections.singletonList(data));

        UpdateValuesResponse result = sheetsService.spreadsheets().values()
                .update(spreadsheetId, range, body)
                .setValueInputOption("USER_ENTERED")
                .execute();

        return result.getUpdatedCells();
    }

    /**
     * Retrieves all inventory items from a specific sheet, using cache if available.
     *
     * @param sheetName The name of the sheet to read from (e.g., "Branch1")
     * @return A list of InventoryItem objects
     * @throws IOException If an API error occurs
     */
    public List<InventoryItem> getAllInventoryItems(String sheetName) throws IOException {
        try {
            if (cachedInventoryItems != null) {
                return cachedInventoryItems; // Return cached items if available
            }

            List<InventoryItem> items = new ArrayList<>();

            // Get sheet metadata to verify sheet exists
            Spreadsheet spreadsheet = sheetsService.spreadsheets().get(spreadsheetId).execute();
            boolean sheetExists = spreadsheet.getSheets().stream()
                    .anyMatch(sheet -> sheetName.equals(sheet.getProperties().getTitle()));

            if (!sheetExists) {
                throw new IOException(sheetName + " sheet not found");
            }

            // Fetch data from sheet
            String range = sheetName + "!B" + DEFAULT_START_ROW + ":F";
            ValueRange response = sheetsService.spreadsheets().values()
                    .get(spreadsheetId, range)
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

            cachedInventoryItems = items; // Cache the items for future use
            return items;

            } catch (IOException e) {
            Logger.logError("Failed to get inventory items from sheet: " + sheetName, e);
            return Collections.emptyList(); // Return an empty list on error
        }

    }

    public void clearCache() {
        cachedInventoryItems = null;
    }

    /**
     * Gets the underlying Sheets service instance.
     *
     * @return The Sheets service instance
     */
    public Sheets getSheetsService() {
        return sheetsService;
    }

    /**
     * Gets the spreadsheet ID being used.
     *
     * @return The spreadsheet ID
     */
    public String getSpreadsheetId() {
        return spreadsheetId;
    }

    /**
     * Shuts down the sheets service and its underlying resources.
     */
    public void shutdown() {
        try {
            if (sheetsService != null && sheetsService.getRequestFactory() != null &&
                    sheetsService.getRequestFactory().getTransport() != null) {

                // Attempt to shutdown the HTTP transport
                sheetsService.getRequestFactory().getTransport().shutdown();
            }
        } catch (Exception e) {
            System.err.println("Error shutting down sheets service: " + e.getMessage());
        }
    }
}