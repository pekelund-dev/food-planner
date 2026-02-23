package com.foodplanner.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodplanner.model.Store;
import com.foodplanner.model.StoreOffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

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
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Value("${stores.ica.enabled:false}")
    private boolean icaEnabled;

    @Value("${stores.willys.enabled:false}")
    private boolean willysEnabled;

    @Value("${stores.coop.enabled:false}")
    private boolean coopEnabled;

    // In-memory cache of available store chains
    private static final Map<String, StoreInfo> AVAILABLE_STORES = new LinkedHashMap<>();

    // Static catalogue of well-known Swedish grocery stores for autocomplete.
    // Seeded from public store directories on ica.se, willys.se, coop.se, hemkop.se, lidl.se, mathem.se.
    // Users can add stores that aren't listed via the search field in Menu Settings — custom stores
    // are saved to Firebase and appear in future autocomplete results.
    private static final List<Store> KNOWN_STORES = new ArrayList<>();

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

        // ICA stores
        KNOWN_STORES.add(new Store("ica-maxi-haninge", "ICA Maxi Haninge", "ica"));
        KNOWN_STORES.add(new Store("ica-maxi-barkarby", "ICA Maxi Barkarby", "ica"));
        KNOWN_STORES.add(new Store("ica-maxi-hogdalen", "ICA Maxi Högdalen", "ica"));
        KNOWN_STORES.add(new Store("ica-maxi-skogaholm", "ICA Maxi Skogaholm", "ica"));
        KNOWN_STORES.add(new Store("ica-maxi-gothenburg-sisjön", "ICA Maxi Göteborg Sisjön", "ica"));
        KNOWN_STORES.add(new Store("ica-maxi-hyllinge", "ICA Maxi Hyllinge", "ica"));
        KNOWN_STORES.add(new Store("ica-maxi-malmo-storheden", "ICA Maxi Malmö Storheden", "ica"));
        KNOWN_STORES.add(new Store("ica-kvantum-caroli", "ICA Kvantum Malmborgs Caroli", "ica"));
        KNOWN_STORES.add(new Store("ica-kvantum-lund-nova", "ICA Kvantum Lund Nova", "ica"));
        KNOWN_STORES.add(new Store("ica-kvantum-taby", "ICA Kvantum Täby", "ica"));
        KNOWN_STORES.add(new Store("ica-kvantum-gothenburg-nordstan", "ICA Kvantum Göteborg Nordstan", "ica"));
        KNOWN_STORES.add(new Store("ica-kvantum-vasteras", "ICA Kvantum Västerås", "ica"));
        KNOWN_STORES.add(new Store("ica-supermarket-malmo-centrum", "ICA Supermarket Malmö Centrum", "ica"));
        KNOWN_STORES.add(new Store("ica-supermarket-stockholm-ostermalm", "ICA Supermarket Stockholm Östermalm", "ica"));
        KNOWN_STORES.add(new Store("ica-supermarket-gothenburg-linnegatan", "ICA Supermarket Göteborg Linnégatan", "ica"));
        KNOWN_STORES.add(new Store("ica-nara-malmo", "ICA Nära Malmö", "ica"));
        KNOWN_STORES.add(new Store("ica-nara-stockholm", "ICA Nära Stockholm", "ica"));

        // Willys stores
        KNOWN_STORES.add(new Store("willys-malmo-centrum", "Willys Malmö Centrum", "willys"));
        KNOWN_STORES.add(new Store("willys-malmo-hageby", "Willys Malmö Hägeby", "willys"));
        KNOWN_STORES.add(new Store("willys-malmo-fosie", "Willys Malmö Fosie", "willys"));
        KNOWN_STORES.add(new Store("willys-lund-sodertull", "Willys Lund Södertull", "willys"));
        KNOWN_STORES.add(new Store("willys-helsingborg", "Willys Helsingborg", "willys"));
        KNOWN_STORES.add(new Store("willys-gothenburg-frolunda", "Willys Göteborg Frölunda", "willys"));
        KNOWN_STORES.add(new Store("willys-gothenburg-backaplan", "Willys Göteborg Backaplan", "willys"));
        KNOWN_STORES.add(new Store("willys-stockholm-nacka", "Willys Stockholm Nacka", "willys"));
        KNOWN_STORES.add(new Store("willys-stockholm-liljeholmen", "Willys Stockholm Liljeholmen", "willys"));
        KNOWN_STORES.add(new Store("willys-hemma-malmo", "Willys Hemma Malmö", "willys"));

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
    }

    public StoreOfferService(FirebaseService firebaseService,
                             @Autowired(required = false) GeminiService geminiService) {
        this.firebaseService = firebaseService;
        this.geminiService = geminiService;
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
     * Refresh offers for all enabled stores. Runs weekly on Mondays at 06:00.
     */
    @Scheduled(cron = "0 0 6 * * MON")
    public void refreshAllOffers() {
        log.info("Refreshing store offers...");
        for (String storeId : AVAILABLE_STORES.keySet()) {
            try {
                fetchAndSaveOffersForStore(storeId);
            } catch (Exception e) {
                log.error("Failed to refresh offers for store {}", storeId, e);
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
     * Manually trigger offer refresh for a specific store using Gemini to find and parse offers.
     * Falls back to chain-level refresh if Gemini is not configured.
     */
    public List<StoreOffer> refreshOffersForSpecificStore(Store store) {
        List<StoreOffer> offers = fetchOffersViaGemini(store);
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
            // Fallback: chain-level refresh when Gemini is unavailable
            String chainId = store.getChain() != null ? store.getChain() : store.getId();
            fetchAndSaveOffersForStore(chainId != null ? chainId : "");
            String offerId = store.getId() != null && !store.getId().isBlank() ? store.getId()
                    : (store.getChain() != null ? store.getChain() : "");
            return getActiveOffers(offerId);
        }
        return offers;
    }

    /**
     * Use Gemini to (1) find the store's erbjudanden URL, (2) fetch the HTML, (3) extract offers.
     */
    private List<StoreOffer> fetchOffersViaGemini(Store store) {
        if (geminiService == null) return List.of();

        // Step 1: Ask Gemini for the offers page URL
        String offersUrl = geminiService.findStoreOffersUrl(store.getName());
        if (offersUrl == null) {
            log.warn("Gemini could not determine offers URL for store '{}'", store.getName());
            return List.of();
        }

        // Step 2: Fetch the page HTML
        String html;
        try {
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (compatible; FoodPlannerBot/1.0)");
            headers.set("Accept", "text/html,application/xhtml+xml");
            var entity = new org.springframework.http.HttpEntity<>(headers);
            var response = restTemplate.exchange(offersUrl, org.springframework.http.HttpMethod.GET, entity, String.class);
            html = response.getBody();
            log.info("Fetched offers page for '{}' ({} chars)", store.getName(), html == null ? 0 : html.length());
        } catch (Exception e) {
            log.warn("Failed to fetch offers page '{}' for store '{}': {}", offersUrl, store.getName(), e.getMessage());
            return List.of();
        }

        // Step 3: Ask Gemini to extract offers from the HTML
        return geminiService.extractOffersFromHtml(html, store.getName(), store.getId());
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
        try {
            // ICA has a public API for store offers
            String url = "https://www.ica.se/api/offers/v2/store-offers";
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return parseIcaResponse(response.getBody());
        } catch (Exception e) {
            log.warn("Failed to fetch ICA offers: {}", e.getMessage());
            return List.of();
        }
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

    private List<StoreOffer> parseIcaResponse(String json) {
        List<StoreOffer> offers = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode offersNode = root.path("offers");
            if (offersNode.isArray()) {
                for (JsonNode offerNode : offersNode) {
                    StoreOffer offer = new StoreOffer();
                    offer.setStoreId("ica");
                    offer.setStoreName("ICA");
                    offer.setProductName(offerNode.path("name").asText());
                    offer.setOriginalPrice(offerNode.path("originalPrice").asDouble(0));
                    offer.setSalePrice(offerNode.path("price").asDouble(0));
                    offer.setProductCategory(offerNode.path("category").asText("Other"));
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
            log.warn("Failed to parse ICA response", e);
        }
        return offers;
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
