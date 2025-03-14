package com.example.vrminventory;

public class LogEntry {
    private String date;
    private String time;
    private String activity;
    private int sku;
    private int quantity;
    private String description;

    // Additional fields for item details
    private String itemName;
    private String itemCategory;
    private double itemPrice;

    public LogEntry(String date, String time, String activity, int sku, int quantity, String description) {
        this.date = date;
        this.time = time;
        this.activity = activity;
        this.sku = sku;
        this.quantity = quantity;
        this.description = description;
    }

    // Getters and setters
    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getActivity() {
        return activity;
    }

    public void setActivity(String activity) {
        this.activity = activity;
    }

    public int getSku() {
        return sku;
    }

    public void setSku(int sku) {
        this.sku = sku;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public String getItemCategory() {
        return itemCategory;
    }

    public void setItemCategory(String itemCategory) {
        this.itemCategory = itemCategory;
    }

    public double getItemPrice() {
        return itemPrice;
    }

    public void setItemPrice(double itemPrice) {
        this.itemPrice = itemPrice;
    }

    @Override
    public String toString() {
        return date + " | " + time + " | " + activity + " | SKU: " + sku + " | Qty: " + quantity;
    }
}