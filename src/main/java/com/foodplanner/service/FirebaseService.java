package com.foodplanner.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.google.firebase.FirebaseApp;
import com.google.firebase.cloud.FirestoreClient;
import com.foodplanner.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutionException;

@Service
public class FirebaseService {

    private static final Logger log = LoggerFactory.getLogger(FirebaseService.class);

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private Firestore getFirestore() {
        return FirestoreClient.getFirestore(FirebaseApp.getInstance());
    }

    // ---- User operations ----

    public User getOrCreateUser(String userId, String email, String name, String pictureUrl) {
        try {
            DocumentReference docRef = getFirestore().collection("users").document(userId);
            DocumentSnapshot snapshot = docRef.get().get();
            if (snapshot.exists()) {
                User user = snapshotToUser(snapshot);
                // Update profile info
                Map<String, Object> updates = new HashMap<>();
                updates.put("email", email);
                updates.put("name", name);
                updates.put("pictureUrl", pictureUrl);
                updates.put("updatedAt", new Date());
                docRef.update(updates);
                user.setEmail(email);
                user.setName(name);
                user.setPictureUrl(pictureUrl);
                return user;
            } else {
                User user = new User();
                user.setId(userId);
                user.setEmail(email);
                user.setName(name);
                user.setPictureUrl(pictureUrl);
                user.setMenuConfig(new MenuConfig());
                user.setSelectedStoreIds(new ArrayList<>());
                user.setCreatedAt(java.time.Instant.now());
                user.setUpdatedAt(java.time.Instant.now());
                docRef.set(userToMap(user)).get();
                return user;
            }
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error getting/creating user", e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to get/create user", e);
        }
    }

    public User getUser(String userId) {
        try {
            DocumentSnapshot snapshot = getFirestore().collection("users").document(userId).get().get();
            return snapshot.exists() ? snapshotToUser(snapshot) : null;
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error getting user {}", userId, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to get user", e);
        }
    }

    public void saveMenuConfig(String userId, MenuConfig config) {
        try {
            getFirestore().collection("users").document(userId)
                    .update("menuConfig", objectMapper.convertValue(config, Map.class))
                    .get();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error saving menu config for user {}", userId, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to save menu config", e);
        }
    }

    public void saveSelectedStores(String userId, List<String> storeIds) {
        try {
            getFirestore().collection("users").document(userId)
                    .update("selectedStoreIds", storeIds)
                    .get();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error saving stores for user {}", userId, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to save stores", e);
        }
    }

    // ---- Meal operations ----

    public List<Meal> getMeals(String userId) {
        try {
            QuerySnapshot snapshot = getFirestore()
                    .collection("users").document(userId)
                    .collection("meals")
                    .orderBy("name")
                    .get().get();
            List<Meal> meals = new ArrayList<>();
            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                meals.add(snapshotToMeal(doc));
            }
            return meals;
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error getting meals for user {}", userId, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to get meals", e);
        }
    }

    public Meal getMeal(String userId, String mealId) {
        try {
            DocumentSnapshot snapshot = getFirestore()
                    .collection("users").document(userId)
                    .collection("meals").document(mealId)
                    .get().get();
            return snapshot.exists() ? snapshotToMeal(snapshot) : null;
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error getting meal {} for user {}", mealId, userId, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to get meal", e);
        }
    }

    public Meal saveMeal(String userId, Meal meal) {
        try {
            CollectionReference mealsRef = getFirestore()
                    .collection("users").document(userId)
                    .collection("meals");
            if (meal.getId() == null || meal.getId().isBlank()) {
                DocumentReference newDoc = mealsRef.document();
                meal.setId(newDoc.getId());
                meal.setUserId(userId);
                meal.setCreatedAt(java.time.Instant.now());
                meal.setUpdatedAt(java.time.Instant.now());
                newDoc.set(mealToMap(meal)).get();
            } else {
                meal.setUpdatedAt(java.time.Instant.now());
                mealsRef.document(meal.getId()).set(mealToMap(meal)).get();
            }
            return meal;
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error saving meal for user {}", userId, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to save meal", e);
        }
    }

    public void deleteMeal(String userId, String mealId) {
        try {
            getFirestore().collection("users").document(userId)
                    .collection("meals").document(mealId)
                    .delete().get();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error deleting meal {} for user {}", mealId, userId, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to delete meal", e);
        }
    }

    // ---- Recipe operations ----

    public List<Recipe> getRecipes(String userId) {
        try {
            QuerySnapshot snapshot = getFirestore()
                    .collection("users").document(userId)
                    .collection("recipes")
                    .orderBy("name")
                    .get().get();
            List<Recipe> recipes = new ArrayList<>();
            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                recipes.add(snapshotToRecipe(doc));
            }
            return recipes;
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error getting recipes for user {}", userId, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to get recipes", e);
        }
    }

    public Recipe getRecipe(String userId, String recipeId) {
        try {
            DocumentSnapshot snapshot = getFirestore()
                    .collection("users").document(userId)
                    .collection("recipes").document(recipeId)
                    .get().get();
            return snapshot.exists() ? snapshotToRecipe(snapshot) : null;
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error getting recipe {} for user {}", recipeId, userId, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to get recipe", e);
        }
    }

    public Recipe saveRecipe(String userId, Recipe recipe) {
        try {
            CollectionReference recipesRef = getFirestore()
                    .collection("users").document(userId)
                    .collection("recipes");
            if (recipe.getId() == null || recipe.getId().isBlank()) {
                DocumentReference newDoc = recipesRef.document();
                recipe.setId(newDoc.getId());
                recipe.setUserId(userId);
                recipe.setCreatedAt(java.time.Instant.now());
                recipe.setUpdatedAt(java.time.Instant.now());
                newDoc.set(recipeToMap(recipe)).get();
            } else {
                recipe.setUpdatedAt(java.time.Instant.now());
                recipesRef.document(recipe.getId()).set(recipeToMap(recipe)).get();
            }
            return recipe;
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error saving recipe for user {}", userId, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to save recipe", e);
        }
    }

    public void deleteRecipe(String userId, String recipeId) {
        try {
            getFirestore().collection("users").document(userId)
                    .collection("recipes").document(recipeId)
                    .delete().get();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error deleting recipe {} for user {}", recipeId, userId, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to delete recipe", e);
        }
    }

    // ---- Weekly Menu operations ----

    public WeeklyMenu getWeeklyMenu(String userId, String weekId) {
        try {
            DocumentSnapshot snapshot = getFirestore()
                    .collection("users").document(userId)
                    .collection("menus").document(weekId)
                    .get().get();
            return snapshot.exists() ? snapshotToWeeklyMenu(snapshot) : null;
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error getting weekly menu {} for user {}", weekId, userId, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to get weekly menu", e);
        }
    }

    public WeeklyMenu saveWeeklyMenu(String userId, WeeklyMenu menu) {
        try {
            if (menu.getId() == null) {
                menu.setId(menu.getWeekId());
            }
            menu.setUserId(userId);
            menu.setUpdatedAt(java.time.Instant.now());
            if (menu.getCreatedAt() == null) {
                menu.setCreatedAt(java.time.Instant.now());
            }
            getFirestore().collection("users").document(userId)
                    .collection("menus").document(menu.getWeekId())
                    .set(weeklyMenuToMap(menu)).get();
            return menu;
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error saving weekly menu for user {}", userId, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to save weekly menu", e);
        }
    }

    // ---- Rating operations ----

    public List<Rating> getRatings(String userId) {
        try {
            QuerySnapshot snapshot = getFirestore()
                    .collection("users").document(userId)
                    .collection("ratings")
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(50)
                    .get().get();
            List<Rating> ratings = new ArrayList<>();
            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                ratings.add(snapshotToRating(doc));
            }
            return ratings;
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error getting ratings for user {}", userId, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to get ratings", e);
        }
    }

    public List<Rating> getRatingsForMeal(String userId, String mealId) {
        try {
            QuerySnapshot snapshot = getFirestore()
                    .collection("users").document(userId)
                    .collection("ratings")
                    .whereEqualTo("mealId", mealId)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .get().get();
            List<Rating> ratings = new ArrayList<>();
            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                ratings.add(snapshotToRating(doc));
            }
            return ratings;
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error getting ratings for meal {} user {}", mealId, userId, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to get meal ratings", e);
        }
    }

    public Rating saveRating(String userId, Rating rating) {
        try {
            CollectionReference ratingsRef = getFirestore()
                    .collection("users").document(userId)
                    .collection("ratings");
            if (rating.getId() == null || rating.getId().isBlank()) {
                DocumentReference newDoc = ratingsRef.document();
                rating.setId(newDoc.getId());
                rating.setUserId(userId);
                rating.setCreatedAt(java.time.Instant.now());
                newDoc.set(ratingToMap(rating)).get();
            } else {
                ratingsRef.document(rating.getId()).set(ratingToMap(rating)).get();
            }
            // Update meal average rating
            updateMealAverageRating(userId, rating.getMealId());
            return rating;
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error saving rating for user {}", userId, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to save rating", e);
        }
    }

    private void updateMealAverageRating(String userId, String mealId) {
        try {
            List<Rating> ratings = getRatingsForMeal(userId, mealId);
            if (ratings.isEmpty()) return;
            double avg = ratings.stream().mapToInt(Rating::getScore).average().orElse(0);
            Map<String, Object> updates = new HashMap<>();
            updates.put("averageRating", avg);
            updates.put("ratingCount", ratings.size());
            getFirestore().collection("users").document(userId)
                    .collection("meals").document(mealId)
                    .update(updates).get();
        } catch (InterruptedException | ExecutionException e) {
            log.warn("Failed to update meal average rating", e);
            Thread.currentThread().interrupt();
        }
    }

    // ---- Shopping List operations ----

    public List<ShoppingList> getShoppingLists(String userId) {
        try {
            QuerySnapshot snapshot = getFirestore()
                    .collection("users").document(userId)
                    .collection("shopping-lists")
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(10)
                    .get().get();
            List<ShoppingList> lists = new ArrayList<>();
            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                lists.add(snapshotToShoppingList(doc));
            }
            return lists;
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error getting shopping lists for user {}", userId, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to get shopping lists", e);
        }
    }

    public ShoppingList getShoppingList(String userId, String listId) {
        try {
            DocumentSnapshot snapshot = getFirestore()
                    .collection("users").document(userId)
                    .collection("shopping-lists").document(listId)
                    .get().get();
            return snapshot.exists() ? snapshotToShoppingList(snapshot) : null;
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error getting shopping list {} for user {}", listId, userId, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to get shopping list", e);
        }
    }

    public ShoppingList saveShoppingList(String userId, ShoppingList list) {
        try {
            CollectionReference listsRef = getFirestore()
                    .collection("users").document(userId)
                    .collection("shopping-lists");
            if (list.getId() == null || list.getId().isBlank()) {
                DocumentReference newDoc = listsRef.document();
                list.setId(newDoc.getId());
                list.setUserId(userId);
                list.setCreatedAt(java.time.Instant.now());
                list.setUpdatedAt(java.time.Instant.now());
                newDoc.set(shoppingListToMap(list)).get();
            } else {
                list.setUpdatedAt(java.time.Instant.now());
                listsRef.document(list.getId()).set(shoppingListToMap(list)).get();
            }
            return list;
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error saving shopping list for user {}", userId, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to save shopping list", e);
        }
    }

    // ---- Store Offers operations ----

    public List<StoreOffer> getActiveOffersForStore(String storeId) {
        try {
            QuerySnapshot snapshot = getFirestore()
                    .collection("store-offers")
                    .whereEqualTo("storeId", storeId)
                    .get().get();
            List<StoreOffer> offers = new ArrayList<>();
            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                offers.add(snapshotToStoreOffer(doc));
            }
            return offers;
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error getting offers for store {}", storeId, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to get store offers", e);
        }
    }

    public void saveStoreOffer(StoreOffer offer) {
        try {
            if (offer.getId() == null || offer.getId().isBlank()) {
                DocumentReference newDoc = getFirestore().collection("store-offers").document();
                offer.setId(newDoc.getId());
                newDoc.set(storeOfferToMap(offer)).get();
            } else {
                getFirestore().collection("store-offers").document(offer.getId())
                        .set(storeOfferToMap(offer)).get();
            }
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error saving store offer", e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to save store offer", e);
        }
    }

    public void deleteExpiredOffers(String storeId) {
        try {
            QuerySnapshot snapshot = getFirestore()
                    .collection("store-offers")
                    .whereEqualTo("storeId", storeId)
                    .get().get();
            WriteBatch batch = getFirestore().batch();
            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                batch.delete(doc.getReference());
            }
            batch.commit().get();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error deleting offers for store {}", storeId, e);
            Thread.currentThread().interrupt();
        }
    }

    // ---- Mapping helpers ----

    private User snapshotToUser(DocumentSnapshot doc) {
        Map<String, Object> data = doc.getData();
        if (data == null) return null;
        User user = new User();
        user.setId(doc.getId());
        user.setEmail((String) data.get("email"));
        user.setName((String) data.get("name"));
        user.setPictureUrl((String) data.get("pictureUrl"));
        if (data.get("selectedStoreIds") instanceof List) {
            user.setSelectedStoreIds((List<String>) data.get("selectedStoreIds"));
        }
        if (data.get("menuConfig") instanceof Map) {
            user.setMenuConfig(objectMapper.convertValue(data.get("menuConfig"), MenuConfig.class));
        } else {
            user.setMenuConfig(new MenuConfig());
        }
        return user;
    }

    private Map<String, Object> userToMap(User user) {
        return objectMapper.convertValue(user, new TypeReference<Map<String, Object>>() {});
    }

    private Meal snapshotToMeal(DocumentSnapshot doc) {
        Map<String, Object> data = doc.getData();
        if (data == null) return null;
        Meal meal = objectMapper.convertValue(data, Meal.class);
        meal.setId(doc.getId());
        return meal;
    }

    private Map<String, Object> mealToMap(Meal meal) {
        return objectMapper.convertValue(meal, new TypeReference<Map<String, Object>>() {});
    }

    private Recipe snapshotToRecipe(DocumentSnapshot doc) {
        Map<String, Object> data = doc.getData();
        if (data == null) return null;
        Recipe recipe = objectMapper.convertValue(data, Recipe.class);
        recipe.setId(doc.getId());
        return recipe;
    }

    private Map<String, Object> recipeToMap(Recipe recipe) {
        return objectMapper.convertValue(recipe, new TypeReference<Map<String, Object>>() {});
    }

    private WeeklyMenu snapshotToWeeklyMenu(DocumentSnapshot doc) {
        Map<String, Object> data = doc.getData();
        if (data == null) return null;
        WeeklyMenu menu = objectMapper.convertValue(data, WeeklyMenu.class);
        menu.setId(doc.getId());
        return menu;
    }

    private Map<String, Object> weeklyMenuToMap(WeeklyMenu menu) {
        return objectMapper.convertValue(menu, new TypeReference<Map<String, Object>>() {});
    }

    private Rating snapshotToRating(DocumentSnapshot doc) {
        Map<String, Object> data = doc.getData();
        if (data == null) return null;
        Rating rating = objectMapper.convertValue(data, Rating.class);
        rating.setId(doc.getId());
        return rating;
    }

    private Map<String, Object> ratingToMap(Rating rating) {
        return objectMapper.convertValue(rating, new TypeReference<Map<String, Object>>() {});
    }

    private ShoppingList snapshotToShoppingList(DocumentSnapshot doc) {
        Map<String, Object> data = doc.getData();
        if (data == null) return null;
        ShoppingList list = objectMapper.convertValue(data, ShoppingList.class);
        list.setId(doc.getId());
        return list;
    }

    private Map<String, Object> shoppingListToMap(ShoppingList list) {
        return objectMapper.convertValue(list, new TypeReference<Map<String, Object>>() {});
    }

    private StoreOffer snapshotToStoreOffer(DocumentSnapshot doc) {
        Map<String, Object> data = doc.getData();
        if (data == null) return null;
        StoreOffer offer = objectMapper.convertValue(data, StoreOffer.class);
        offer.setId(doc.getId());
        return offer;
    }

    private Map<String, Object> storeOfferToMap(StoreOffer offer) {
        return objectMapper.convertValue(offer, new TypeReference<Map<String, Object>>() {});
    }
}
