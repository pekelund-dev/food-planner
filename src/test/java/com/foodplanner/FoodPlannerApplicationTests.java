package com.foodplanner;

import com.foodplanner.model.MenuConfig;
import com.foodplanner.model.Recipe;
import com.foodplanner.model.ShoppingList;
import com.foodplanner.model.WeeklyMenu;
import com.foodplanner.service.MenuService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Basic unit tests for domain model logic.
 */
class FoodPlannerApplicationTests {

    @Test
    void menuConfigDefaults() {
        MenuConfig config = new MenuConfig();
        assertEquals(2, config.getNumberOfPeople());
        assertTrue(config.isIncludeDinner());
        assertFalse(config.isIncludeBreakfast());
        assertFalse(config.isIncludeLunch());
        assertTrue(config.isPreferBudgetFriendly());
        assertTrue(config.isUseStoreOffers());
        assertEquals("gemini-2.5-flash", config.getGeminiModel());
        assertEquals("MONDAY", config.getStartDayOfWeek());
        assertEquals(7, config.getMenuSpanDays());
    }

    @Test
    void menuConfigStartDayAndSpan() {
        MenuConfig config = new MenuConfig();
        config.setStartDayOfWeek("WEDNESDAY");
        config.setMenuSpanDays(5);
        assertEquals("WEDNESDAY", config.getStartDayOfWeek());
        assertEquals(5, config.getMenuSpanDays());
    }

    @Test
    void menuServiceGetMenuDaysDefault() {
        MenuConfig config = new MenuConfig();
        String[] days = new MenuServiceTestHelper().getMenuDays(config);
        assertEquals(7, days.length);
        assertEquals("MONDAY", days[0]);
        assertEquals("SUNDAY", days[6]);
    }

    @Test
    void menuServiceGetMenuDaysCustomStart() {
        MenuConfig config = new MenuConfig();
        config.setStartDayOfWeek("SATURDAY");
        config.setMenuSpanDays(3);
        String[] days = new MenuServiceTestHelper().getMenuDays(config);
        assertEquals(3, days.length);
        assertEquals("SATURDAY", days[0]);
        assertEquals("SUNDAY", days[1]);
        assertEquals("MONDAY", days[2]);
    }

    @Test
    void plannedMealDescriptionField() {
        WeeklyMenu.PlannedMeal meal = new WeeklyMenu.PlannedMeal();
        meal.setDescription("Delicious Italian pasta");
        assertEquals("Delicious Italian pasta", meal.getDescription());
    }

    @Test
    void recipeTotalTime() {
        Recipe recipe = new Recipe();
        recipe.setPrepTimeMinutes(15);
        recipe.setCookTimeMinutes(30);
        assertEquals(45, recipe.getTotalTimeMinutes());
    }

    @Test
    void shoppingListCheckedCount() {
        ShoppingList list = new ShoppingList();
        ShoppingList.ShoppingItem item1 = new ShoppingList.ShoppingItem();
        item1.setChecked(true);
        ShoppingList.ShoppingItem item2 = new ShoppingList.ShoppingItem();
        item2.setChecked(false);
        list.setItems(java.util.List.of(item1, item2));
        assertEquals(1, list.getCheckedCount());
    }

    @Test
    void shoppingListSaleItemCount() {
        ShoppingList list = new ShoppingList();
        ShoppingList.ShoppingItem item1 = new ShoppingList.ShoppingItem();
        item1.setOnSale(true);
        ShoppingList.ShoppingItem item2 = new ShoppingList.ShoppingItem();
        item2.setOnSale(false);
        list.setItems(java.util.List.of(item1, item2));
        assertEquals(1, list.getSaleItemCount());
    }

    @Test
    void weeklyMenuDayMenuContainsFields() {
        WeeklyMenu.DayMenu dayMenu = new WeeklyMenu.DayMenu();
        assertNull(dayMenu.getDinner());
        assertNull(dayMenu.getLunch());
        assertNull(dayMenu.getBreakfast());
    }

    /**
     * Test helper that exposes MenuService.getMenuDays without needing Spring context.
     */
    static class MenuServiceTestHelper extends MenuService {
        MenuServiceTestHelper() {
            super(null, null, null);
        }
    }
}
