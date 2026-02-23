package com.foodplanner.controller;

import com.foodplanner.model.Meal;
import com.foodplanner.model.Rating;
import com.foodplanner.model.Recipe;
import com.foodplanner.service.FirebaseService;
import com.foodplanner.service.RecipeService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/recipes")
public class RecipeController {

    private final RecipeService recipeService;
    private final FirebaseService firebaseService;

    public RecipeController(RecipeService recipeService, FirebaseService firebaseService) {
        this.recipeService = recipeService;
        this.firebaseService = firebaseService;
    }

    @GetMapping
    public String listRecipes(@AuthenticationPrincipal OAuth2User principal, Model model) {
        String userId = principal.getAttribute("sub");
        List<Recipe> recipes = recipeService.getRecipes(userId);
        List<Meal> meals = firebaseService.getMeals(userId);
        model.addAttribute("recipes", recipes);
        model.addAttribute("meals", meals);
        return "recipe/list";
    }

    @GetMapping("/{recipeId}")
    public String recipeDetail(@AuthenticationPrincipal OAuth2User principal,
                                @PathVariable String recipeId,
                                Model model) {
        String userId = principal.getAttribute("sub");
        Recipe recipe = recipeService.getRecipe(userId, recipeId);
        if (recipe == null) return "redirect:/recipes";

        List<Rating> ratings = firebaseService.getRatingsForMeal(userId,
                recipe.getMealId() != null ? recipe.getMealId() : recipeId);
        model.addAttribute("recipe", recipe);
        model.addAttribute("ratings", ratings);
        return "recipe/detail";
    }

    @GetMapping("/new")
    public String newRecipeForm(Model model) {
        model.addAttribute("recipe", new Recipe());
        return "recipe/edit";
    }

    @GetMapping("/{recipeId}/edit")
    public String editRecipeForm(@AuthenticationPrincipal OAuth2User principal,
                                  @PathVariable String recipeId,
                                  Model model) {
        String userId = principal.getAttribute("sub");
        Recipe recipe = recipeService.getRecipe(userId, recipeId);
        if (recipe == null) return "redirect:/recipes";
        model.addAttribute("recipe", recipe);
        return "recipe/edit";
    }

    @PostMapping
    public String saveRecipe(@AuthenticationPrincipal OAuth2User principal,
                              @ModelAttribute Recipe recipe) {
        String userId = principal.getAttribute("sub");
        recipeService.saveRecipe(userId, recipe);
        return "redirect:/recipes";
    }

    @PostMapping("/generate")
    public String generateRecipe(@AuthenticationPrincipal OAuth2User principal,
                                  @RequestParam String mealName,
                                  Model model) {
        String userId = principal.getAttribute("sub");
        try {
            Recipe recipe = recipeService.generateRecipeForMeal(userId, mealName);
            return "redirect:/recipes/" + recipe.getId();
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            return "redirect:/recipes?error=true";
        }
    }

    @DeleteMapping("/{recipeId}")
    @ResponseBody
    public ResponseEntity<Void> deleteRecipe(@AuthenticationPrincipal OAuth2User principal,
                                              @PathVariable String recipeId) {
        String userId = principal.getAttribute("sub");
        recipeService.deleteRecipe(userId, recipeId);
        return ResponseEntity.ok().build();
    }

    // Meals management

    @GetMapping("/meals")
    public String listMeals(@AuthenticationPrincipal OAuth2User principal, Model model) {
        String userId = principal.getAttribute("sub");
        List<Meal> meals = firebaseService.getMeals(userId);
        model.addAttribute("meals", meals);
        return "recipe/meals";
    }

    @PostMapping("/meals")
    public String saveMeal(@AuthenticationPrincipal OAuth2User principal,
                            @ModelAttribute Meal meal) {
        String userId = principal.getAttribute("sub");
        firebaseService.saveMeal(userId, meal);
        return "redirect:/recipes/meals";
    }

    // Ratings

    @PostMapping("/{recipeId}/rate")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> rateMeal(
            @AuthenticationPrincipal OAuth2User principal,
            @PathVariable String recipeId,
            @RequestParam int score,
            @RequestParam(required = false) String comment) {
        String userId = principal.getAttribute("sub");
        Recipe recipe = recipeService.getRecipe(userId, recipeId);
        if (recipe == null) return ResponseEntity.notFound().build();

        Rating rating = new Rating();
        rating.setMealId(recipe.getMealId() != null ? recipe.getMealId() : recipeId);
        rating.setMealName(recipe.getName());
        rating.setScore(score);
        rating.setComment(comment);

        Rating saved = firebaseService.saveRating(userId, rating);
        return ResponseEntity.ok(Map.of("id", saved.getId(), "score", saved.getScore()));
    }

    // HTMX partial for ratings
    @GetMapping("/{recipeId}/ratings-fragment")
    public String ratingsFragment(@AuthenticationPrincipal OAuth2User principal,
                                   @PathVariable String recipeId,
                                   Model model) {
        String userId = principal.getAttribute("sub");
        Recipe recipe = recipeService.getRecipe(userId, recipeId);
        List<Rating> ratings = firebaseService.getRatingsForMeal(userId,
                recipe != null && recipe.getMealId() != null ? recipe.getMealId() : recipeId);
        model.addAttribute("ratings", ratings);
        model.addAttribute("recipeId", recipeId);
        return "fragments/ratings :: ratingsFragment";
    }
}
