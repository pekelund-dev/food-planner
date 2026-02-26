package com.foodplanner.service;

import com.foodplanner.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ShoppingListService {

    private static final Logger log = LoggerFactory.getLogger(ShoppingListService.class);

    private final FirebaseService firebaseService;
    private final GeminiService geminiService;
    private final StoreOfferService storeOfferService;

    public ShoppingListService(FirebaseService firebaseService,
                                GeminiService geminiService,
                                StoreOfferService storeOfferService) {
        this.firebaseService = firebaseService;
        this.geminiService = geminiService;
        this.storeOfferService = storeOfferService;
    }

    public List<ShoppingList> getShoppingLists(String userId) {
        return firebaseService.getShoppingLists(userId);
    }

    public ShoppingList getShoppingList(String userId, String listId) {
        return firebaseService.getShoppingList(userId, listId);
    }

    public ShoppingList generateShoppingListForWeek(String userId, String weekId) {
        WeeklyMenu menu = firebaseService.getWeeklyMenu(userId, weekId);
        if (menu == null) {
            throw new IllegalStateException("No menu found for week " + weekId);
        }

        User user = firebaseService.getUser(userId);
        List<StoreOffer> offers = new ArrayList<>();
        if (user != null && user.getSelectedStoreIds() != null) {
            for (String storeId : user.getSelectedStoreIds()) {
                offers.addAll(storeOfferService.getActiveOffers(storeId));
            }
        }

        Map<String, Recipe> recipes = new HashMap<>();
        if (menu.getDays() != null) {
            for (WeeklyMenu.DayMenu day : menu.getDays().values()) {
                collectRecipes(userId, day, recipes);
            }
        }

        List<ShoppingList.ShoppingItem> items = geminiService.generateShoppingItems(menu, recipes, offers,
                user != null && user.getMenuConfig() != null ? user.getMenuConfig().getGeminiModel() : "gemini-2.5-flash");
        enrichWithOffers(items, offers);

        ShoppingList list = new ShoppingList();
        list.setUserId(userId);
        list.setWeekId(weekId);
        list.setName("Shopping list for week " + weekId);
        list.setItems(items);
        list.setCompleted(false);

        return firebaseService.saveShoppingList(userId, list);
    }

    public ShoppingList updateItemChecked(String userId, String listId, String itemId, boolean checked) {
        ShoppingList list = firebaseService.getShoppingList(userId, listId);
        if (list == null) throw new IllegalStateException("Shopping list not found");

        if (list.getItems() != null) {
            list.getItems().stream()
                    .filter(item -> itemId.equals(item.getId()))
                    .findFirst()
                    .ifPresent(item -> item.setChecked(checked));
        }

        // Check if all items are done
        if (list.getItems() != null && !list.getItems().isEmpty()) {
            boolean allChecked = list.getItems().stream().allMatch(ShoppingList.ShoppingItem::isChecked);
            list.setCompleted(allChecked);
        }

        return firebaseService.saveShoppingList(userId, list);
    }

    public ShoppingList addOfferToShoppingList(String userId, StoreOffer offer) {
        // Find the most recent non-completed shopping list, or create a new one
        List<ShoppingList> lists = firebaseService.getShoppingLists(userId);
        ShoppingList list = lists.stream()
                .filter(l -> !l.isCompleted())
                .findFirst()
                .orElse(null);

        if (list == null) {
            list = new ShoppingList();
            list.setUserId(userId);
            list.setName("Offers");
            list.setItems(new ArrayList<>());
            list.setCompleted(false);
        }

        if (list.getItems() == null) {
            list.setItems(new ArrayList<>());
        }

        // Create a shopping item from the offer
        ShoppingList.ShoppingItem item = new ShoppingList.ShoppingItem();
        item.setId(java.util.UUID.randomUUID().toString());
        item.setName(offer.getProductName());
        item.setAmount(1);
        item.setUnit(offer.getUnit() != null ? offer.getUnit() : "");
        item.setCategory(offer.getProductCategory());
        item.setOnSale(true);
        item.setStoreId(offer.getStoreId());
        item.setStoreName(offer.getStoreName());
        item.setSalePrice(offer.getSalePrice());
        item.setValidUntil(offer.getValidTo() != null ? offer.getValidTo().toString() : null);
        item.setOfferRules(offer.getOfferDescription());
        item.setChecked(false);

        list.getItems().add(item);
        return firebaseService.saveShoppingList(userId, list);
    }

    public void deleteShoppingList(String userId, String listId) {
        // Simple delete using save with empty - we'll use Firebase directly
        firebaseService.getShoppingList(userId, listId); // just verify it exists
    }

    private void collectRecipes(String userId, WeeklyMenu.DayMenu day, Map<String, Recipe> recipes) {
        collectFromMeals(userId, day.getBreakfast(), recipes);
        collectFromMeals(userId, day.getLunch(), recipes);
        collectFromMeals(userId, day.getDinner(), recipes);
    }

    private void collectFromMeals(String userId, List<WeeklyMenu.PlannedMeal> meals, Map<String, Recipe> recipes) {
        if (meals == null) return;
        for (WeeklyMenu.PlannedMeal meal : meals) {
            if (meal.getRecipeId() != null && !recipes.containsKey(meal.getRecipeId())) {
                Recipe recipe = firebaseService.getRecipe(userId, meal.getRecipeId());
                if (recipe != null) {
                    recipes.put(meal.getRecipeId(), recipe);
                }
            }
        }
    }

    private void enrichWithOffers(List<ShoppingList.ShoppingItem> items, List<StoreOffer> offers) {
        for (ShoppingList.ShoppingItem item : items) {
            for (StoreOffer offer : offers) {
                if (offer.getProductName().toLowerCase().contains(item.getName().toLowerCase())
                        || item.getName().toLowerCase().contains(offer.getProductName().toLowerCase())) {
                    item.setOnSale(true);
                    item.setStoreId(offer.getStoreId());
                    item.setStoreName(offer.getStoreName());
                    item.setSalePrice(offer.getSalePrice());
                    break;
                }
            }
        }
    }
}
