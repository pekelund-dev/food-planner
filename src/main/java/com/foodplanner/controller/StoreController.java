package com.foodplanner.controller;

import com.foodplanner.model.StoreOffer;
import com.foodplanner.service.FirebaseService;
import com.foodplanner.service.StoreOfferService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
        List<String> selectedStores = user != null && user.getSelectedStoreIds() != null
                ? user.getSelectedStoreIds() : List.of();

        List<StoreOffer> allOffers = new ArrayList<>();
        for (String storeId : selectedStores) {
            allOffers.addAll(storeOfferService.getActiveOffers(storeId));
        }

        model.addAttribute("offers", allOffers);
        model.addAttribute("selectedStores", selectedStores);
        model.addAttribute("availableStores", storeOfferService.getAvailableStores());
        return "stores/offers";
    }

    @PostMapping("/{storeId}/refresh")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> refreshOffers(
            @PathVariable String storeId) {
        List<StoreOffer> offers = storeOfferService.refreshOffersForStore(storeId);
        return ResponseEntity.ok(Map.of("storeId", storeId, "count", offers.size()));
    }
}
