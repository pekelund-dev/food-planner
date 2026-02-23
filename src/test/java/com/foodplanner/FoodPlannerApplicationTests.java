package com.foodplanner;

import com.foodplanner.model.MenuConfig;
import com.foodplanner.model.Recipe;
import com.foodplanner.model.ShoppingList;
import com.foodplanner.model.WeeklyMenu;
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
}
