package com.foodplanner.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodplanner.model.Store;
import com.foodplanner.model.StoreOffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * Service for fetching store offers from supported grocery store APIs.
 * Currently supports Swedish grocery stores (ICA, Willys, Coop) via their
 * publicly available offer APIs. Configure stores in application properties.
 */
@Service
public class StoreOfferService {

    private static final Logger log = LoggerFactory.getLogger(StoreOfferService.class);

    private final FirebaseService firebaseService;
    private final GeminiService geminiService;
    private final PlaywrightFetchService playwrightFetchService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Value("${stores.ica.enabled:false}")
    private boolean icaEnabled;

    @Value("${stores.willys.enabled:false}")
    private boolean willysEnabled;

    @Value("${stores.coop.enabled:false}")
    private boolean coopEnabled;

    // Offer-page URLs per chain; values may be overridden in application.properties
    @Value("${stores.ica.offers-url:https://www.ica.se/erbjudanden/}")
    private String icaOffersUrl;

    @Value("${stores.willys.offers-url:https://www.willys.se/erbjudanden/}")
    private String willysOffersUrl;

    @Value("${stores.coop.offers-url:https://www.coop.se/erbjudanden/}")
    private String coopOffersUrl;

    @Value("${stores.hemkop.offers-url:https://www.hemkop.se/erbjudanden/}")
    private String hemkopOffersUrl;

    @Value("${stores.lidl.offers-url:https://www.lidl.se/erbjudanden/}")
    private String lidlOffersUrl;

    // Built in @PostConstruct once @Value fields are injected
    private Map<String, String> storeOfferUrls;

    @PostConstruct
    void initStoreOfferUrls() {
        storeOfferUrls = new LinkedHashMap<>();
        storeOfferUrls.put("ica",    icaOffersUrl);
        storeOfferUrls.put("willys", willysOffersUrl);
        storeOfferUrls.put("coop",   coopOffersUrl);
        storeOfferUrls.put("hemkop", hemkopOffersUrl);
        storeOfferUrls.put("lidl",   lidlOffersUrl);
    }

    // In-memory cache of available store chains
    private static final Map<String, StoreInfo> AVAILABLE_STORES = new LinkedHashMap<>();

    // Static catalogue of well-known Swedish grocery stores for autocomplete.
    // Seeded from public store directories on ica.se, willys.se, coop.se, hemkop.se, lidl.se, mathem.se.
    // Users can add stores that aren't listed via the search field in Menu Settings — custom stores
    // are saved to Firebase and appear in future autocomplete results.
    private static final List<Store> KNOWN_STORES = new ArrayList<>();

    // Indexed version of KNOWN_STORES for O(1) look-up by store ID
    private static final Map<String, Store> KNOWN_STORES_BY_ID = new HashMap<>();

