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
        List<Store> selectedStores = user != null && user.getSelectedStores() != null
                ? user.getSelectedStores() : List.of();

        // Derive the unique chain IDs for offer fetching
        Set<String> chainIds = selectedStores.stream()
                .map(Store::getChain)
                .filter(c -> c != null && !c.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<StoreOffer> allOffers = new ArrayList<>();
        for (String chainId : chainIds) {
            allOffers.addAll(storeOfferService.getActiveOffers(chainId));
        }

        model.addAttribute("offers", allOffers);
        model.addAttribute("selectedStores", selectedStores);
        model.addAttribute("chainIds", chainIds);
        model.addAttribute("availableStores", storeOfferService.getAvailableStores());
        return "stores/offers";
    }

    /** HTMX endpoint: returns store-name autocomplete suggestions as an HTML fragment. */
    @GetMapping("/search")
    public String searchStores(@RequestParam(defaultValue = "") String q, Model model) {
        model.addAttribute("results", storeOfferService.searchStores(q));
        return "fragments/store-suggestions :: results";
    }

    @PostMapping("/{storeId}/refresh")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> refreshOffers(
            @PathVariable String storeId) {
        List<StoreOffer> offers = storeOfferService.refreshOffersForStore(storeId);
        return ResponseEntity.ok(Map.of("storeId", storeId, "count", offers.size()));
    }
}

