package com.malyszczuk.ingredients_retriever.controller;

import com.malyszczuk.ingredients_retriever.domain.Ingredient;
import com.malyszczuk.ingredients_retriever.domain.Recipe;
import com.malyszczuk.ingredients_retriever.domain.RecipeSource;
import com.malyszczuk.ingredients_retriever.dto.CreateRecipeRequest;
import com.malyszczuk.ingredients_retriever.dto.IngredientResponse;
import com.malyszczuk.ingredients_retriever.dto.RecipeResponse;
import com.malyszczuk.ingredients_retriever.service.RecipeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/recipes")
@RequiredArgsConstructor
public class RecipeController {

    private final RecipeService recipeService;

    @GetMapping
    public List<RecipeResponse> listRecipes() {
        return recipeService.listRecipes().stream()
                .map(this::toResponse)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RecipeResponse addRecipe(@Valid @RequestBody CreateRecipeRequest request) {
        Recipe recipe = recipeService.addRecipe(
                request.title(),
                RecipeSource.MANUAL,
                request.sourceReference(),
                request.ingredients());
        return toResponse(recipe);
    }

    private RecipeResponse toResponse(Recipe recipe) {
        List<IngredientResponse> ingredients = recipe.getIngredients().stream()
                .map(this::toResponse)
                .toList();
        return new RecipeResponse(
                recipe.getId(),
                recipe.getTitle(),
                recipe.getSourceType().name(),
                recipe.getSourceReference(),
                recipe.getCreatedAt(),
                ingredients);
    }

    private IngredientResponse toResponse(Ingredient ingredient) {
        return new IngredientResponse(ingredient.getId(), ingredient.getName(), ingredient.getQuantity(), ingredient.getUnit());
    }
}
