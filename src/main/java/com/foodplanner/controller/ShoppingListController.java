package com.foodplanner.controller;

import com.foodplanner.model.ShoppingList;
import com.foodplanner.service.MenuService;
import com.foodplanner.service.ShoppingListService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/shopping")
public class ShoppingListController {

    private final ShoppingListService shoppingListService;
    private final MenuService menuService;

    public ShoppingListController(ShoppingListService shoppingListService,
                                   MenuService menuService) {
        this.shoppingListService = shoppingListService;
        this.menuService = menuService;
    }

    @GetMapping
    public String shoppingLists(@AuthenticationPrincipal OAuth2User principal, Model model) {
        String userId = principal.getAttribute("sub");
        List<ShoppingList> lists = shoppingListService.getShoppingLists(userId);
        model.addAttribute("lists", lists);
        model.addAttribute("currentWeekId", menuService.getCurrentWeekId());
        return "shopping/index";
    }

    @GetMapping("/{listId}")
    public String shoppingListDetail(@AuthenticationPrincipal OAuth2User principal,
                                      @PathVariable String listId,
                                      Model model) {
        String userId = principal.getAttribute("sub");
        ShoppingList list = shoppingListService.getShoppingList(userId, listId);
        if (list == null) return "redirect:/shopping";
        model.addAttribute("list", list);
        return "shopping/detail";
    }

    @PostMapping("/generate/{weekId}")
    public String generateShoppingList(@AuthenticationPrincipal OAuth2User principal,
                                        @PathVariable String weekId) {
        String userId = principal.getAttribute("sub");
        try {
            ShoppingList list = shoppingListService.generateShoppingListForWeek(userId, weekId);
            return "redirect:/shopping/" + list.getId();
        } catch (Exception e) {
            return "redirect:/shopping?error=" + e.getMessage();
        }
    }

    @PostMapping("/{listId}/items/{itemId}/check")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> toggleItem(
            @AuthenticationPrincipal OAuth2User principal,
            @PathVariable String listId,
            @PathVariable String itemId,
            @RequestParam boolean checked) {
        String userId = principal.getAttribute("sub");
        ShoppingList list = shoppingListService.updateItemChecked(userId, listId, itemId, checked);
        return ResponseEntity.ok(Map.of(
                "checked", checked,
                "checkedCount", list.getCheckedCount(),
                "totalCount", list.getItems() != null ? list.getItems().size() : 0
        ));
    }

    // HTMX fragment for shopping list items
    @GetMapping("/{listId}/items-fragment")
    public String itemsFragment(@AuthenticationPrincipal OAuth2User principal,
                                 @PathVariable String listId,
                                 Model model) {
        String userId = principal.getAttribute("sub");
        ShoppingList list = shoppingListService.getShoppingList(userId, listId);
        model.addAttribute("list", list);
        return "fragments/shopping-items :: shoppingItemsFragment";
    }
}
