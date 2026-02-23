package com.foodplanner.service;

import com.foodplanner.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RecipeService {

    private static final Logger log = LoggerFactory.getLogger(RecipeService.class);

    private final FirebaseService firebaseService;
    private final GeminiService geminiService;
    private final StoreOfferService storeOfferService;

    public RecipeService(FirebaseService firebaseService,
                         GeminiService geminiService,
                         StoreOfferService storeOfferService) {
        this.firebaseService = firebaseService;
        this.geminiService = geminiService;
        this.storeOfferService = storeOfferService;
    }

    public List<Recipe> getRecipes(String userId) {
        return firebaseService.getRecipes(userId);
    }

    public Recipe getRecipe(String userId, String recipeId) {
        return firebaseService.getRecipe(userId, recipeId);
    }

    public Recipe saveRecipe(String userId, Recipe recipe) {
        return firebaseService.saveRecipe(userId, recipe);
    }

    public void deleteRecipe(String userId, String recipeId) {
        firebaseService.deleteRecipe(userId, recipeId);
    }

    public Recipe generateRecipeForMeal(String userId, String mealName) {
        User user = firebaseService.getUser(userId);
        MenuConfig config = user != null && user.getMenuConfig() != null
                ? user.getMenuConfig() : new MenuConfig();

        List<StoreOffer> offers = List.of();
        if (user != null && user.getSelectedStoreIds() != null && !user.getSelectedStoreIds().isEmpty()) {
            offers = user.getSelectedStoreIds().stream()
                    .flatMap(storeId -> storeOfferService.getActiveOffers(storeId).stream())
                    .toList();
        }

        Recipe recipe = geminiService.generateRecipe(userId, mealName, config, offers);
        return firebaseService.saveRecipe(userId, recipe);
    }
}
