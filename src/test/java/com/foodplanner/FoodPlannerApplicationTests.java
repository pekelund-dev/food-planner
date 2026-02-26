package com.foodplanner;

import com.foodplanner.model.MenuConfig;
import com.foodplanner.model.Recipe;
import com.foodplanner.model.ShoppingList;
import com.foodplanner.model.StoreOffer;
import com.foodplanner.model.WeeklyMenu;
import com.foodplanner.service.MenuService;
import com.foodplanner.service.StoreOfferService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

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

    // ---- ICA __INITIAL_DATA__ parsing tests ----

    /** Minimal HTML snippet containing a window.__INITIAL_DATA__ script block. */
    private static final String ICA_HTML_SNIPPET =
        "<html><head></head><body>" +
        "<script>window.__INITIAL_DATA__ = {" +
        "\"offers\":{" +
        "\"weeklyOffers\":[{" +
        "\"id\":\"5003802339\"," +
        "\"details\":{\"brand\":\"Kronfågel. Ursprung Sverige\",\"name\":\"Färsk kycklingfilé\"," +
        "\"packageInformation\":\"Ca 925 g\",\"mechanicInfo\":\"109 kr/kg\",\"customerInformation\":\"Naturell\"}," +
        "\"category\":{\"articleGroupName\":\"Kött & chark\",\"articleGroupId\":6}," +
        "\"validTo\":\"2026-03-01T00:00:00\"," +
        "\"parsedMechanics\":{\"quantity\":0,\"value2\":\"109\",\"value4\":\"/kg\"}," +
        "\"stores\":[{\"regularPrice\":\"169,90\",\"referencePriceText\":\"Ord.pris 169:90 kr.\"}]," +
        "\"eans\":[{\"image\":\"https://assets.icanet.se/image.jpg\"}]" +
        "},{" +
        "\"id\":\"5003802338\"," +
        "\"details\":{\"brand\":\"Gevalia\",\"name\":\"Kaffe\"," +
        "\"packageInformation\":\"425-450 g\",\"mechanicInfo\":\"2 för 135 kr\",\"customerInformation\":\"\"}," +
        "\"category\":{\"articleGroupName\":\"Skafferivaror\",\"articleGroupId\":9}," +
        "\"validTo\":\"2026-03-01T00:00:00\"," +
        "\"parsedMechanics\":{\"quantity\":2,\"value2\":\"135\",\"value4\":\"\"}," +
        "\"stores\":[{\"regularPrice\":\"77,90\",\"referencePriceText\":\"Ord.pris 77:90 kr.\"}]," +
        "\"eans\":[{\"image\":\"https://assets.icanet.se/gevalia.jpg\"}]" +
        "}]}};</script></body></html>";

    @Test
    void icaParserExtractsOffersFromHtml() {
        StoreOfferService service = new StoreOfferTestHelper();
        List<StoreOffer> offers = service.parseIcaOffersFromHtml(
                ICA_HTML_SNIPPET, "ICA Kvantum Test", "ica-test");
        assertEquals(2, offers.size());
    }

    @Test
    void icaParserMapsProductNameAsBrandPlusName() {
        StoreOfferService service = new StoreOfferTestHelper();
        List<StoreOffer> offers = service.parseIcaOffersFromHtml(
                ICA_HTML_SNIPPET, "ICA Kvantum Test", "ica-test");
        assertEquals("Kronfågel. Ursprung Sverige Färsk kycklingfilé", offers.get(0).getProductName());
        assertEquals("Gevalia Kaffe", offers.get(1).getProductName());
    }

    @Test
    void icaParserSetsCorrectSalePriceForSimpleOffer() {
        StoreOfferService service = new StoreOfferTestHelper();
        List<StoreOffer> offers = service.parseIcaOffersFromHtml(
                ICA_HTML_SNIPPET, "ICA Kvantum Test", "ica-test");
        // "109 kr/kg", quantity=0 → salePrice = 109
        assertEquals(109.0, offers.get(0).getSalePrice(), 0.001);
    }

    @Test
    void icaParserDividesTotalPriceByQuantityForMultiBuyOffer() {
        StoreOfferService service = new StoreOfferTestHelper();
        List<StoreOffer> offers = service.parseIcaOffersFromHtml(
                ICA_HTML_SNIPPET, "ICA Kvantum Test", "ica-test");
        // "2 för 135 kr", quantity=2 → salePrice = 135/2 = 67.5
        assertEquals(67.5, offers.get(1).getSalePrice(), 0.001);
    }

    @Test
    void icaParserSetsOriginalPriceFromRegularPrice() {
        StoreOfferService service = new StoreOfferTestHelper();
        List<StoreOffer> offers = service.parseIcaOffersFromHtml(
                ICA_HTML_SNIPPET, "ICA Kvantum Test", "ica-test");
        assertEquals(169.90, offers.get(0).getOriginalPrice(), 0.001);
        assertEquals(77.90, offers.get(1).getOriginalPrice(), 0.001);
    }

    @Test
    void icaParserSetsValidToDate() {
        StoreOfferService service = new StoreOfferTestHelper();
        List<StoreOffer> offers = service.parseIcaOffersFromHtml(
                ICA_HTML_SNIPPET, "ICA Kvantum Test", "ica-test");
        assertEquals(LocalDate.of(2026, 3, 1), offers.get(0).getValidTo());
    }

    @Test
    void icaParserMapsMeatCategory() {
        StoreOfferService service = new StoreOfferTestHelper();
        List<StoreOffer> offers = service.parseIcaOffersFromHtml(
                ICA_HTML_SNIPPET, "ICA Kvantum Test", "ica-test");
        assertEquals("Meat", offers.get(0).getProductCategory());
        assertEquals("Pantry", offers.get(1).getProductCategory());
    }

    @Test
    void icaParserSetsImageUrl() {
        StoreOfferService service = new StoreOfferTestHelper();
        List<StoreOffer> offers = service.parseIcaOffersFromHtml(
                ICA_HTML_SNIPPET, "ICA Kvantum Test", "ica-test");
        assertEquals("https://assets.icanet.se/image.jpg", offers.get(0).getImageUrl());
    }

    @Test
    void icaParserReturnsEmptyListWhenNoMarker() {
        StoreOfferService service = new StoreOfferTestHelper();
        List<StoreOffer> offers = service.parseIcaOffersFromHtml(
                "<html><body>no data here</body></html>", "Store", "id");
        assertTrue(offers.isEmpty());
    }

    @Test
    void icaParserHandlesUndefinedValues() {
        StoreOfferService service = new StoreOfferTestHelper();
        String htmlWithUndefined = ICA_HTML_SNIPPET.replace("\"validTo\":\"2026-03-01T00:00:00\"",
                "\"validTo\":undefined");
        List<StoreOffer> offers = service.parseIcaOffersFromHtml(
                htmlWithUndefined, "ICA Kvantum Test", "ica-test");
        // Should still parse successfully, validTo defaults to +7 days
        assertFalse(offers.isEmpty());
        assertNotNull(offers.get(0).getValidTo());
    }

    @Test
    void icaParserHandlesPriceRange() {
        StoreOfferService service = new StoreOfferTestHelper();
        String htmlWithRange = ICA_HTML_SNIPPET.replace("\"regularPrice\":\"169,90\"",
                "\"regularPrice\":\"133,90-139,90\"");
        List<StoreOffer> offers = service.parseIcaOffersFromHtml(
                htmlWithRange, "ICA Kvantum Test", "ica-test");
        // Should use first price in range
        assertEquals(133.90, offers.get(0).getOriginalPrice(), 0.001);
    }

    /**
     * Test helper that exposes MenuService.getMenuDays without needing Spring context.
     */
    static class MenuServiceTestHelper extends MenuService {
        MenuServiceTestHelper() {
            super(null, null, null);
        }
    }

    /**
     * Test helper that exposes the package-private parseIcaOffersFromHtml method
     * without needing a Spring context or any dependencies.
     */
    static class StoreOfferTestHelper extends StoreOfferService {
        StoreOfferTestHelper() {
            super(null, null, null);
        }
    }
}
