package com.foodplanner.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class WeeklyMenu {

    private String id;
    private String userId;
    private String weekId; // e.g. "2025-W04"
    private LocalDate weekStart;
    private LocalDate weekEnd;
    private Map<String, DayMenu> days; // key: "MONDAY", "TUESDAY", etc.
    private boolean aiGenerated;
    private Instant createdAt;
    private Instant updatedAt;

    public static class DayMenu {
        private List<PlannedMeal> breakfast;
        private List<PlannedMeal> lunch;
        private List<PlannedMeal> dinner;

        public DayMenu() {}

        public List<PlannedMeal> getBreakfast() { return breakfast; }
        public void setBreakfast(List<PlannedMeal> breakfast) { this.breakfast = breakfast; }

        public List<PlannedMeal> getLunch() { return lunch; }
        public void setLunch(List<PlannedMeal> lunch) { this.lunch = lunch; }

        public List<PlannedMeal> getDinner() { return dinner; }
        public void setDinner(List<PlannedMeal> dinner) { this.dinner = dinner; }
    }

    public static class PlannedMeal {
        private String mealId;
        private String mealName;
        private String recipeId;
        private int servings;
        private boolean completed;
        private double estimatedCost;

        public PlannedMeal() {}

        public String getMealId() { return mealId; }
        public void setMealId(String mealId) { this.mealId = mealId; }

        public String getMealName() { return mealName; }
        public void setMealName(String mealName) { this.mealName = mealName; }

        public String getRecipeId() { return recipeId; }
        public void setRecipeId(String recipeId) { this.recipeId = recipeId; }

        public int getServings() { return servings; }
        public void setServings(int servings) { this.servings = servings; }

        public boolean isCompleted() { return completed; }
        public void setCompleted(boolean completed) { this.completed = completed; }

        public double getEstimatedCost() { return estimatedCost; }
        public void setEstimatedCost(double estimatedCost) { this.estimatedCost = estimatedCost; }
    }

    public WeeklyMenu() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getWeekId() { return weekId; }
    public void setWeekId(String weekId) { this.weekId = weekId; }

    public LocalDate getWeekStart() { return weekStart; }
    public void setWeekStart(LocalDate weekStart) { this.weekStart = weekStart; }

    public LocalDate getWeekEnd() { return weekEnd; }
    public void setWeekEnd(LocalDate weekEnd) { this.weekEnd = weekEnd; }

    public Map<String, DayMenu> getDays() { return days; }
    public void setDays(Map<String, DayMenu> days) { this.days = days; }

    public boolean isAiGenerated() { return aiGenerated; }
    public void setAiGenerated(boolean aiGenerated) { this.aiGenerated = aiGenerated; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
