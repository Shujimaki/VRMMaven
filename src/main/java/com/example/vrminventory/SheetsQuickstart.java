package com.example.vrminventory;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

/**
 * Sample application demonstrating the use of the GoogleSheetsService class.
 */
public class SheetsQuickstart {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    public static void main(String... args) {
        try {
            // Create the sheets service
            GoogleSheetsService sheetsService = new GoogleSheetsService();

            // Find the next available row in Branch3
            String branch3Range = sheetsService.findNextRow("Branch3!");
            System.out.println("Next available range: " + branch3Range);

            // Prepare the data to write
            List<Object> dataToWrite = Arrays.asList(
                    LocalDate.now().format(DATE_FORMATTER),
                    LocalTime.now().format(TIME_FORMATTER),
                    2,
                    10046,
                    250,
                    "SampleDesc"
            );

            // Write the data
            int cellsUpdated = sheetsService.writeData(branch3Range, dataToWrite);
            System.out.printf("%d cells updated.%n", cellsUpdated);

            // Demonstrate retrieving inventory items
            List<InventoryItem> items = sheetsService.getAllInventoryItems("Branch1");
            System.out.println("Retrieved " + items.size() + " inventory items:");
            for (int i = 0; i < Math.min(5, items.size()); i++) {
                InventoryItem item = items.get(i);
                System.out.printf("Item %d: SKU=%d, Name=%s, Price=%.2f, Quantity=%d%n",
                        i + 1, item.getSku(), item.getName(), item.getPrice(), item.getQuantity());
            }

        } catch (GeneralSecurityException | IOException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}