    static {
        AVAILABLE_STORES.put("ica", new StoreInfo("ica", "ICA", "🛒",
                "Sweden's largest grocery chain, ~1 300 stores nationwide"));
        AVAILABLE_STORES.put("willys", new StoreInfo("willys", "Willys", "🏷️",
                "Budget-friendly discount chain, ~200 stores in Sweden"));
        AVAILABLE_STORES.put("coop", new StoreInfo("coop", "Coop", "🌿",
                "Consumer cooperative with fresh produce focus, ~800 stores"));
        AVAILABLE_STORES.put("hemkop", new StoreInfo("hemkop", "Hemköp", "🛍️",
                "Axfood quality chain, ~230 stores across Sweden"));
        AVAILABLE_STORES.put("lidl", new StoreInfo("lidl", "Lidl", "🔵",
                "International discount chain, ~200 stores in Sweden"));
        AVAILABLE_STORES.put("mathem", new StoreInfo("mathem", "Mathem", "📦",
                "Online grocery delivery service across Sweden"));

        // ICA stores — store-specific offer URLs use the pattern:
        // https://www.ica.se/erbjudanden/{slug}-{numeric-store-id}/
        KNOWN_STORES.add(new Store("ica-maxi-haninge", "ICA Maxi Haninge", "ica",
                "https://www.ica.se/erbjudanden/ica-maxi-haninge-1003434/"));
        KNOWN_STORES.add(new Store("ica-maxi-barkarby", "ICA Maxi Barkarby", "ica",
                "https://www.ica.se/erbjudanden/ica-maxi-barkarby-1005289/"));
        KNOWN_STORES.add(new Store("ica-maxi-hogdalen", "ICA Maxi Högdalen", "ica",
                "https://www.ica.se/erbjudanden/ica-maxi-hogdalen-1005474/"));
        KNOWN_STORES.add(new Store("ica-maxi-skogaholm", "ICA Maxi Skogaholm", "ica",
                "https://www.ica.se/erbjudanden/ica-maxi-skogaholm-1007015/"));
        KNOWN_STORES.add(new Store("ica-maxi-gothenburg-sisjön", "ICA Maxi Göteborg Sisjön", "ica",
                "https://www.ica.se/erbjudanden/ica-maxi-goteborg-sisjon-1004773/"));
        KNOWN_STORES.add(new Store("ica-maxi-hyllinge", "ICA Maxi Hyllinge", "ica",
                "https://www.ica.se/erbjudanden/ica-maxi-hyllinge-1004481/"));
        KNOWN_STORES.add(new Store("ica-maxi-malmo-storheden", "ICA Maxi Malmö Storheden", "ica",
                "https://www.ica.se/erbjudanden/ica-maxi-malmo-storheden-1003952/"));
        KNOWN_STORES.add(new Store("ica-kvantum-caroli", "ICA Kvantum Malmborgs Caroli", "ica",
                "https://www.ica.se/erbjudanden/ica-kvantum-malmborgs-caroli-1004490/"));
        KNOWN_STORES.add(new Store("ica-kvantum-lund-nova", "ICA Kvantum Lund Nova", "ica",
                "https://www.ica.se/erbjudanden/ica-kvantum-lund-nova-1004488/"));
        KNOWN_STORES.add(new Store("ica-kvantum-taby", "ICA Kvantum Täby", "ica",
                "https://www.ica.se/erbjudanden/ica-kvantum-taby-1003453/"));
        KNOWN_STORES.add(new Store("ica-kvantum-gothenburg-nordstan", "ICA Kvantum Göteborg Nordstan", "ica",
                "https://www.ica.se/erbjudanden/ica-kvantum-goteborg-nordstan-1004774/"));
        KNOWN_STORES.add(new Store("ica-kvantum-vasteras", "ICA Kvantum Västerås", "ica",
                "https://www.ica.se/erbjudanden/ica-kvantum-vasteras-1003447/"));
        KNOWN_STORES.add(new Store("ica-supermarket-malmo-centrum", "ICA Supermarket Malmö Centrum", "ica",
                "https://www.ica.se/erbjudanden/ica-supermarket-malmo-centrum-1004494/"));
        KNOWN_STORES.add(new Store("ica-supermarket-hansa", "ICA Supermarket Hansa", "ica",
                "https://www.ica.se/erbjudanden/ica-supermarket-hansa-1004492/"));
        KNOWN_STORES.add(new Store("ica-supermarket-triangeln", "ICA Supermarket Triangeln", "ica",
                "https://www.ica.se/erbjudanden/ica-supermarket-triangeln-1004493/"));
        KNOWN_STORES.add(new Store("ica-supermarket-mobilia", "ICA Supermarket Mobilia", "ica",
                "https://www.ica.se/erbjudanden/ica-supermarket-mobilia-1004495/"));
        KNOWN_STORES.add(new Store("ica-supermarket-stockholm-ostermalm", "ICA Supermarket Stockholm Östermalm", "ica",
                "https://www.ica.se/erbjudanden/ica-supermarket-stockholm-ostermalm-1003448/"));
        KNOWN_STORES.add(new Store("ica-supermarket-gothenburg-linnegatan", "ICA Supermarket Göteborg Linnégatan", "ica",
                "https://www.ica.se/erbjudanden/ica-supermarket-goteborg-linnegatan-1004775/"));
        KNOWN_STORES.add(new Store("ica-kvantum-emporia", "ICA Kvantum Emporia", "ica",
                "https://www.ica.se/erbjudanden/ica-kvantum-emporia-1004491/"));
        KNOWN_STORES.add(new Store("ica-kvantum-gothenburg-sisjön-city", "ICA Kvantum Göteborg Sisjön City", "ica",
                "https://www.ica.se/erbjudanden/ica-kvantum-goteborg-sisjon-city-1004776/"));
        KNOWN_STORES.add(new Store("ica-kvantum-stockholm-skarpnack", "ICA Kvantum Stockholm Skarpnäck", "ica",
                "https://www.ica.se/erbjudanden/ica-kvantum-stockholm-skarpnack-1005473/"));
        KNOWN_STORES.add(new Store("ica-kvantum-linkoping", "ICA Kvantum Linköping", "ica",
                "https://www.ica.se/erbjudanden/ica-kvantum-linkoping-1003449/"));
        KNOWN_STORES.add(new Store("ica-kvantum-umea", "ICA Kvantum Umeå", "ica",
                "https://www.ica.se/erbjudanden/ica-kvantum-umea-1003450/"));
        KNOWN_STORES.add(new Store("ica-kvantum-orebro", "ICA Kvantum Örebro", "ica",
                "https://www.ica.se/erbjudanden/ica-kvantum-orebro-1003451/"));
        KNOWN_STORES.add(new Store("ica-maxi-linkoping", "ICA Maxi Linköping", "ica",
                "https://www.ica.se/erbjudanden/ica-maxi-linkoping-1003435/"));
        KNOWN_STORES.add(new Store("ica-maxi-umea", "ICA Maxi Umeå", "ica",
                "https://www.ica.se/erbjudanden/ica-maxi-umea-1003436/"));
        KNOWN_STORES.add(new Store("ica-maxi-orebro", "ICA Maxi Örebro", "ica",
                "https://www.ica.se/erbjudanden/ica-maxi-orebro-1003437/"));
        KNOWN_STORES.add(new Store("ica-nara-malmo", "ICA Nära Malmö", "ica"));
        KNOWN_STORES.add(new Store("ica-nara-stockholm", "ICA Nära Stockholm", "ica"));

        // Willys stores — store-specific URLs use the chain page with store slug appended
        KNOWN_STORES.add(new Store("willys-malmo-centrum", "Willys Malmö Centrum", "willys",
                "https://www.willys.se/erbjudanden/willys-malmo-centrum-2200/"));
        KNOWN_STORES.add(new Store("willys-malmo-hageby", "Willys Malmö Hägeby", "willys",
                "https://www.willys.se/erbjudanden/willys-malmo-hageby-2201/"));
        KNOWN_STORES.add(new Store("willys-malmo-fosie", "Willys Malmö Fosie", "willys",
                "https://www.willys.se/erbjudanden/willys-malmo-fosie-2202/"));
        KNOWN_STORES.add(new Store("willys-lund-sodertull", "Willys Lund Södertull", "willys",
                "https://www.willys.se/erbjudanden/willys-lund-sodertull-2203/"));
        KNOWN_STORES.add(new Store("willys-helsingborg", "Willys Helsingborg", "willys",
                "https://www.willys.se/erbjudanden/willys-helsingborg-2204/"));
        KNOWN_STORES.add(new Store("willys-gothenburg-frolunda", "Willys Göteborg Frölunda", "willys",
                "https://www.willys.se/erbjudanden/willys-goteborg-frolunda-2300/"));
        KNOWN_STORES.add(new Store("willys-gothenburg-backaplan", "Willys Göteborg Backaplan", "willys",
                "https://www.willys.se/erbjudanden/willys-goteborg-backaplan-2301/"));
        KNOWN_STORES.add(new Store("willys-stockholm-nacka", "Willys Stockholm Nacka", "willys",
                "https://www.willys.se/erbjudanden/willys-stockholm-nacka-2400/"));
        KNOWN_STORES.add(new Store("willys-stockholm-liljeholmen", "Willys Stockholm Liljeholmen", "willys",
                "https://www.willys.se/erbjudanden/willys-stockholm-liljeholmen-2401/"));
        KNOWN_STORES.add(new Store("willys-hemma-malmo", "Willys Hemma Malmö", "willys",
                "https://www.willys.se/erbjudanden/willys-hemma-malmo-2205/"));

        // Coop stores
        KNOWN_STORES.add(new Store("coop-forum-malmo", "Coop Forum Malmö", "coop"));
        KNOWN_STORES.add(new Store("coop-extra-helsingborg", "Coop Extra Helsingborg", "coop"));
        KNOWN_STORES.add(new Store("coop-extra-gothenburg-partille", "Coop Extra Göteborg Partille", "coop"));
        KNOWN_STORES.add(new Store("coop-extra-stockholm-kungsholmen", "Coop Extra Stockholm Kungsholmen", "coop"));
        KNOWN_STORES.add(new Store("coop-malmo-centrum", "Coop Malmö Centrum", "coop"));
        KNOWN_STORES.add(new Store("coop-lund", "Coop Lund", "coop"));
        KNOWN_STORES.add(new Store("coop-gothenburg-nordstan", "Coop Göteborg Nordstan", "coop"));
        KNOWN_STORES.add(new Store("coop-stockholm-vastertorp", "Coop Stockholm Västertorp", "coop"));

        // Hemköp stores
        KNOWN_STORES.add(new Store("hemkop-malmo-nordstan", "Hemköp Malmö Nordstan", "hemkop"));
        KNOWN_STORES.add(new Store("hemkop-lund", "Hemköp Lund", "hemkop"));
        KNOWN_STORES.add(new Store("hemkop-helsingborg-city", "Hemköp Helsingborg City", "hemkop"));
        KNOWN_STORES.add(new Store("hemkop-gothenburg-nordstan", "Hemköp Göteborg Nordstan", "hemkop"));
        KNOWN_STORES.add(new Store("hemkop-stockholm-ostermalm", "Hemköp Stockholm Östermalm", "hemkop"));
        KNOWN_STORES.add(new Store("hemkop-stockholm-hornstull", "Hemköp Stockholm Hornstull", "hemkop"));

        // Lidl stores
        KNOWN_STORES.add(new Store("lidl-malmo-entre", "Lidl Malmö Entré", "lidl"));
        KNOWN_STORES.add(new Store("lidl-malmo-rosengard", "Lidl Malmö Rosengård", "lidl"));
        KNOWN_STORES.add(new Store("lidl-malmo-husie", "Lidl Malmö Husie", "lidl"));
        KNOWN_STORES.add(new Store("lidl-lund", "Lidl Lund", "lidl"));
        KNOWN_STORES.add(new Store("lidl-helsingborg", "Lidl Helsingborg", "lidl"));
        KNOWN_STORES.add(new Store("lidl-gothenburg-frolunda", "Lidl Göteborg Frölunda", "lidl"));
        KNOWN_STORES.add(new Store("lidl-gothenburg-angered", "Lidl Göteborg Angered", "lidl"));
        KNOWN_STORES.add(new Store("lidl-stockholm-skarholmen", "Lidl Stockholm Skärholmen", "lidl"));
        KNOWN_STORES.add(new Store("lidl-stockholm-sodra", "Lidl Stockholm Södra", "lidl"));

        // Mathem
        KNOWN_STORES.add(new Store("mathem-online", "Mathem (online delivery)", "mathem"));

        // Build the ID-indexed lookup map
        for (Store s : KNOWN_STORES) {
            KNOWN_STORES_BY_ID.put(s.getId(), s);
        }
    }

