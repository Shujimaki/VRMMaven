package com.example.vrminventory;

public class InventoryItem {
    private int sku;
    private String name;
    private String category;
    private double price;
    private int quantity;

    public InventoryItem(int sku, String name, String category, double price, int quantity) {
        this.sku = sku;
        this.name = name;
        this.category = category;
        this.price = price;
        this.quantity = quantity;
    }

    // Getters
    public int getSku() { return sku; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public double getPrice() { return price; }
    public int getQuantity() { return quantity; }

    @Override
    public String toString() {
        return String.format("SKU: %d, Name: %s, Category: %s, Price: %.2f, Quantity: %d", sku, name, category, price, quantity);
    }
}
