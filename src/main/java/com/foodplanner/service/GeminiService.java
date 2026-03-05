package com.foodplanner.service;

import com.foodplanner.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.LocaleResolver;

import jakarta.servlet.http.HttpServletRequest;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@Service
public class GeminiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);

    private final ChatClient chatClient;

    private static final String[] DAYS = {"MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"};

    // ---- DTOs for structured AI responses ----

    private record MenuResponse(Map<String, DayMenuDto> days) {}
    private record DayMenuDto(List<PlannedMealDto> dinner) {}
    private record PlannedMealDto(String mealName, String description, double estimatedCost) {}
    private record SingleMealDto(String mealName, String description, double estimatedCost) {}
    private record RecipeDto(String name, String description, int servings,
                             int prepTimeMinutes, int cookTimeMinutes, String difficulty,
                             List<IngredientDto> ingredients, List<String> instructions,
                             List<String> tags) {}
    private record IngredientDto(String name, double amount, String unit) {}
    private record ShoppingItemDto(String name, double amount, String unit,
                                   String category, boolean onSale, String storeName) {}
    private record StoreOfferDto(String productName, double salePrice, double originalPrice,
                                  double discountPercent, String productCategory,
                                  String unit, String offerDescription) {}

    private final LocaleResolver localeResolver;

    /**
     * Inject ChatClient.Builder with required=false. Spring AI auto-configures
     * ChatClient.Builder after all user @Configuration classes are processed,
     * so services (initialized later) reliably receive it when the API key is set.
     * Using @ConditionalOnBean in a user @Configuration class does NOT work because
     * that condition is evaluated before auto-configurations run.
     */
    public GeminiService(@Autowired(required = false) ChatClient.Builder chatClientBuilder,
                         @Autowired(required = false) LocaleResolver localeResolver) {
        this.chatClient = chatClientBuilder != null ? chatClientBuilder.build() : null;
        this.localeResolver = localeResolver;
        if (this.chatClient != null) {
            log.info("Gemini AI configured via Spring AI – AI-powered menus enabled");
        }
    }

    /**
     * Returns a language instruction to append to AI prompts based on the current request locale.
     * Defaults to Swedish if no request context is available.
     */
    private String getLanguageInstruction() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null && localeResolver != null) {
                HttpServletRequest request = attrs.getRequest();
                Locale locale = localeResolver.resolveLocale(request);
                if ("en".equals(locale.getLanguage())) {
                    return "Generate all meal names, descriptions, and recipe content in English.\n";
                }
            }
        } catch (Exception e) {
            log.debug("Could not determine locale, defaulting to Swedish for AI prompts");
        }
        return "Generate all meal names, descriptions, and recipe content in Swedish.\n";
    }

    private boolean isConfigured() {
        return chatClient != null;
    }

    /**
     * Generate a weekly menu using Gemini AI via Spring AI.
     */
    public WeeklyMenu generateWeeklyMenu(String userId, MenuConfig config, List<StoreOffer> currentOffers) {
        return generateWeeklyMenu(userId, config, currentOffers, null);
    }

    /**
     * Generate a weekly menu using Gemini AI via Spring AI, with optional feedback on a previous menu.
     */
    public WeeklyMenu generateWeeklyMenu(String userId, MenuConfig config, List<StoreOffer> currentOffers, String feedback) {
        if (!isConfigured()) {
            log.warn("Gemini AI not configured – set GEMINI_API_KEY env var (get key at https://aistudio.google.com/app/apikey) – returning sample menu");
            return buildSampleMenu(userId, config);
        }

        try {
            String prompt = buildMenuPrompt(config, currentOffers, feedback);
            MenuResponse dto = callAi(prompt, config.getGeminiModel(), MenuResponse.class);
            return mapToMenu(userId, dto, config);
        } catch (Exception e) {
            log.error("Failed to generate AI menu response", e);
            return buildSampleMenu(userId, config);
        }
    }

    /**
     * Generate a recipe for a given meal using Gemini AI via Spring AI.
     */
    public Recipe generateRecipe(String userId, String mealName, MenuConfig config, List<StoreOffer> currentOffers) {
        if (!isConfigured()) {
            log.warn("Gemini AI not configured – set GEMINI_API_KEY env var (get key at https://aistudio.google.com/app/apikey) – returning sample recipe");
            return buildSampleRecipe(userId, mealName);
        }

        try {
            String prompt = buildRecipePrompt(mealName, config, currentOffers);
            RecipeDto dto = callAi(prompt, config.getGeminiModel(), RecipeDto.class);
            return mapToRecipe(userId, mealName, dto);
        } catch (Exception e) {
            log.error("Failed to generate AI recipe response", e);
            return buildSampleRecipe(userId, mealName);
        }
    }

    /**
     * Generate shopping list items for a given weekly menu using Gemini AI via Spring AI.
     */
    public List<ShoppingList.ShoppingItem> generateShoppingItems(WeeklyMenu menu,
                                                                   Map<String, Recipe> recipes,
                                                                   List<StoreOffer> offers,
                                                                   String model) {
        if (!isConfigured()) {
            return buildSampleShoppingItems(recipes, offers);
        }

        try {
            String prompt = buildShoppingListPrompt(menu, recipes, offers);
            List<ShoppingItemDto> dtos = callAi(prompt, model,
                    new ParameterizedTypeReference<List<ShoppingItemDto>>() {});
            return mapToShoppingItems(dtos, offers);
        } catch (Exception e) {
            log.error("Failed to generate AI shopping list response", e);
            return buildSampleShoppingItems(recipes, offers);
        }
    }

    /**
     * Generate a single replacement meal with optional user feedback.
     */
    public WeeklyMenu.PlannedMeal regenerateSingleMeal(String currentMealName, MenuConfig config,
                                                        List<StoreOffer> offers, String feedback) {
        if (!isConfigured()) {
            WeeklyMenu.PlannedMeal meal = new WeeklyMenu.PlannedMeal();
            meal.setMealName("Replacement Dish");
            meal.setDescription("A sample replacement dish");
            meal.setEstimatedCost(75.0);
            meal.setServings(config.getNumberOfPeople());
            return meal;
        }
        try {
            String prompt = buildSingleMealPrompt(currentMealName, config, offers, feedback);
            SingleMealDto dto = callAi(prompt, config.getGeminiModel(), SingleMealDto.class);
            return mapToPlannedMeal(dto, config.getNumberOfPeople());
        } catch (Exception e) {
            log.error("Failed to generate single meal AI response", e);
            WeeklyMenu.PlannedMeal meal = new WeeklyMenu.PlannedMeal();
            meal.setMealName("Replacement Dish");
            meal.setServings(config.getNumberOfPeople());
            return meal;
        }
    }

    /**
     * Invoke the Spring AI ChatClient and return a structured response of the given type.
     */
    private <T> T callAi(String prompt, String model, Class<T> responseType) {
        try {
            return chatClient.prompt()
                    .user(prompt)
                    .options(GoogleGenAiChatOptions.builder().model(model).build())
                    .call()
                    .entity(responseType);
        } catch (Exception e) {
            log.error("Error calling AI model via Spring AI", e);
            throw new RuntimeException("AI call failed: " + e.getMessage(), e);
        }
    }

    /**
     * Invoke the Spring AI ChatClient and return a structured list response.
     */
    private <T> T callAi(String prompt, String model, ParameterizedTypeReference<T> typeRef) {
        try {
            return chatClient.prompt()
                    .user(prompt)
                    .options(GoogleGenAiChatOptions.builder().model(model).build())
                    .call()
                    .entity(typeRef);
        } catch (Exception e) {
            log.error("Error calling AI model via Spring AI", e);
            throw new RuntimeException("AI call failed: " + e.getMessage(), e);
        }
    }

    private String buildMenuPrompt(MenuConfig config, List<StoreOffer> offers) {
        return buildMenuPrompt(config, offers, null);
    }

    private String buildMenuPrompt(MenuConfig config, List<StoreOffer> offers, String feedback) {
        String[] days = getConfiguredDays(config);
        StringBuilder sb = new StringBuilder();
        sb.append(getLanguageInstruction());
        sb.append("You are a professional meal planner. Create a menu for ").append(days.length).append(" days.\n\n");
        sb.append("Configuration:\n");
        sb.append("- Number of people: ").append(config.getNumberOfPeople()).append("\n");

        if (config.getDietaryPreferences() != null && !config.getDietaryPreferences().isEmpty()) {
            sb.append("- Dietary preferences: ").append(String.join(", ", config.getDietaryPreferences())).append("\n");
        }
        if (config.getAllergies() != null && !config.getAllergies().isEmpty()) {
            sb.append("- Allergies/restrictions: ").append(String.join(", ", config.getAllergies())).append("\n");
        }
        if (config.getCuisinePreferences() != null && !config.getCuisinePreferences().isBlank()) {
            sb.append("- Cuisine preferences: ").append(config.getCuisinePreferences()).append("\n");
        }
        if (config.getAdditionalInstructions() != null && !config.getAdditionalInstructions().isBlank()) {
            sb.append("- Additional instructions: ").append(config.getAdditionalInstructions()).append("\n");
        }
        if (config.isPreferBudgetFriendly()) {
            sb.append("- Prefer budget-friendly meals\n");
        }
        if (feedback != null && !feedback.isBlank()) {
            sb.append("- User feedback on previous menu: ").append(feedback).append("\n");
            sb.append("  Please generate a completely different menu based on this feedback.\n");
        }

        if (!offers.isEmpty()) {
            sb.append("\nCurrent store offers (try to use these ingredients to save money):\n");
            for (StoreOffer offer : offers.subList(0, Math.min(offers.size(), 20))) {
                sb.append("- ").append(offer.getProductName())
                  .append(" (").append(offer.getStoreName()).append(", ")
                  .append(String.format("%.0f%%", offer.getDiscountPercent())).append(" off)\n");
            }
        }

        sb.append("\nProvide dinner meals for these days: ").append(String.join(", ", days)).append(".\n");
        sb.append("estimatedCost should be in SEK per serving.");

        return sb.toString();
    }

    private String buildRecipePrompt(String mealName, MenuConfig config, List<StoreOffer> offers) {
        StringBuilder sb = new StringBuilder();
        sb.append(getLanguageInstruction());
        sb.append("Create a detailed recipe for: ").append(mealName).append("\n\n");
        sb.append("Number of people: ").append(config.getNumberOfPeople()).append("\n");

        if (config.getDietaryPreferences() != null && !config.getDietaryPreferences().isEmpty()) {
            sb.append("Dietary requirements: ").append(String.join(", ", config.getDietaryPreferences())).append("\n");
        }

        if (!offers.isEmpty()) {
            sb.append("\nAvailable store offers (prefer these ingredients):\n");
            for (StoreOffer offer : offers.subList(0, Math.min(offers.size(), 10))) {
                sb.append("- ").append(offer.getProductName())
                  .append(" (on sale at ").append(offer.getStoreName()).append(")\n");
            }
        }

        sb.append("\ndifficulty must be one of: EASY, MEDIUM, HARD.");

        return sb.toString();
    }

    private String buildSingleMealPrompt(String currentMealName, MenuConfig config,
                                          List<StoreOffer> offers, String feedback) {
        StringBuilder sb = new StringBuilder();
        sb.append(getLanguageInstruction());
        sb.append("Suggest one replacement dinner meal instead of: \"").append(currentMealName).append("\"\n");
        sb.append("Number of people: ").append(config.getNumberOfPeople()).append("\n");
        if (feedback != null && !feedback.isBlank()) {
            sb.append("Reason for replacement / feedback: ").append(feedback).append("\n");
        }
        if (config.getDietaryPreferences() != null && !config.getDietaryPreferences().isEmpty()) {
            sb.append("Dietary preferences: ").append(String.join(", ", config.getDietaryPreferences())).append("\n");
        }
        if (!offers.isEmpty()) {
            sb.append("Ingredients on sale you may use: ");
            for (int i = 0; i < Math.min(10, offers.size()); i++) {
                if (i > 0) sb.append(", ");
                sb.append(offers.get(i).getProductName());
            }
            sb.append("\n");
        }
        sb.append("estimatedCost should be in SEK.");
        return sb.toString();
    }

    private WeeklyMenu.PlannedMeal mapToPlannedMeal(SingleMealDto dto, int servings) {
        WeeklyMenu.PlannedMeal meal = new WeeklyMenu.PlannedMeal();
        meal.setServings(servings);
        if (dto != null) {
            meal.setMealName(dto.mealName() != null ? dto.mealName() : "Replacement Dish");
            meal.setDescription(dto.description() != null ? dto.description() : "");
            meal.setEstimatedCost(dto.estimatedCost());
        } else {
            meal.setMealName("Replacement Dish");
        }
        return meal;
    }

    private String[] getConfiguredDays(MenuConfig config) {
        if (config == null) return DAYS.clone();
        String start = config.getStartDayOfWeek() != null ? config.getStartDayOfWeek().toUpperCase() : "MONDAY";
        int span = config.getMenuSpanDays() > 0 ? Math.min(config.getMenuSpanDays(), 7) : 7;
        int startIdx = 0;
        for (int i = 0; i < DAYS.length; i++) {
            if (DAYS[i].equals(start)) { startIdx = i; break; }
        }
        String[] result = new String[span];
        for (int i = 0; i < span; i++) {
            result[i] = DAYS[(startIdx + i) % 7];
        }
        return result;
    }

    private String buildShoppingListPrompt(WeeklyMenu menu, Map<String, Recipe> recipes, List<StoreOffer> offers) {
        StringBuilder sb = new StringBuilder();
        sb.append(getLanguageInstruction());
        sb.append("Create a consolidated shopping list for the following weekly menu.\n\n");
        sb.append("Meals this week:\n");

        if (menu.getDays() != null) {
            for (Map.Entry<String, WeeklyMenu.DayMenu> entry : menu.getDays().entrySet()) {
                WeeklyMenu.DayMenu day = entry.getValue();
                if (day.getDinner() != null) {
                    for (WeeklyMenu.PlannedMeal meal : day.getDinner()) {
                        sb.append("- ").append(entry.getKey()).append(": ").append(meal.getMealName()).append("\n");
                        if (meal.getRecipeId() != null && recipes.containsKey(meal.getRecipeId())) {
                            Recipe recipe = recipes.get(meal.getRecipeId());
                            if (recipe.getIngredients() != null) {
                                for (Recipe.Ingredient ing : recipe.getIngredients()) {
                                    sb.append("  - ").append(ing.getAmount()).append(" ")
                                      .append(ing.getUnit()).append(" ").append(ing.getName()).append("\n");
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!offers.isEmpty()) {
            sb.append("\nCurrent store offers:\n");
            for (StoreOffer offer : offers.subList(0, Math.min(offers.size(), 20))) {
                sb.append("- ").append(offer.getProductName())
                  .append(" at ").append(offer.getStoreName()).append("\n");
            }
        }

        sb.append("\nConsolidate all ingredients into a single shopping list. ");
        sb.append("Combine duplicates, categorize by section (Produce, Dairy, Meat, Pantry, etc.), ");
        sb.append("and mark items that are on sale.\n");

        return sb.toString();
    }

    private WeeklyMenu mapToMenu(String userId, MenuResponse dto, MenuConfig config) {
        WeeklyMenu menu = new WeeklyMenu();
        menu.setUserId(userId);
        menu.setAiGenerated(true);

        LocalDate nextMonday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        menu.setWeekStart(nextMonday);
        menu.setWeekEnd(nextMonday.plusDays(6));
        menu.setWeekId(MenuService.formatWeekId(nextMonday));

        Map<String, WeeklyMenu.DayMenu> days = new LinkedHashMap<>();
        Map<String, DayMenuDto> daysDto = (dto != null && dto.days() != null) ? dto.days() : Map.of();
        int servings = config.getNumberOfPeople();
        for (String day : DAYS) {
            WeeklyMenu.DayMenu dayMenu = new WeeklyMenu.DayMenu();
            DayMenuDto dayDto = daysDto.get(day);
            if (dayDto != null) {
                dayMenu.setDinner(mapToPlannedMeals(dayDto.dinner(), servings));
            }
            days.put(day, dayMenu);
        }
        menu.setDays(days);
        return menu;
    }

    private List<WeeklyMenu.PlannedMeal> mapToPlannedMeals(List<PlannedMealDto> dtos, int servings) {
        if (dtos == null) return List.of();
        return dtos.stream().map(dto -> {
            WeeklyMenu.PlannedMeal meal = new WeeklyMenu.PlannedMeal();
            meal.setMealName(dto.mealName() != null ? dto.mealName() : "Unknown meal");
            meal.setDescription(dto.description() != null ? dto.description() : "");
            meal.setEstimatedCost(dto.estimatedCost());
            meal.setServings(servings);
            return meal;
        }).toList();
    }

    private Recipe mapToRecipe(String userId, String mealName, RecipeDto dto) {
        if (dto == null) return buildSampleRecipe(userId, mealName);
        Recipe recipe = new Recipe();
        recipe.setUserId(userId);
        recipe.setName(dto.name() != null ? dto.name() : mealName);
        recipe.setDescription(dto.description() != null ? dto.description() : "");
        recipe.setServings(dto.servings() > 0 ? dto.servings() : 2);
        recipe.setPrepTimeMinutes(dto.prepTimeMinutes() > 0 ? dto.prepTimeMinutes() : 15);
        recipe.setCookTimeMinutes(dto.cookTimeMinutes() > 0 ? dto.cookTimeMinutes() : 30);
        if (dto.difficulty() != null) {
            try {
                recipe.setDifficulty(Recipe.DifficultyLevel.valueOf(dto.difficulty().toUpperCase()));
            } catch (IllegalArgumentException e) {
                recipe.setDifficulty(Recipe.DifficultyLevel.MEDIUM);
            }
        } else {
            recipe.setDifficulty(Recipe.DifficultyLevel.MEDIUM);
        }
        if (dto.ingredients() != null) {
            recipe.setIngredients(dto.ingredients().stream()
                    .map(i -> new Recipe.Ingredient(i.name(), i.amount(), i.unit()))
                    .toList());
        } else {
            recipe.setIngredients(List.of());
        }
        recipe.setInstructions(dto.instructions() != null ? dto.instructions() : List.of());
        recipe.setTags(dto.tags() != null ? dto.tags() : List.of());
        return recipe;
    }

    private List<ShoppingList.ShoppingItem> mapToShoppingItems(List<ShoppingItemDto> dtos, List<StoreOffer> offers) {
        if (dtos == null) return buildSampleShoppingItems(Map.of(), offers);
        return dtos.stream().map(dto -> {
            ShoppingList.ShoppingItem item = new ShoppingList.ShoppingItem();
            item.setId(UUID.randomUUID().toString());
            item.setName(dto.name() != null ? dto.name() : "");
            item.setAmount(dto.amount() > 0 ? dto.amount() : 1);
            item.setUnit(dto.unit() != null ? dto.unit() : "");
            item.setCategory(dto.category() != null ? dto.category() : "Other");
            item.setOnSale(dto.onSale());
            if (dto.storeName() != null && !"null".equals(dto.storeName())) {
                item.setStoreName(dto.storeName());
            }
            return item;
        }).toList();
    }

    // ---- Store offer extraction ----

    /**
     * Ask Gemini to find the store-specific offers page URL for a Swedish grocery store.
     * Used for custom stores that are not in the static KNOWN_STORES catalogue.
     *
     * @param storeName  full store name, e.g. "ICA Kvantum Malmborgs Caroli"
     * @param chain      chain key, e.g. "ica"
     * @param chainBase  chain-level offers URL as a reference, e.g. "https://www.ica.se/erbjudanden/"
     * @return the store-specific URL, or {@code null} if Gemini cannot determine it
     */
    public String findOffersUrl(String storeName, String chain, String chainBase) {
        if (!isConfigured()) return null;
        String safeName = storeName == null ? "" : storeName.replaceAll("[\\p{Cntrl}]", " ").trim();
        String prompt = "Vilken URL används för veckans erbjudanden från \"" + safeName + "\" på dess butiksspecifika sida?\n\n"
                + "URL-mönstret för svenska matbutikskedjor:\n"
                + "- ICA: https://www.ica.se/erbjudanden/{butiksnamn-med-bindestreck}-{numeriskt-butiks-id}/\n"
                + "  Exempel: ICA Kvantum Malmborgs Caroli → https://www.ica.se/erbjudanden/ica-kvantum-malmborgs-caroli-1004490/\n"
                + "  Exempel: ICA Maxi Haninge → https://www.ica.se/erbjudanden/ica-maxi-haninge-1003434/\n"
                + "- Willys: https://www.willys.se/erbjudanden/{butiksnamn-med-bindestreck}-{numeriskt-butiks-id}/\n"
                + "- Coop: https://www.coop.se/erbjudanden/{butiksnamn-med-bindestreck}-{numeriskt-butiks-id}/\n\n"
                + "Svara med ENBART URL:en på en rad. Inga förklaringar, ingen markdown, inga citattecken.";
        try {
            // Enable Google Search grounding so Gemini can look up the current store URL
            // rather than relying solely on training data (which may be outdated).
            GoogleGenAiChatOptions searchOptions = GoogleGenAiChatOptions.builder()
                    .googleSearchRetrieval(true)
                    .build();
            String response = chatClient.prompt()
                    .user(prompt)
                    .options(searchOptions)
                    .call()
                    .content();
            if (response == null) return null;
            String url = response.trim().lines().findFirst().orElse("").trim();
            // Only return if it looks like a plausible HTTPS URL
            if (url.startsWith("https://")) {
                log.info("Gemini inferred offers URL for '{}': {}", storeName, url);
                return url;
            }
            log.info("Gemini did not return a valid URL for '{}', got: {}", storeName, url);
            return null;
        } catch (Exception e) {
            log.debug("Failed to infer offers URL for '{}' via Gemini: {}", storeName, e.getMessage());
            return null;
        }
    }

    /**
     * Ask the AI to extract real product offers from rendered page content fetched by Playwright.
     * The {@code pageContent} argument is the visible text of the store's offers page.
     */
    public List<StoreOffer> extractOffersFromHtml(String pageContent, String storeName, String storeId) {
        if (!isConfigured()) return List.of();
        // Strip control characters from the store name to prevent prompt injection
        String safeName = storeName == null ? "" : storeName.replaceAll("[\\p{Cntrl}]", " ").trim();
        String prompt = "You are extracting grocery store offer data from a Swedish store's offers page.\n"
                + "Store name: \"" + safeName + "\"\n\n"
                + "Below is the full visible text of the store's current offers page:\n"
                + "--- BEGIN CONTENT ---\n" + pageContent + "\n--- END CONTENT ---\n\n"
                + "IMPORTANT RULES:\n"
                + "1. Extract EVERY single product offer present in the content — do not stop early or truncate the list.\n"
                + "   Swedish store pages typically contain 30–80 weekly offers; extract them all.\n"
                + "2. Always include the BRAND name in productName (e.g. \"Arla Mellanmjölk 1,5%\", not just \"Mjölk\").\n"
                + "3. For multi-buy deals (e.g. \"2 för 25 kr\", \"3 för 2\", \"Köp 2 betala för 1\"):\n"
                + "   - Set salePrice to the effective per-unit price (total deal price ÷ required quantity).\n"
                + "   - Set originalPrice to the normal single-unit price (if shown).\n"
                + "   - Set offerDescription to the verbatim deal text from the page (e.g. \"2 för 25 kr\").\n"
                + "   - Calculate discountPercent = ((originalPrice - salePrice) / originalPrice) * 100.\n"
                + "4. For simple price-cut offers, leave offerDescription as an empty string.\n"
                + "5. productCategory must be one of: Meat, Fish, Dairy, Produce, Pantry, Beverages, Snacks, Bakery, Frozen, Cleaning, Other.\n";
        try {
            List<StoreOfferDto> dtos = callAi(prompt, "gemini-2.5-flash",
                    new ParameterizedTypeReference<List<StoreOfferDto>>() {});
            List<StoreOffer> offers = mapToStoreOffers(dtos, storeName, storeId);
            log.info("AI extracted {} offers from page content for store '{}'", offers.size(), storeName);
            return offers;
        } catch (Exception e) {
            log.warn("Failed to extract offers from page content for '{}': {}", storeName, e.getMessage());
            return List.of();
        }
    }

    /**
     * Fallback: ask Gemini to generate plausible weekly offers based solely on its training
     * knowledge of the store. Used when Playwright cannot fetch the live offers page.
     */
    public List<StoreOffer> generateOffersForStore(String storeName, String storeId) {
        if (!isConfigured()) return List.of();
        String prompt = "You are an expert on Swedish grocery stores and their typical weekly promotions.\n"
                + "Generate 15–20 realistic current weekly offers (\"veckans erbjudanden\") for this store: \""
                + storeName + "\"\n\n"
                + "Use typical Swedish grocery product names and realistic Swedish krona prices.\n"
                + "Include a variety of categories: Meat, Fish, Dairy, Produce, Pantry, Beverages, Snacks.\n"
                + "Sale prices should be 10–50% lower than original prices.\n";
        try {
            List<StoreOfferDto> dtos = callAi(prompt, "gemini-2.5-flash",
                    new ParameterizedTypeReference<List<StoreOfferDto>>() {});
            List<StoreOffer> offers = mapToStoreOffers(dtos, storeName, storeId);
            log.info("Gemini generated {} fallback offers for store '{}'", offers.size(), storeName);
            return offers;
        } catch (Exception e) {
            log.warn("Failed to generate fallback offers for '{}': {}", storeName, e.getMessage());
            return List.of();
        }
    }

    private List<StoreOffer> mapToStoreOffers(List<StoreOfferDto> dtos, String storeName, String storeId) {
        if (dtos == null) return List.of();
        List<StoreOffer> offers = new ArrayList<>();
        for (StoreOfferDto dto : dtos) {
            if (dto.productName() == null || dto.productName().isBlank()) continue;
            StoreOffer offer = new StoreOffer();
            offer.setId(UUID.randomUUID().toString());
            offer.setStoreId(storeId);
            offer.setStoreName(storeName);
            offer.setProductName(dto.productName());
            offer.setSalePrice(dto.salePrice());
            offer.setOriginalPrice(dto.originalPrice());
            offer.setProductCategory(dto.productCategory() != null ? dto.productCategory() : "Other");
            offer.setUnit(dto.unit() != null ? dto.unit() : "");
            offer.setOfferDescription(dto.offerDescription() != null ? dto.offerDescription() : "");
            offer.setFetchedAt(Instant.now());
            offer.setValidFrom(LocalDate.now());
            offer.setValidTo(LocalDate.now().plusDays(7));
            // Prefer AI-provided discountPercent: the AI accounts for deal structure
            // (e.g., for "3 for 2" there's no meaningful originalPrice/salePrice pair),
            // whereas the simple formula below only works for straightforward price cuts.
            if (dto.discountPercent() > 0) {
                offer.setDiscountPercent(dto.discountPercent());
            } else if (offer.getOriginalPrice() > 0 && offer.getSalePrice() > 0
                    && offer.getOriginalPrice() > offer.getSalePrice()) {
                double discount = ((offer.getOriginalPrice() - offer.getSalePrice())
                        / offer.getOriginalPrice()) * 100;
                offer.setDiscountPercent(discount);
            }
            offers.add(offer);
        }
        return offers;
    }

    // ---- Fallback sample data ----

    private WeeklyMenu buildSampleMenu(String userId, MenuConfig config) {
        WeeklyMenu menu = new WeeklyMenu();
        menu.setUserId(userId);
        menu.setAiGenerated(false);

        LocalDate nextMonday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        menu.setWeekStart(nextMonday);
        menu.setWeekEnd(nextMonday.plusDays(6));
        menu.setWeekId(MenuService.formatWeekId(nextMonday));

        String[][] sampleMeals = {
            {"Spaghetti Bolognese", "Classic Italian pasta dish"},
            {"Grilled Chicken with Vegetables", "Healthy and simple"},
            {"Vegetable Stir Fry", "Quick and nutritious"},
            {"Beef Tacos", "Mexican-inspired family favorite"},
            {"Salmon with Roasted Potatoes", "Light and delicious"},
            {"Homemade Pizza", "Fun Friday dinner"},
            {"Roast Chicken with Sides", "Sunday classic"}
        };

        Map<String, WeeklyMenu.DayMenu> days = new LinkedHashMap<>();
        for (int i = 0; i < DAYS.length; i++) {
            WeeklyMenu.DayMenu dayMenu = new WeeklyMenu.DayMenu();
            WeeklyMenu.PlannedMeal meal = new WeeklyMenu.PlannedMeal();
            meal.setMealName(sampleMeals[i][0]);
            meal.setServings(config.getNumberOfPeople());
            meal.setEstimatedCost(8.0 + i * 1.5);
            dayMenu.setDinner(List.of(meal));
            days.put(DAYS[i], dayMenu);
        }
        menu.setDays(days);
        return menu;
    }

    private Recipe buildSampleRecipe(String userId, String mealName) {
        Recipe recipe = new Recipe();
        recipe.setUserId(userId);
        recipe.setName(mealName);
        recipe.setDescription("A delicious " + mealName + " recipe");
        recipe.setServings(2);
        recipe.setPrepTimeMinutes(15);
        recipe.setCookTimeMinutes(30);
        recipe.setDifficulty(Recipe.DifficultyLevel.EASY);
        recipe.setIngredients(List.of(
                new Recipe.Ingredient("Main ingredient", 400, "g"),
                new Recipe.Ingredient("Seasoning", 1, "tsp"),
                new Recipe.Ingredient("Oil", 2, "tbsp")
        ));
        recipe.setInstructions(List.of(
                "Prepare all ingredients.",
                "Cook according to recipe.",
                "Serve hot."
        ));
        recipe.setTags(List.of("quick", "easy"));
        return recipe;
    }

    private List<ShoppingList.ShoppingItem> buildSampleShoppingItems(Map<String, Recipe> recipes,
                                                                       List<StoreOffer> offers) {
        List<ShoppingList.ShoppingItem> items = new ArrayList<>();
        String[][] sampleItems = {
            {"Chicken breast", "500", "g", "Meat"},
            {"Pasta", "400", "g", "Pantry"},
            {"Tomatoes", "4", "pcs", "Produce"},
            {"Onion", "2", "pcs", "Produce"},
            {"Garlic", "1", "head", "Produce"},
            {"Olive oil", "1", "bottle", "Pantry"},
            {"Salt", "1", "pkg", "Pantry"}
        };
        for (String[] item : sampleItems) {
            ShoppingList.ShoppingItem si = new ShoppingList.ShoppingItem();
            si.setId(UUID.randomUUID().toString());
            si.setName(item[0]);
            si.setAmount(Double.parseDouble(item[1]));
            si.setUnit(item[2]);
            si.setCategory(item[3]);
            items.add(si);
        }
        return items;
    }
}