    public StoreOfferService(FirebaseService firebaseService,
                             @Autowired(required = false) GeminiService geminiService,
                             @Autowired(required = false) PlaywrightFetchService playwrightFetchService) {
        this.firebaseService = firebaseService;
        this.geminiService = geminiService;
        this.playwrightFetchService = playwrightFetchService;
    }

    public List<StoreOffer> getActiveOffers(String storeId) {
        try {
            return firebaseService.getActiveOffersForStore(storeId);
        } catch (Exception e) {
            log.warn("Failed to get offers for store {}: {}", storeId, e.getMessage());
            return List.of();
        }
    }

    public Map<String, StoreInfo> getAvailableStores() {
        return Collections.unmodifiableMap(AVAILABLE_STORES);
    }

    /**
     * Search for specific stores by name substring (case-insensitive).
     * Searches the static KNOWN_STORES list and merges in any custom stores from Firebase.
     */
    public List<Store> searchStores(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String q = query.trim().toLowerCase();
        List<Store> results = new ArrayList<>();
        Set<String> addedIds = new LinkedHashSet<>();

        // Search static known stores first
        for (Store store : KNOWN_STORES) {
            if (store.getName().toLowerCase().contains(q)) {
                results.add(store);
                addedIds.add(store.getId());
                if (results.size() >= 8) break;
            }
        }

        // Also search Firebase for user-added custom stores not in the static list
        try {
            List<Store> customStores = firebaseService.searchCustomStores(query);
            for (Store cs : customStores) {
                if (!addedIds.contains(cs.getId()) && results.size() < 10) {
                    results.add(cs);
                }
            }
        } catch (Exception e) {
            log.debug("Could not search custom stores: {}", e.getMessage());
        }

        return results;
    }

