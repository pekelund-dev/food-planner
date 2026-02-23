package com.foodplanner.model;

import java.time.Instant;
import java.util.List;

public class ShoppingList {

    private String id;
    private String userId;
    private String weekId;
    private String name;
    private List<ShoppingItem> items;
    private boolean completed;
    private Instant createdAt;
    private Instant updatedAt;

    public static class ShoppingItem {
        private String id;
        private String name;
        private double amount;
        private String unit;
        private String category;
        private boolean checked;
        private String mealName;
        private boolean onSale;
        private String storeId;
        private String storeName;
        private Double salePrice;

        public ShoppingItem() {}

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public double getAmount() { return amount; }
        public void setAmount(double amount) { this.amount = amount; }

        public String getUnit() { return unit; }
        public void setUnit(String unit) { this.unit = unit; }

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }

        public boolean isChecked() { return checked; }
        public void setChecked(boolean checked) { this.checked = checked; }

        public String getMealName() { return mealName; }
        public void setMealName(String mealName) { this.mealName = mealName; }

        public boolean isOnSale() { return onSale; }
        public void setOnSale(boolean onSale) { this.onSale = onSale; }

        public String getStoreId() { return storeId; }
        public void setStoreId(String storeId) { this.storeId = storeId; }

        public String getStoreName() { return storeName; }
        public void setStoreName(String storeName) { this.storeName = storeName; }

        public Double getSalePrice() { return salePrice; }
        public void setSalePrice(Double salePrice) { this.salePrice = salePrice; }
    }

    public ShoppingList() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getWeekId() { return weekId; }
    public void setWeekId(String weekId) { this.weekId = weekId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<ShoppingItem> getItems() { return items; }
    public void setItems(List<ShoppingItem> items) { this.items = items; }

    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public long getCheckedCount() {
        if (items == null) return 0;
        return items.stream().filter(ShoppingItem::isChecked).count();
    }

    public long getSaleItemCount() {
        if (items == null) return 0;
        return items.stream().filter(ShoppingItem::isOnSale).count();
    }
}
