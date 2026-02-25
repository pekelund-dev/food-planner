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
    private static final String[] ALL_DAYS = {"MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"};

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
        return generateAndSaveWeeklyMenuForWeek(userId, getNextWeekId(), null);
    }

    public WeeklyMenu generateAndSaveWeeklyMenuForWeek(String userId, String targetWeekId, String feedback) {
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
                    log.info("No offers cached for '{}', auto-fetching before menu generation", store.getName());
                    storeOffers = storeOfferService.refreshOffersForSpecificStore(store);
                    if (storeOffers.isEmpty()) {
                        log.warn("Could not fetch offers for '{}' – menu will be generated without them", store.getName());
                    }
                }
                offers.addAll(storeOffers);
            }
        }

        WeeklyMenu menu = geminiService.generateWeeklyMenu(userId, config, offers, feedback);
        // Override week dates with the target week
        menu.setWeekId(targetWeekId);
        LocalDate weekStart = parseWeekId(targetWeekId);
        menu.setWeekStart(weekStart);
        int span = config.getMenuSpanDays() > 0 ? config.getMenuSpanDays() : 7;
        menu.setWeekEnd(weekStart.plusDays(span - 1));
        return firebaseService.saveWeeklyMenu(userId, menu);
    }

    public WeeklyMenu regenerateDish(String userId, String weekId, String day,
                                      String mealType, int mealIndex, String feedback) {
        WeeklyMenu menu = firebaseService.getWeeklyMenu(userId, weekId);
        if (menu == null || menu.getDays() == null) return null;

        User user = firebaseService.getUser(userId);
        MenuConfig config = user != null && user.getMenuConfig() != null
                ? user.getMenuConfig() : new MenuConfig();

        List<StoreOffer> offers = new ArrayList<>();
        if (user != null && config.isUseStoreOffers()) {
            List<Store> selectedStores = user.getSelectedStores() != null ? user.getSelectedStores() : List.of();
            for (Store store : selectedStores) {
                if (store.hasValidId()) {
                    offers.addAll(storeOfferService.getActiveOffers(store.getId()));
                }
            }
        }

        WeeklyMenu.DayMenu dayMenu = menu.getDays().get(day.toUpperCase());
        if (dayMenu == null) return menu;

        List<WeeklyMenu.PlannedMeal> meals = switch (mealType.toLowerCase()) {
            case "breakfast" -> dayMenu.getBreakfast();
            case "lunch" -> dayMenu.getLunch();
            default -> dayMenu.getDinner();
        };

        String currentMealName = (meals != null && mealIndex < meals.size())
                ? meals.get(mealIndex).getMealName() : "Unknown meal";

        WeeklyMenu.PlannedMeal newMeal = geminiService.regenerateSingleMeal(currentMealName, config, offers, feedback);
        newMeal.setCompleted(false);

        if (meals != null && mealIndex < meals.size()) {
            meals.set(mealIndex, newMeal);
        }

        return firebaseService.saveWeeklyMenu(userId, menu);
    }

    public WeeklyMenu saveWeeklyMenu(String userId, WeeklyMenu menu) {
        return firebaseService.saveWeeklyMenu(userId, menu);
    }

    public List<String> listMenuWeekIds(String userId) {
        return firebaseService.listWeeklyMenuIds(userId);
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

    public String[] getMenuDays(MenuConfig config) {
        if (config == null) return ALL_DAYS.clone();
        String start = config.getStartDayOfWeek() != null ? config.getStartDayOfWeek().toUpperCase() : "MONDAY";
        int span = config.getMenuSpanDays() > 0 ? Math.min(config.getMenuSpanDays(), 7) : 7;
        int startIdx = 0;
        for (int i = 0; i < ALL_DAYS.length; i++) {
            if (ALL_DAYS[i].equals(start)) { startIdx = i; break; }
        }
        String[] result = new String[span];
        for (int i = 0; i < span; i++) {
            result[i] = ALL_DAYS[(startIdx + i) % 7];
        }
        return result;
    }

    public String getCurrentWeekId() {
        LocalDate monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return formatWeekId(monday);
    }

    public String getNextWeekId() {
        LocalDate nextMonday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        return formatWeekId(nextMonday);
    }

    public String getPreviousWeekId(String weekId) {
        return formatWeekId(parseWeekId(weekId).minusWeeks(1));
    }

    public String getNextWeekIdFrom(String weekId) {
        return formatWeekId(parseWeekId(weekId).plusWeeks(1));
    }

    public static String formatWeekId(LocalDate date) {
        int year = date.get(IsoFields.WEEK_BASED_YEAR);
        int week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        return String.format("%04d-W%02d", year, week);
    }

    public LocalDate parseWeekId(String weekId) {
        try {
            String[] parts = weekId.split("-W");
            int year = Integer.parseInt(parts[0]);
            int week = Integer.parseInt(parts[1]);
            return LocalDate.now()
                    .with(IsoFields.WEEK_BASED_YEAR, year)
                    .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, week)
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        } catch (Exception e) {
            log.warn("Failed to parse weekId '{}', using current week", weekId);
            return LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        }
    }

    public LocalDate getWeekStartDate(String weekId) {
        return parseWeekId(weekId);
    }
}
