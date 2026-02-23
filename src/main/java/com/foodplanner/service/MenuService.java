package com.foodplanner.service;

import com.foodplanner.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@Service
public class MenuService {

    private static final Logger log = LoggerFactory.getLogger(MenuService.class);

    private final FirebaseService firebaseService;
    private final GeminiService geminiService;
    private final StoreOfferService storeOfferService;

    public MenuService(FirebaseService firebaseService,
                       GeminiService geminiService,
                       StoreOfferService storeOfferService) {
        this.firebaseService = firebaseService;
        this.geminiService = geminiService;
        this.storeOfferService = storeOfferService;
    }

    public WeeklyMenu getCurrentWeekMenu(String userId) {
        String weekId = getCurrentWeekId();
        return firebaseService.getWeeklyMenu(userId, weekId);
    }

    public WeeklyMenu getWeekMenu(String userId, String weekId) {
        return firebaseService.getWeeklyMenu(userId, weekId);
    }

    public WeeklyMenu generateAndSaveWeeklyMenu(String userId) {
        User user = firebaseService.getUser(userId);
        MenuConfig config = user != null && user.getMenuConfig() != null
                ? user.getMenuConfig() : new MenuConfig();

        List<StoreOffer> offers = new ArrayList<>();
        if (user != null && config.isUseStoreOffers()) {
            List<Store> selectedStores = user.getSelectedStores() != null ? user.getSelectedStores() : List.of();
            for (Store store : selectedStores) {
                if (!store.hasValidId()) continue;
                List<StoreOffer> storeOffers = storeOfferService.getActiveOffers(store.getId());
                if (storeOffers.isEmpty()) {
                    // Auto-fetch offers on first menu generation
                    log.info("No offers cached for '{}', auto-fetching before menu generation", store.getName());
                    storeOffers = storeOfferService.refreshOffersForSpecificStore(store);
                    if (storeOffers.isEmpty()) {
                        log.warn("Could not fetch offers for '{}' – menu will be generated without them", store.getName());
                    }
                }
                offers.addAll(storeOffers);
            }
        }

        WeeklyMenu menu = geminiService.generateWeeklyMenu(userId, config, offers);
        return firebaseService.saveWeeklyMenu(userId, menu);
    }

    public WeeklyMenu saveWeeklyMenu(String userId, WeeklyMenu menu) {
        return firebaseService.saveWeeklyMenu(userId, menu);
    }

    public void updateMealCompletion(String userId, String weekId, String day,
                                      String mealType, int mealIndex, boolean completed) {
        WeeklyMenu menu = firebaseService.getWeeklyMenu(userId, weekId);
        if (menu == null || menu.getDays() == null) return;

        WeeklyMenu.DayMenu dayMenu = menu.getDays().get(day);
        if (dayMenu == null) return;

        List<WeeklyMenu.PlannedMeal> meals = switch (mealType.toLowerCase()) {
            case "breakfast" -> dayMenu.getBreakfast();
            case "lunch" -> dayMenu.getLunch();
            default -> dayMenu.getDinner();
        };

        if (meals != null && mealIndex < meals.size()) {
            meals.get(mealIndex).setCompleted(completed);
        }

        firebaseService.saveWeeklyMenu(userId, menu);
    }

    public String getCurrentWeekId() {
        LocalDate monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return formatWeekId(monday);
    }

    public String getNextWeekId() {
        LocalDate nextMonday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        return formatWeekId(nextMonday);
    }

    public static String formatWeekId(LocalDate date) {
        // Use ISO week-based year and week number (e.g. "2025-W04")
        int year = date.get(IsoFields.WEEK_BASED_YEAR);
        int week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        return String.format("%04d-W%02d", year, week);
    }

    public LocalDate getWeekStartDate(String weekId) {
        return LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