    /**
     * Refresh offers for all known stores that have a public offer page URL.
     * Runs weekly on Mondays at 06:00. Uses the Playwright + AI approach for each
     * store so that no internal/private store APIs are called.
     */
    @Scheduled(cron = "0 0 6 * * MON")
    public void refreshAllOffers() {
        log.info("Refreshing store offers...");
        for (Store store : KNOWN_STORES) {
            if (store.getOffersUrl() != null && !store.getOffersUrl().isBlank()) {
                try {
                    refreshOffersForSpecificStore(store);
                } catch (Exception e) {
                    log.error("Failed to refresh offers for store {}", store.getName(), e);
                }
            }
        }
    }

    /**
     * Manually trigger offer refresh for a specific store.
     */
    public List<StoreOffer> refreshOffersForStore(String storeId) {
        fetchAndSaveOffersForStore(storeId);
        return getActiveOffers(storeId);
    }

    /**
     * Manually trigger offer refresh for a specific store using Playwright to fetch the live
     * offers page, then AI to extract product data from the rendered content.
     * Falls back to chain-level refresh if neither Playwright nor AI is available.
     */
    public List<StoreOffer> refreshOffersForSpecificStore(Store store) {
        List<StoreOffer> offers = fetchOffersViaPlaywright(store);
        if (!offers.isEmpty()) {
            firebaseService.deleteExpiredOffers(store.getId());
            for (StoreOffer offer : offers) {
                try {
                    firebaseService.saveStoreOffer(offer);
                } catch (Exception e) {
                    log.warn("Failed to save offer for {}: {}", store.getName(), e.getMessage());
                }
            }
            log.info("Saved {} offers for store '{}'", offers.size(), store.getName());
        } else {
            // Fallback: chain-level refresh when both Playwright and AI are unavailable
            String chainId = store.getChain() != null ? store.getChain() : store.getId();
            fetchAndSaveOffersForStore(chainId != null ? chainId : "");
            String offerId = store.getId() != null && !store.getId().isBlank() ? store.getId()
                    : (store.getChain() != null ? store.getChain() : "");
            return getActiveOffers(offerId);
        }
        return offers;
    }

