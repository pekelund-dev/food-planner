package com.foodplanner.controller;

import com.foodplanner.model.User;
import com.foodplanner.service.FirebaseService;
import com.foodplanner.service.MenuService;
import com.foodplanner.service.ShoppingListService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final FirebaseService firebaseService;
    private final MenuService menuService;
    private final ShoppingListService shoppingListService;

    public HomeController(FirebaseService firebaseService,
                          MenuService menuService,
                          ShoppingListService shoppingListService) {
        this.firebaseService = firebaseService;
        this.menuService = menuService;
        this.shoppingListService = shoppingListService;
    }

    @GetMapping("/")
    public String index(@AuthenticationPrincipal OAuth2User principal) {
        if (principal != null) {
            return "redirect:/dashboard";
        }
        return "index";
    }

    @GetMapping("/login")
    public String login(@AuthenticationPrincipal OAuth2User principal) {
        if (principal != null) {
            return "redirect:/dashboard";
        }
        return "login";
    }

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal OAuth2User principal, Model model) {
        String userId = principal.getAttribute("sub");
        String email = principal.getAttribute("email");
        String name = principal.getAttribute("name");
        String picture = principal.getAttribute("picture");

        // Get or create user — fall back to a minimal User if Firebase is unavailable
        try {
            User user = firebaseService.getOrCreateUser(userId, email, name, picture);
            model.addAttribute("user", user);
        } catch (Exception e) {
            User fallback = new User();
            fallback.setId(userId);
            fallback.setEmail(email);
            fallback.setName(name);
            fallback.setPictureUrl(picture);
            model.addAttribute("user", fallback);
        }

        // Current week id — safe to compute independently
        try {
            model.addAttribute("currentWeekId", menuService.getCurrentWeekId());
        } catch (Exception e) {
            model.addAttribute("currentWeekId", null);
        }

        // Get current week menu
        try {
            var currentMenu = menuService.getCurrentWeekMenu(userId);
            model.addAttribute("currentMenu", currentMenu);
        } catch (Exception e) {
            model.addAttribute("currentMenu", null);
        }

        // Get recent shopping lists
        try {
            var shoppingLists = shoppingListService.getShoppingLists(userId);
            model.addAttribute("shoppingLists", shoppingLists);
        } catch (Exception e) {
            model.addAttribute("shoppingLists", java.util.List.of());
        }

        return "dashboard";
    }
}
