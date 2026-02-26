package com.foodplanner.controller;

import com.foodplanner.model.Store;
import com.foodplanner.model.StoreOffer;
import com.foodplanner.service.FirebaseService;
import com.foodplanner.service.StoreOfferService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/stores")
public class StoreController {

    private final StoreOfferService storeOfferService;
    private final FirebaseService firebaseService;

    public StoreController(StoreOfferService storeOfferService, FirebaseService firebaseService) {
        this.storeOfferService = storeOfferService;
        this.firebaseService = firebaseService;
    }

    @GetMapping
    public String storeOffers(@AuthenticationPrincipal OAuth2User principal, Model model) {
        String userId = principal.getAttribute("sub");
        var user = firebaseService.getUser(userId);
        // Deduplicate by store ID to handle stale duplicate entries in Firestore
        List<Store> raw = user != null && user.getSelectedStores() != null
                ? user.getSelectedStores() : List.of();
        List<Store> selectedStores = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Store s : raw) {
            if (s.hasValidId() && seen.add(s.getId())) {
                selectedStores.add(s);
            }
        }

        List<StoreOffer> allOffers = new ArrayList<>();
        for (Store store : selectedStores) {
            allOffers.addAll(storeOfferService.getActiveOffers(store.getId()));
        }

        // Flat list sorted by product name (used by the "sort by name" view)
        List<StoreOffer> offersSortedByName = allOffers.stream()
                .sorted(Comparator.comparing(StoreOffer::getProductName,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .collect(Collectors.toList());

        // Grouped map: category → offers sorted by name (TreeMap gives alphabetical category order)
        Map<String, List<StoreOffer>> offersByCategory = offersSortedByName.stream()
                .collect(Collectors.groupingBy(
                        o -> o.getProductCategory() != null && !o.getProductCategory().isBlank()
                                ? o.getProductCategory() : "Other",
                        TreeMap::new,
                        Collectors.toList()
                ));

        model.addAttribute("offers", offersSortedByName);
        model.addAttribute("offersByCategory", offersByCategory);
        model.addAttribute("selectedStores", selectedStores);
        model.addAttribute("availableStores", storeOfferService.getAvailableStores());
        return "stores/offers";
    }

    /** HTMX endpoint: returns store-name autocomplete suggestions as an HTML fragment. */
    @GetMapping("/search")
    public String searchStores(@RequestParam(defaultValue = "") String q, Model model) {
        model.addAttribute("results", storeOfferService.searchStores(q));
        model.addAttribute("q", q);
        return "fragments/store-suggestions :: results";
    }

    @PostMapping("/{storeId}/refresh")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> refreshOffers(
            @PathVariable String storeId,
            @AuthenticationPrincipal OAuth2User principal) {
        String userId = principal != null ? principal.getAttribute("sub") : null;
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }
        var user = firebaseService.getUser(userId);

        // Look up the specific store from the user's saved stores
        Store store = null;
        if (user != null && user.getSelectedStores() != null) {
            store = user.getSelectedStores().stream()
                    .filter(s -> storeId.equals(s.getId()))
                    .findFirst()
                    .orElse(null);
        }
        if (store == null) {
            // Fallback: treat storeId as a chain key
            store = new Store(storeId, storeId, storeId);
        }

        List<StoreOffer> offers = storeOfferService.refreshOffersForSpecificStore(store);
        return ResponseEntity.ok(Map.of("storeId", storeId, "storeName", store.getName(), "count", offers.size()));
    }
}