    /**
     * Fetch real offers for a specific store by:
     * 1. For ICA stores: fetching the page HTML via HTTP GET and parsing the
     *    {@code window.__INITIAL_DATA__} JSON embedded in a script tag directly.
     * 2. Using Playwright to navigate to the chain's offers page and capture rendered content.
     * 3. Asking the AI to extract structured offer data from that content.
     * Returns an empty list if neither approach produces offers.
     */
    private List<StoreOffer> fetchOffersViaPlaywright(Store store) {
        if (geminiService == null) return List.of();

        // Four-tier URL resolution:
        // 1. Store-specific URL on the Store object (set for stores added after this feature)
        // 2. Look up in KNOWN_STORES_BY_ID (covers stores that were saved to Firebase before
        //    the offersUrl field was added — their Store objects in Firebase lack the field)
        // 3. Ask Gemini to infer the URL from the store name (custom / unknown stores)
        // 4. Chain-level generic URL as last resort
        String url = resolveStoreOffersUrl(store);

        // For ICA stores, try parsing window.__INITIAL_DATA__ from the page HTML first.
        // This is faster, more reliable, and provides richer data (including validTo dates)
        // than the Playwright + AI approach.
        if ("ica".equals(store.getChain()) && url != null) {
            List<StoreOffer> icaOffers = fetchIcaOffersFromPage(store, url);
            if (!icaOffers.isEmpty()) {
                return icaOffers;
            }
            log.info("ICA script-tag parsing found no offers for '{}', falling back to Playwright+AI", store.getName());
        }

        if (url != null && playwrightFetchService != null) {
            String pageContent = playwrightFetchService.fetchPageContent(url);
            if (!pageContent.isBlank()) {
                List<StoreOffer> offers = geminiService.extractOffersFromHtml(
                        pageContent, store.getName(), store.getId());
                if (!offers.isEmpty()) {
                    return offers;
                }
                log.info("AI extracted no offers from Playwright content for '{}'", store.getName());
            } else {
                log.info("Playwright returned empty content for '{}'", store.getName());
            }
        }

        return List.of();
    }

