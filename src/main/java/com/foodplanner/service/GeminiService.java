package com.foodplanner.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodplanner.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private static final String[] DAYS = {"MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"};

    /**
     * Inject ChatClient.Builder with required=false. Spring AI auto-configures
     * ChatClient.Builder after all user @Configuration classes are processed,
     * so services (initialized later) reliably receive it when the API key is set.
     * Using @ConditionalOnBean in a user @Configuration class does NOT work because
     * that condition is evaluated before auto-configurations run.
     */
    public GeminiService(@Autowired(required = false) ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder != null ? chatClientBuilder.build() : null;
        if (this.chatClient != null) {
            log.info("Gemini AI configured via Spring AI – AI-powered menus enabled");
        }
    }

    private boolean isConfigured() {
        return chatClient != null;
    }

    /**
     * Generate a weekly menu using Gemini AI via Spring AI.
     */
    public WeeklyMenu generateWeeklyMenu(String userId, MenuConfig config, List<StoreOffer> currentOffers) {
        if (!isConfigured()) {
            log.warn("Gemini AI not configured – set GEMINI_API_KEY env var (get key at https://aistudio.google.com/app/apikey) – returning sample menu");
            return buildSampleMenu(userId, config);
        }

        String prompt = buildMenuPrompt(config, currentOffers);
        String response = callAi(prompt);
        return parseMenuResponse(userId, response, config);
    }

    /**
     * Generate a recipe for a given meal using Gemini AI via Spring AI.
     */
    public Recipe generateRecipe(String userId, String mealName, MenuConfig config, List<StoreOffer> currentOffers) {
        if (!isConfigured()) {
            log.warn("Gemini AI not configured – set GEMINI_API_KEY env var (get key at https://aistudio.google.com/app/apikey) – returning sample recipe");
            return buildSampleRecipe(userId, mealName);
        }

        String prompt = buildRecipePrompt(mealName, config, currentOffers);
        String response = callAi(prompt);
        return parseRecipeResponse(userId, mealName, response);
    }

    /**
     * Generate shopping list items for a given weekly menu using Gemini AI via Spring AI.
     */
    public List<ShoppingList.ShoppingItem> generateShoppingItems(WeeklyMenu menu,
                                                                   Map<String, Recipe> recipes,
                                                                   List<StoreOffer> offers) {
        if (!isConfigured()) {
            return buildSampleShoppingItems(recipes, offers);
        }

        String prompt = buildShoppingListPrompt(menu, recipes, offers);
        String response = callAi(prompt);
        return parseShoppingListResponse(response, offers);
    }

    /**
     * Invoke the Spring AI ChatClient and return the raw text response.
     */
    private String callAi(String prompt) {
        try {
            return chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("Error calling AI model via Spring AI", e);
            throw new RuntimeException("AI call failed: " + e.getMessage(), e);
        }
    }

    private String buildMenuPrompt(MenuConfig config, List<StoreOffer> offers) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a professional meal planner. Create a weekly dinner menu for 7 days.\n\n");
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

        if (!offers.isEmpty()) {
            sb.append("\nCurrent store offers (try to use these ingredients to save money):\n");
            for (StoreOffer offer : offers.subList(0, Math.min(offers.size(), 20))) {
                sb.append("- ").append(offer.getProductName())
                  .append(" (").append(offer.getStoreName()).append(", ")
                  .append(String.format("%.0f%%", offer.getDiscountPercent())).append(" off)\n");
            }
        }

        sb.append("\nReturn a JSON object with this exact structure:\n");
        sb.append("{\n");
        sb.append("  \"days\": {\n");
        sb.append("    \"MONDAY\": {\"dinner\": [{\"mealName\": \"string\", \"description\": \"string\", \"estimatedCost\": number}]},\n");
        sb.append("    \"TUESDAY\": {\"dinner\": [{\"mealName\": \"string\", \"description\": \"string\", \"estimatedCost\": number}]},\n");
        sb.append("    \"WEDNESDAY\": {\"dinner\": [{\"mealName\": \"string\", \"description\": \"string\", \"estimatedCost\": number}]},\n");
        sb.append("    \"THURSDAY\": {\"dinner\": [{\"mealName\": \"string\", \"description\": \"string\", \"estimatedCost\": number}]},\n");
        sb.append("    \"FRIDAY\": {\"dinner\": [{\"mealName\": \"string\", \"description\": \"string\", \"estimatedCost\": number}]},\n");
        sb.append("    \"SATURDAY\": {\"dinner\": [{\"mealName\": \"string\", \"description\": \"string\", \"estimatedCost\": number}]},\n");
        sb.append("    \"SUNDAY\": {\"dinner\": [{\"mealName\": \"string\", \"description\": \"string\", \"estimatedCost\": number}]}\n");
        sb.append("  }\n");
        sb.append("}\n");
        sb.append("Respond with valid JSON only, no markdown fences.");

        return sb.toString();
    }

    private String buildRecipePrompt(String mealName, MenuConfig config, List<StoreOffer> offers) {
        StringBuilder sb = new StringBuilder();
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

        sb.append("\nReturn a JSON object with this structure:\n");
        sb.append("{\n");
        sb.append("  \"name\": \"string\",\n");
        sb.append("  \"description\": \"string\",\n");
        sb.append("  \"servings\": number,\n");
        sb.append("  \"prepTimeMinutes\": number,\n");
        sb.append("  \"cookTimeMinutes\": number,\n");
        sb.append("  \"difficulty\": \"EASY|MEDIUM|HARD\",\n");
        sb.append("  \"ingredients\": [{\"name\": \"string\", \"amount\": number, \"unit\": \"string\"}],\n");
        sb.append("  \"instructions\": [\"step 1\", \"step 2\"],\n");
        sb.append("  \"tags\": [\"tag1\", \"tag2\"]\n");
        sb.append("}\n");
        sb.append("Respond with valid JSON only, no markdown fences.");

        return sb.toString();
    }

    private String buildShoppingListPrompt(WeeklyMenu menu, Map<String, Recipe> recipes, List<StoreOffer> offers) {
        StringBuilder sb = new StringBuilder();
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
        sb.append("and mark items that are on sale.\n\n");
        sb.append("Return JSON array only, no markdown fences:\n");
        sb.append("[{\"name\": \"string\", \"amount\": number, \"unit\": \"string\", \"category\": \"string\", \"onSale\": boolean, \"storeName\": \"string or null\"}]\n");

        return sb.toString();
    }

    private WeeklyMenu parseMenuResponse(String userId, String response, MenuConfig config) {
        try {
            log.debug("Raw AI menu response (length={}): {}", response == null ? 0 : response.length(), response);
            String json = stripMarkdownFences(response);
            JsonNode root = objectMapper.readTree(json);
            WeeklyMenu menu = new WeeklyMenu();
            menu.setUserId(userId);
            menu.setAiGenerated(true);

            LocalDate nextMonday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
            menu.setWeekStart(nextMonday);
            menu.setWeekEnd(nextMonday.plusDays(6));
            menu.setWeekId(MenuService.formatWeekId(nextMonday));

            Map<String, WeeklyMenu.DayMenu> days = new LinkedHashMap<>();
            JsonNode daysNode = root.path("days");
            for (String day : DAYS) {
                JsonNode dayNode = daysNode.path(day);
                WeeklyMenu.DayMenu dayMenu = new WeeklyMenu.DayMenu();
                if (!dayNode.isMissingNode()) {
                    dayMenu.setDinner(parsePlannedMeals(dayNode.path("dinner")));
                    dayMenu.setLunch(parsePlannedMeals(dayNode.path("lunch")));
                    dayMenu.setBreakfast(parsePlannedMeals(dayNode.path("breakfast")));
                }
                days.put(day, dayMenu);
            }
            menu.setDays(days);
            return menu;
        } catch (Exception e) {
            log.error("Failed to parse AI menu response", e);
            return buildSampleMenu(userId, config);
        }
    }

    private List<WeeklyMenu.PlannedMeal> parsePlannedMeals(JsonNode mealsNode) {
        List<WeeklyMenu.PlannedMeal> meals = new ArrayList<>();
        if (mealsNode.isArray()) {
            for (JsonNode mealNode : mealsNode) {
                WeeklyMenu.PlannedMeal meal = new WeeklyMenu.PlannedMeal();
                meal.setMealName(mealNode.path("mealName").asText("Unknown meal"));
                meal.setEstimatedCost(mealNode.path("estimatedCost").asDouble(0));
                meal.setServings(2);
                meals.add(meal);
            }
        }
        return meals;
    }

    private Recipe parseRecipeResponse(String userId, String mealName, String response) {
        try {
            log.debug("Raw AI recipe response (length={}): {}", response == null ? 0 : response.length(), response);
            String json = stripMarkdownFences(response);
            JsonNode root = objectMapper.readTree(json);
            Recipe recipe = new Recipe();
            recipe.setUserId(userId);
            recipe.setName(root.path("name").asText(mealName));
            recipe.setDescription(root.path("description").asText(""));
            recipe.setServings(root.path("servings").asInt(2));
            recipe.setPrepTimeMinutes(root.path("prepTimeMinutes").asInt(15));
            recipe.setCookTimeMinutes(root.path("cookTimeMinutes").asInt(30));

            String diffStr = root.path("difficulty").asText("MEDIUM");
            try {
                recipe.setDifficulty(Recipe.DifficultyLevel.valueOf(diffStr.toUpperCase()));
            } catch (IllegalArgumentException e) {
                recipe.setDifficulty(Recipe.DifficultyLevel.MEDIUM);
            }

            List<Recipe.Ingredient> ingredients = new ArrayList<>();
            JsonNode ingsNode = root.path("ingredients");
            if (ingsNode.isArray()) {
                for (JsonNode ingNode : ingsNode) {
                    Recipe.Ingredient ing = new Recipe.Ingredient(
                            ingNode.path("name").asText(),
                            ingNode.path("amount").asDouble(1),
                            ingNode.path("unit").asText("")
                    );
                    ingredients.add(ing);
                }
            }
            recipe.setIngredients(ingredients);

            List<String> instructions = new ArrayList<>();
            JsonNode instNode = root.path("instructions");
            if (instNode.isArray()) {
                for (JsonNode step : instNode) {
                    instructions.add(step.asText());
                }
            }
            recipe.setInstructions(instructions);

            List<String> tags = new ArrayList<>();
            JsonNode tagsNode = root.path("tags");
            if (tagsNode.isArray()) {
                for (JsonNode tag : tagsNode) {
                    tags.add(tag.asText());
                }
            }
            recipe.setTags(tags);

            return recipe;
        } catch (Exception e) {
            log.error("Failed to parse AI recipe response", e);
            return buildSampleRecipe(userId, mealName);
        }
    }

    private List<ShoppingList.ShoppingItem> parseShoppingListResponse(String response, List<StoreOffer> offers) {
        try {
            log.debug("Raw AI shopping list response (length={}): {}", response == null ? 0 : response.length(), response);
            String json = stripMarkdownFences(response);
            JsonNode root = objectMapper.readTree(json);
            List<ShoppingList.ShoppingItem> items = new ArrayList<>();
            if (root.isArray()) {
                for (JsonNode itemNode : root) {
                    ShoppingList.ShoppingItem item = new ShoppingList.ShoppingItem();
                    item.setId(UUID.randomUUID().toString());
                    item.setName(itemNode.path("name").asText());
                    item.setAmount(itemNode.path("amount").asDouble(1));
                    item.setUnit(itemNode.path("unit").asText(""));
                    item.setCategory(itemNode.path("category").asText("Other"));
                    item.setOnSale(itemNode.path("onSale").asBoolean(false));
                    String storeName = itemNode.path("storeName").asText(null);
                    if (!"null".equals(storeName)) {
                        item.setStoreName(storeName);
                    }
                    items.add(item);
                }
            }
            return items;
        } catch (Exception e) {
            log.error("Failed to parse AI shopping list response", e);
            return buildSampleShoppingItems(Map.of(), offers);
        }
    }

    /** Strip optional markdown code fences that some models still add despite instructions. */
    private String stripMarkdownFences(String text) {
        if (text == null) return "{}";
        String trimmed = text.strip();

        // Handle code fences that may appear anywhere in the response, e.g. when Gemini
        // adds a preamble like "Here are the offers:\n```json\n[...]\n```"
        int fenceStart = trimmed.indexOf("```");
        if (fenceStart >= 0) {
            // Skip the opening fence line (e.g. ```json or ```)
            int firstNewline = trimmed.indexOf('\n', fenceStart);
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline > fenceStart && lastFence > firstNewline) {
                return trimmed.substring(firstNewline + 1, lastFence).strip();
            }
        }

        // Also handle a response that starts with [ or { directly — return as-is
        if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            return trimmed;
        }

        // Last resort: find first [ or { (JSON array/object start) within the response
        int arrayStart = trimmed.indexOf('[');
        int objectStart = trimmed.indexOf('{');
        if (arrayStart >= 0 || objectStart >= 0) {
            int jsonStart;
            if (arrayStart < 0) jsonStart = objectStart;
            else if (objectStart < 0) jsonStart = arrayStart;
            else jsonStart = Math.min(arrayStart, objectStart);

            int arrayEnd = trimmed.lastIndexOf(']');
            int objectEnd = trimmed.lastIndexOf('}');
            int jsonEnd = Math.max(arrayEnd, objectEnd);
            if (jsonEnd > jsonStart) {
                return trimmed.substring(jsonStart, jsonEnd + 1);
            }
        }

        return trimmed;
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
                + "5. productCategory must be one of: Meat, Fish, Dairy, Produce, Pantry, Beverages, Snacks, Bakery, Frozen, Cleaning, Other.\n\n"
                + "For each offer return these fields:\n"
                + "- productName: brand + product name (string)\n"
                + "- salePrice: effective per-unit sale price in SEK (number)\n"
                + "- originalPrice: normal full price per unit in SEK (number, 0 if not shown)\n"
                + "- discountPercent: pre-calculated discount percentage (number, 0 if unknown)\n"
                + "- productCategory: one of the categories above (string)\n"
                + "- unit: unit of measure (e.g. kg, st, förp, l)\n"
                + "- offerDescription: verbatim multi-buy deal text, or empty string\n\n"
                + "Return a JSON array only, no markdown fences, no commentary:\n"
                + "[{\"productName\": \"string\", \"salePrice\": number, \"originalPrice\": number, "
                + "\"discountPercent\": number, \"productCategory\": \"string\", \"unit\": \"string\", "
                + "\"offerDescription\": \"string\"}]";
        try {
            String response = callAi(prompt);
            log.info("AI raw response length for '{}': {} chars", storeName,
                    response != null ? response.length() : 0);
            log.debug("AI raw response for '{}': {}", storeName, response);
            List<StoreOffer> offers = parseExtractedOffers(stripMarkdownFences(response), storeName, storeId);
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
                + "Sale prices should be 10–50% lower than original prices.\n\n"
                + "Return a JSON array only, no markdown fences:\n"
                + "[{\"productName\": \"string\", \"salePrice\": number, \"originalPrice\": number, "
                + "\"productCategory\": \"string\", \"unit\": \"string\"}]";
        try {
            String response = callAi(prompt);
            List<StoreOffer> offers = parseExtractedOffers(stripMarkdownFences(response), storeName, storeId);
            log.info("Gemini generated {} fallback offers for store '{}'", offers.size(), storeName);
            return offers;
        } catch (Exception e) {
            log.warn("Failed to generate fallback offers for '{}': {}", storeName, e.getMessage());
            return List.of();
        }
    }

    private List<StoreOffer> parseExtractedOffers(String json, String storeName, String storeId) {
        List<StoreOffer> offers = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            if (!root.isArray()) return offers;
            for (JsonNode node : root) {
                StoreOffer offer = new StoreOffer();
                offer.setId(UUID.randomUUID().toString());
                offer.setStoreId(storeId);
                offer.setStoreName(storeName);
                offer.setProductName(node.path("productName").asText(""));
                offer.setSalePrice(node.path("salePrice").asDouble(0));
                offer.setOriginalPrice(node.path("originalPrice").asDouble(0));
                offer.setProductCategory(node.path("productCategory").asText("Other"));
                offer.setUnit(node.path("unit").asText(""));
                offer.setOfferDescription(node.path("offerDescription").asText(""));
                offer.setFetchedAt(Instant.now());
                offer.setValidFrom(LocalDate.now());
                offer.setValidTo(LocalDate.now().plusDays(7));
                // Prefer AI-provided discountPercent: the AI accounts for deal structure
                // (e.g., for "3 for 2" there's no meaningful originalPrice/salePrice pair),
                // whereas the simple formula below only works for straightforward price cuts.
                double aiDiscount = node.path("discountPercent").asDouble(0);
                if (aiDiscount > 0) {
                    offer.setDiscountPercent(aiDiscount);
                } else if (offer.getOriginalPrice() > 0 && offer.getSalePrice() > 0
                        && offer.getOriginalPrice() > offer.getSalePrice()) {
                    double discount = ((offer.getOriginalPrice() - offer.getSalePrice())
                            / offer.getOriginalPrice()) * 100;
                    offer.setDiscountPercent(discount);
                }
                if (!offer.getProductName().isBlank()) {
                    offers.add(offer);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse extracted offers JSON", e);
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
