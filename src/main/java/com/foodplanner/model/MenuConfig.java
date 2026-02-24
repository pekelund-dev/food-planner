package com.foodplanner.model;

import java.util.List;

public class MenuConfig {

    private int numberOfPeople = 2;
    private List<String> dietaryPreferences;
    private List<String> allergies;
    private String cuisinePreferences;
    private int mealsPerDay = 1;
    private boolean includeBreakfast = false;
    private boolean includeLunch = false;
    private boolean includeDinner = true;
    private String additionalInstructions;
    private boolean preferBudgetFriendly = true;
    private boolean useStoreOffers = true;
    private String geminiModel = "gemini-2.5-flash";

    public MenuConfig() {}

    public int getNumberOfPeople() { return numberOfPeople; }
    public void setNumberOfPeople(int numberOfPeople) { this.numberOfPeople = numberOfPeople; }

    public List<String> getDietaryPreferences() { return dietaryPreferences; }
    public void setDietaryPreferences(List<String> dietaryPreferences) { this.dietaryPreferences = dietaryPreferences; }

    public List<String> getAllergies() { return allergies; }
    public void setAllergies(List<String> allergies) { this.allergies = allergies; }

    public String getCuisinePreferences() { return cuisinePreferences; }
    public void setCuisinePreferences(String cuisinePreferences) { this.cuisinePreferences = cuisinePreferences; }

    public int getMealsPerDay() { return mealsPerDay; }
    public void setMealsPerDay(int mealsPerDay) { this.mealsPerDay = mealsPerDay; }

    public boolean isIncludeBreakfast() { return includeBreakfast; }
    public void setIncludeBreakfast(boolean includeBreakfast) { this.includeBreakfast = includeBreakfast; }

    public boolean isIncludeLunch() { return includeLunch; }
    public void setIncludeLunch(boolean includeLunch) { this.includeLunch = includeLunch; }

    public boolean isIncludeDinner() { return includeDinner; }
    public void setIncludeDinner(boolean includeDinner) { this.includeDinner = includeDinner; }

    public String getAdditionalInstructions() { return additionalInstructions; }
    public void setAdditionalInstructions(String additionalInstructions) { this.additionalInstructions = additionalInstructions; }

    public boolean isPreferBudgetFriendly() { return preferBudgetFriendly; }
    public void setPreferBudgetFriendly(boolean preferBudgetFriendly) { this.preferBudgetFriendly = preferBudgetFriendly; }

    public boolean isUseStoreOffers() { return useStoreOffers; }
    public void setUseStoreOffers(boolean useStoreOffers) { this.useStoreOffers = useStoreOffers; }

    public String getGeminiModel() { return geminiModel; }
    public void setGeminiModel(String geminiModel) { this.geminiModel = geminiModel; }
}