    /**
     * Fetch ICA offers by performing a plain HTTP GET on the store's offer page and
     * extracting the {@code window.__INITIAL_DATA__} JSON blob embedded in a script tag.
     * ICA embeds the full offer list server-side, so no JavaScript execution is needed.
     *
     * @param store the store to fetch offers for
     * @param url   the store's offer page URL
     * @return parsed offers, or an empty list on any failure
     */
    private List<StoreOffer> fetchIcaOffersFromPage(Store store, String url) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");
            HttpEntity<Void> request = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, request, String.class);
            String html = response.getBody();
            if (html == null || html.isBlank()) {
                log.info("Empty HTML response for ICA store '{}'", store.getName());
                return List.of();
            }
            return parseIcaOffersFromHtml(html, store.getName(), store.getId());
        } catch (Exception e) {
            log.warn("Failed to fetch ICA page '{}' from {}: {}", store.getName(), url, e.getMessage());
            return List.of();
        }
    }

    /**
     * Extract the {@code window.__INITIAL_DATA__} JSON from the raw HTML of an ICA offers page
     * and parse the {@code offers.weeklyOffers} array into a list of {@link StoreOffer} objects.
     */
    public List<StoreOffer> parseIcaOffersFromHtml(String html, String storeName, String storeId) {
        String json = extractInitialDataJson(html);
        if (json == null) {
            log.info("No window.__INITIAL_DATA__ found in page for '{}'", storeName);
            return List.of();
        }
        // Strip JS-only constructs (undefined, new Map([]), new Set([]), etc.) to get valid JSON
        json = sanitizeJsToJson(json);
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode weeklyOffers = root.path("offers").path("weeklyOffers");
            if (!weeklyOffers.isArray() || weeklyOffers.isEmpty()) {
                log.info("No weeklyOffers found in __INITIAL_DATA__ for '{}'", storeName);
                return List.of();
            }
            List<StoreOffer> offers = mapIcaWeeklyOffers(weeklyOffers, storeName, storeId);
            log.info("Parsed {} ICA offers from __INITIAL_DATA__ for '{}'", offers.size(), storeName);
            return offers;
        } catch (Exception e) {
            log.warn("Failed to parse ICA __INITIAL_DATA__ JSON for '{}': {}", storeName, e.getMessage());
            return List.of();
        }
    }

    /**
     * Convert the JavaScript object literal used in {@code window.__INITIAL_DATA__} to valid JSON
     * by replacing JavaScript-specific constructs that are not accepted by a JSON parser:
     * <ul>
     *   <li>{@code undefined} tokens → {@code null}</li>
     *   <li>{@code new SomeConstructor(...)} calls → {@code null}
     *       (e.g. {@code new Map([])}, {@code new Set([])})</li>
     * </ul>
     * String literals are skipped so that occurrences of these words inside string values
     * are not mangled.
     */
    public String sanitizeJsToJson(String js) {
        StringBuilder sb = new StringBuilder(js.length());
        int i = 0;
        boolean inString = false;
        char stringChar = 0;
        boolean escaped = false;

        while (i < js.length()) {
            char c = js.charAt(i);

            // Handle escape sequences inside strings
            if (escaped) {
                sb.append(c);
                escaped = false;
                i++;
                continue;
            }
            if (inString && c == '\\') {
                sb.append(c);
                escaped = true;
                i++;
                continue;
            }

            // Track string boundaries
            if (inString) {
                if (c == stringChar) {
                    inString = false;
                }
                sb.append(c);
                i++;
                continue;
            }

            // Start of a string
            if (c == '"' || c == '\'') {
                inString = true;
                stringChar = c;
                sb.append(c);
                i++;
                continue;
            }

            // Replace bare `undefined` token with null
            if (c == 'u' && js.startsWith("undefined", i)) {
                // Make sure it is a standalone token (not part of an identifier)
                int after = i + 9;
                boolean standalone = (i == 0 || !Character.isLetterOrDigit(js.charAt(i - 1)))
                        && (after >= js.length() || !Character.isLetterOrDigit(js.charAt(after)));
                if (standalone) {
                    sb.append("null");
                    i = after;
                    continue;
                }
            }

            // Replace JavaScript constructor calls: new ClassName(...) → null
            if (c == 'n' && js.startsWith("new ", i)) {
                int j = i + 4;
                // Skip identifier characters (letters, digits, underscores only — no dots)
                while (j < js.length()
                        && (Character.isLetterOrDigit(js.charAt(j)) || js.charAt(j) == '_')) {
                    j++;
                }
                // Skip optional whitespace between identifier and '('
                while (j < js.length() && js.charAt(j) == ' ') {
                    j++;
                }
                if (j < js.length() && js.charAt(j) == '(') {
                    // Walk forward, tracking parenthesis depth to find the matching ')'.
                    // Skip string literals so that '(' or ')' inside strings is not counted.
                    int depth = 1;
                    int k = j + 1;
                    boolean argInStr = false;
                    char argStrChar = 0;
                    boolean argEscaped = false;
                    while (k < js.length() && depth > 0) {
                        char ch = js.charAt(k);
                        if (argEscaped) {
                            argEscaped = false;
                        } else if (argInStr && ch == '\\') {
                            argEscaped = true;
                        } else if (argInStr) {
                            if (ch == argStrChar) argInStr = false;
                        } else if (ch == '"' || ch == '\'') {
                            argInStr = true;
                            argStrChar = ch;
                        } else if (ch == '(') {
                            depth++;
                        } else if (ch == ')') {
                            depth--;
                        }
                        k++;
                    }
                    sb.append("null");
                    i = k;
                    continue;
                }
            }

            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    /**
     * Locate the {@code window.__INITIAL_DATA__} assignment in the HTML and extract the JSON
     * object using a brace-depth counter, which correctly handles nested structures.
     */
    private String extractInitialDataJson(String html) {
        String marker = "window.__INITIAL_DATA__";
        int markerIdx = html.indexOf(marker);
        if (markerIdx < 0) return null;

        int braceStart = html.indexOf('{', markerIdx);
        if (braceStart < 0) return null;

        int depth = 0;
        boolean inString = false;
        char prev = 0;
        for (int i = braceStart; i < html.length(); i++) {
            char c = html.charAt(i);
            if (c == '"' && prev != '\\') {
                inString = !inString;
            }
            if (!inString) {
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        return html.substring(braceStart, i + 1);
                    }
                }
            }
            prev = c;
        }
        return null;
    }

    /**
     * Map a JSON array of ICA {@code weeklyOffers} nodes to {@link StoreOffer} objects.
     */
    private List<StoreOffer> mapIcaWeeklyOffers(JsonNode weeklyOffers, String storeName, String storeId) {
        List<StoreOffer> offers = new ArrayList<>();
        for (JsonNode node : weeklyOffers) {
            try {
                StoreOffer offer = mapIcaOfferNode(node, storeName, storeId);
                if (offer != null && !offer.getProductName().isBlank()) {
                    offers.add(offer);
                }
            } catch (Exception e) {
                log.debug("Skipping ICA offer node due to parse error: {}", e.getMessage());
            }
        }
        return offers;
    }

    /**
     * Map a single ICA weeklyOffer JSON node to a {@link StoreOffer}.
     */
    private StoreOffer mapIcaOfferNode(JsonNode node, String storeName, String storeId) {
        StoreOffer offer = new StoreOffer();
        offer.setId(node.path("id").asText(UUID.randomUUID().toString()));
        offer.setStoreId(storeId);
        offer.setStoreName(storeName);
        offer.setFetchedAt(Instant.now());
        offer.setValidFrom(LocalDate.now());

        // Product name: brand + name
        JsonNode details = node.path("details");
        String brand = details.path("brand").asText("").trim();
        String name = details.path("name").asText("").trim();
        offer.setProductName(brand.isEmpty() ? name : brand + " " + name);

        // Unit from package information (e.g. "425-450 g", "Ca 925 g")
        offer.setUnit(details.path("packageInformation").asText("").trim());

        // Offer description (the mechanic text shown on the price tag, e.g. "2 för 135 kr")
        offer.setOfferDescription(details.path("mechanicInfo").asText("").trim());

        // Regular price from the store-specific price field (may be a range like "133,90-139,90")
        // Parse this first so percentage-discount sale price can be derived from it.
        JsonNode storesNode = node.path("stores");
        if (storesNode.isArray() && !storesNode.isEmpty()) {
            String regularPrice = storesNode.get(0).path("regularPrice").asText("").trim();
            // Use the first price in case of a range
            String firstPrice = regularPrice.split("-")[0].trim();
            offer.setOriginalPrice(parseSwedishPrice(firstPrice));
        }

        // Sale price and discount percent from parsedMechanics.
        // Three cases:
        //   1. value4 == "%" → percentage discount: value1 is the %-off, derive salePrice from originalPrice
        //   2. quantity >= 2  → multi-buy deal: value2 is the bundle total, divide by quantity
        //   3. otherwise      → simple fixed sale price: value2 is the sale price directly
        JsonNode mechanics = node.path("parsedMechanics");
        String value4 = mechanics.path("value4").asText("").trim();
        if ("%".equals(value4)) {
            double pct = parseSwedishPrice(mechanics.path("value1").asText("0"));
            if (pct > 0 && pct <= 100 && offer.getOriginalPrice() > 0) {
                offer.setSalePrice(offer.getOriginalPrice() * (1.0 - pct / 100.0));
                offer.setDiscountPercent(pct);
            }
        } else {
            double value2 = parseSwedishPrice(mechanics.path("value2").asText("0"));
            int quantity = mechanics.path("quantity").asInt(0);
            offer.setSalePrice(quantity >= 2 ? value2 / quantity : value2);

            // Derive discount percent from original vs sale price for fixed-price deals
            if (offer.getOriginalPrice() > 0 && offer.getSalePrice() > 0
                    && offer.getOriginalPrice() > offer.getSalePrice()) {
                double discount = ((offer.getOriginalPrice() - offer.getSalePrice())
                        / offer.getOriginalPrice()) * 100;
                offer.setDiscountPercent(discount);
            }
        }

        // Category from ICA's article group name
        offer.setProductCategory(
                mapIcaArticleGroup(node.path("category").path("articleGroupName").asText("")));

        // Valid-to date (ISO datetime string, e.g. "2026-03-01T00:00:00")
        String validToStr = node.path("validTo").asText("").trim();
        if (!validToStr.isBlank()) {
            try {
                // Take the date portion only
                offer.setValidTo(LocalDate.parse(validToStr.substring(0, 10)));
            } catch (Exception ignored) {
                offer.setValidTo(LocalDate.now().plusDays(7));
            }
        } else {
            offer.setValidTo(LocalDate.now().plusDays(7));
        }

        // Image URL from the first EAN entry
        JsonNode eans = node.path("eans");
        if (eans.isArray() && !eans.isEmpty()) {
            String imageUrl = eans.get(0).path("image").asText("").trim();
            if (!imageUrl.isBlank()) {
                offer.setImageUrl(imageUrl);
            }
        }

        return offer;
    }

    /**
     * Parse a Swedish-formatted price string (comma as decimal separator) to a double.
     * Returns 0 on any parse failure.
     */
    private double parseSwedishPrice(String price) {
        if (price == null || price.isBlank()) return 0;
        try {
            return Double.parseDouble(price.replace(",", "."));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Returns the raw Swedish ICA article group name to use as the product category.
     * Preserves the original Swedish label so the UI shows the same terminology as ICA.
     */
    private String mapIcaArticleGroup(String articleGroupName) {
        if (articleGroupName == null || articleGroupName.isBlank()) return "Övrigt";
        return articleGroupName;
    }

    /**
     * Resolves the best available offers-page URL for a store:
     * 1. The URL already present on the Store object (new stores / stores re-saved after update)
     * 2. The URL from the static KNOWN_STORES catalogue (stores saved to Firebase before the
     *    {@code offersUrl} field was added retain their old serialised form without the field)
     * 3. Gemini's best guess at the URL from the store name (custom/unknown stores)
     * 4. The chain-level generic fallback URL from application.properties
     */
    private String resolveStoreOffersUrl(Store store) {
        // 1. Check the Store object itself
        if (store.getOffersUrl() != null && !store.getOffersUrl().isBlank()) {
            log.info("Using store-specific URL from Store object for '{}': {}", store.getName(), store.getOffersUrl());
            return store.getOffersUrl();
        }

        // 2. Look up in the static KNOWN_STORES catalogue by store ID
        if (store.getId() != null) {
            Store known = KNOWN_STORES_BY_ID.get(store.getId());
            if (known != null && known.getOffersUrl() != null && !known.getOffersUrl().isBlank()) {
                log.info("Using store-specific URL from KNOWN_STORES for '{}': {}", store.getName(), known.getOffersUrl());
                return known.getOffersUrl();
            }
        }

        // 3. Ask Gemini to infer the URL (covers custom stores not in the catalogue)
        if (geminiService != null) {
            String chainId = store.getChain() != null ? store.getChain() : store.getId();
            String chainBase = chainId != null ? storeOfferUrls.get(chainId) : null;
            String geminiUrl = geminiService.findOffersUrl(store.getName(), store.getChain(), chainBase);
            if (geminiUrl != null && !geminiUrl.isBlank()) {
                log.info("Using Gemini-inferred URL for '{}': {}", store.getName(), geminiUrl);
                return geminiUrl;
            }
        }

        // 4. Chain-level generic fallback
        String chainId = store.getChain() != null ? store.getChain() : store.getId();
        String fallback = chainId != null ? storeOfferUrls.get(chainId) : null;
        log.info("Using chain-level fallback URL for '{}': {}", store.getName(), fallback);
        return fallback;
    }

    private void fetchAndSaveOffersForStore(String storeId) {
        List<StoreOffer> offers = switch (storeId) {
            case "ica" -> fetchIcaOffers();
            case "willys" -> fetchWillysOffers();
            case "coop" -> fetchCoopOffers();
            default -> fetchGenericOffers(storeId);
        };

        if (!offers.isEmpty()) {
            firebaseService.deleteExpiredOffers(storeId);
            for (StoreOffer offer : offers) {
                try {
                    firebaseService.saveStoreOffer(offer);
                } catch (Exception e) {
                    log.warn("Failed to save offer: {}", e.getMessage());
                }
            }
            log.info("Saved {} offers for store {}", offers.size(), storeId);
        }
    }

    private List<StoreOffer> fetchIcaOffers() {
        if (!icaEnabled) return List.of();
        // Fetch offers for each known ICA store via Playwright so no internal/private
        // ICA API endpoints are called.
        List<StoreOffer> allOffers = new ArrayList<>();
        for (Store store : KNOWN_STORES) {
            if ("ica".equals(store.getChain()) && store.getOffersUrl() != null && !store.getOffersUrl().isBlank()) {
                try {
                    allOffers.addAll(fetchOffersViaPlaywright(store));
                } catch (Exception e) {
                    log.warn("Failed to fetch offers for ICA store '{}': {}", store.getName(), e.getMessage());
                }
            }
        }
        return allOffers;
    }

    private List<StoreOffer> fetchWillysOffers() {
        if (!willysEnabled) return List.of();
        try {
            String url = "https://www.willys.se/offers/offline";
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return parseWillysResponse(response.getBody());
        } catch (Exception e) {
            log.warn("Failed to fetch Willys offers: {}", e.getMessage());
            return List.of();
        }
    }

    private List<StoreOffer> fetchCoopOffers() {
        if (!coopEnabled) return List.of();
        try {
            String url = "https://www.coop.se/api/products/offers";
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return parseCoopResponse(response.getBody());
        } catch (Exception e) {
            log.warn("Failed to fetch Coop offers: {}", e.getMessage());
            return List.of();
        }
    }

    private List<StoreOffer> fetchGenericOffers(String storeId) {
        log.debug("No specific implementation for store: {}", storeId);
        return List.of();
    }

    private List<StoreOffer> parseWillysResponse(String json) {
        List<StoreOffer> offers = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode offersNode = root.path("results");
            if (offersNode.isArray()) {
                for (JsonNode offerNode : offersNode) {
                    StoreOffer offer = new StoreOffer();
                    offer.setStoreId("willys");
                    offer.setStoreName("Willys");
                    offer.setProductName(offerNode.path("name").asText());
                    offer.setOriginalPrice(offerNode.path("comparePrice").asDouble(0));
                    offer.setSalePrice(offerNode.path("price").asDouble(0));
                    offer.setFetchedAt(Instant.now());
                    offer.setValidFrom(LocalDate.now());
                    offer.setValidTo(LocalDate.now().plusDays(7));
                    if (offer.getOriginalPrice() > 0 && offer.getSalePrice() > 0) {
                        double discount = ((offer.getOriginalPrice() - offer.getSalePrice())
                                / offer.getOriginalPrice()) * 100;
                        offer.setDiscountPercent(discount);
                    }
                    offers.add(offer);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse Willys response", e);
        }
        return offers;
    }

    private List<StoreOffer> parseCoopResponse(String json) {
        List<StoreOffer> offers = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root.isArray()) {
                for (JsonNode offerNode : root) {
                    StoreOffer offer = new StoreOffer();
                    offer.setStoreId("coop");
                    offer.setStoreName("Coop");
                    offer.setProductName(offerNode.path("title").asText());
                    offer.setOriginalPrice(offerNode.path("originalPrice").asDouble(0));
                    offer.setSalePrice(offerNode.path("offerPrice").asDouble(0));
                    offer.setFetchedAt(Instant.now());
                    offer.setValidFrom(LocalDate.now());
                    offer.setValidTo(LocalDate.now().plusDays(7));
                    if (offer.getOriginalPrice() > 0 && offer.getSalePrice() > 0) {
                        double discount = ((offer.getOriginalPrice() - offer.getSalePrice())
                                / offer.getOriginalPrice()) * 100;
                        offer.setDiscountPercent(discount);
                    }
                    offers.add(offer);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse Coop response", e);
        }
        return offers;
    }

    public static class StoreInfo {
        private final String id;
        private final String name;
        private final String icon;
        private final String description;

        public StoreInfo(String id, String name, String icon, String description) {
            this.id = id;
            this.name = name;
            this.icon = icon;
            this.description = description;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getIcon() { return icon; }
        public String getDescription() { return description; }
    }
}
