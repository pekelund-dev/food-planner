package com.foodplanner.controller;

import com.foodplanner.model.*;
import com.foodplanner.service.FirebaseService;
import com.foodplanner.service.MenuService;
import com.foodplanner.service.StoreOfferService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/menu")
public class MenuController {

    private final MenuService menuService;
    private final FirebaseService firebaseService;
    private final StoreOfferService storeOfferService;

    public MenuController(MenuService menuService,
                          FirebaseService firebaseService,
                          StoreOfferService storeOfferService) {
        this.menuService = menuService;
        this.firebaseService = firebaseService;
        this.storeOfferService = storeOfferService;
    }

    @GetMapping
    public String weeklyMenu(@AuthenticationPrincipal OAuth2User principal,
                              @RequestParam(required = false) String weekId,
                              Model model) {
        String userId = principal.getAttribute("sub");
        User user = firebaseService.getUser(userId);

        String targetWeekId = weekId != null ? weekId : menuService.getCurrentWeekId();
        model.addAttribute("weekId", targetWeekId);

        WeeklyMenu menu = menuService.getWeekMenu(userId, targetWeekId);
        model.addAttribute("menu", menu);
        model.addAttribute("user", user);
        model.addAttribute("days", new String[]{"MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"});

        return "menu/weekly";
    }

    @PostMapping("/generate")
    public String generateMenu(@AuthenticationPrincipal OAuth2User principal, Model model) {
        String userId = principal.getAttribute("sub");
        try {
            WeeklyMenu menu = menuService.generateAndSaveWeeklyMenu(userId);
            return "redirect:/menu?weekId=" + menu.getWeekId() + "&generated=true";
        } catch (Exception e) {
            model.addAttribute("error", "Failed to generate menu: " + e.getMessage());
            return "redirect:/menu?error=true";
        }
    }

    @GetMapping("/config")
    public String menuConfig(@AuthenticationPrincipal OAuth2User principal, Model model) {
        String userId = principal.getAttribute("sub");
        User user = firebaseService.getUser(userId);
        MenuConfig config = user != null && user.getMenuConfig() != null
                ? user.getMenuConfig() : new MenuConfig();
        model.addAttribute("config", config);
        model.addAttribute("availableStores", storeOfferService.getAvailableStores());
        model.addAttribute("selectedStoreIds",
                user != null && user.getSelectedStoreIds() != null ? user.getSelectedStoreIds() : List.of());
        return "menu/config";
    }

    @PostMapping("/config")
    public String saveMenuConfig(@AuthenticationPrincipal OAuth2User principal,
                                  @ModelAttribute MenuConfig config,
                                  @RequestParam(required = false) List<String> selectedStoreIds) {
        String userId = principal.getAttribute("sub");
        firebaseService.saveMenuConfig(userId, config);
        if (selectedStoreIds != null) {
            firebaseService.saveSelectedStores(userId, selectedStoreIds);
        }
        return "redirect:/menu/config?saved=true";
    }

    @PostMapping("/{weekId}/meal/{day}/{mealType}/{mealIndex}/complete")
    @ResponseBody
    public ResponseEntity<Map<String, Boolean>> toggleMealComplete(
            @AuthenticationPrincipal OAuth2User principal,
            @PathVariable String weekId,
            @PathVariable String day,
            @PathVariable String mealType,
            @PathVariable int mealIndex,
            @RequestParam boolean completed) {
        String userId = principal.getAttribute("sub");
        menuService.updateMealCompletion(userId, weekId, day, mealType, mealIndex, completed);
        return ResponseEntity.ok(Map.of("completed", completed));
    }
}
