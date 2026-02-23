package com.foodplanner.model;

import java.time.Instant;
import java.util.List;

public class Recipe {

    private String id;
    private String userId;
    private String mealId;
    private String name;
    private String description;
    private List<Ingredient> ingredients;
    private List<String> instructions;
    private int servings;
    private int prepTimeMinutes;
    private int cookTimeMinutes;
    private DifficultyLevel difficulty;
    private List<String> tags;
    private String imageUrl;
    private Instant createdAt;
    private Instant updatedAt;

    public enum DifficultyLevel {
        EASY, MEDIUM, HARD
    }

    public static class Ingredient {
        private String name;
        private double amount;
        private String unit;
        private boolean onSale;
        private String storeId;

        public Ingredient() {}

        public Ingredient(String name, double amount, String unit) {
            this.name = name;
            this.amount = amount;
            this.unit = unit;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public double getAmount() { return amount; }
        public void setAmount(double amount) { this.amount = amount; }

        public String getUnit() { return unit; }
        public void setUnit(String unit) { this.unit = unit; }

        public boolean isOnSale() { return onSale; }
        public void setOnSale(boolean onSale) { this.onSale = onSale; }

        public String getStoreId() { return storeId; }
        public void setStoreId(String storeId) { this.storeId = storeId; }
    }

    public Recipe() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getMealId() { return mealId; }
    public void setMealId(String mealId) { this.mealId = mealId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<Ingredient> getIngredients() { return ingredients; }
    public void setIngredients(List<Ingredient> ingredients) { this.ingredients = ingredients; }

    public List<String> getInstructions() { return instructions; }
    public void setInstructions(List<String> instructions) { this.instructions = instructions; }

    public int getServings() { return servings; }
    public void setServings(int servings) { this.servings = servings; }

    public int getPrepTimeMinutes() { return prepTimeMinutes; }
    public void setPrepTimeMinutes(int prepTimeMinutes) { this.prepTimeMinutes = prepTimeMinutes; }

    public int getCookTimeMinutes() { return cookTimeMinutes; }
    public void setCookTimeMinutes(int cookTimeMinutes) { this.cookTimeMinutes = cookTimeMinutes; }

    public DifficultyLevel getDifficulty() { return difficulty; }
    public void setDifficulty(DifficultyLevel difficulty) { this.difficulty = difficulty; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public int getTotalTimeMinutes() {
        return prepTimeMinutes + cookTimeMinutes;
    }
}
