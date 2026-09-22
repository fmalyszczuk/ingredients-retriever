package com.malyszczuk.ingredients_retriever.service;

import com.malyszczuk.ingredients_retriever.domain.Ingredient;
import com.malyszczuk.ingredients_retriever.domain.Recipe;
import com.malyszczuk.ingredients_retriever.domain.RecipeSource;
import com.malyszczuk.ingredients_retriever.dto.IngredientRequest;
import com.malyszczuk.ingredients_retriever.repository.RecipeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RecipeService {

    private final RecipeRepository recipeRepository;
    private final ShoppingListService shoppingListService;

    @Transactional(readOnly = true)
    public List<Recipe> listRecipes() {
        return recipeRepository.findAll();
    }

    @Transactional
    public Recipe addRecipe(String title, RecipeSource sourceType, String sourceReference,
                             List<IngredientRequest> ingredientRequests) {
        Recipe recipe = Recipe.builder()
                .title(title)
                .sourceType(sourceType)
                .sourceReference(sourceReference)
                .build();

        ingredientRequests.forEach(request -> recipe.addIngredient(
                Ingredient.builder()
                        .name(request.name())
                        .quantity(request.quantity())
                        .unit(request.unit())
                        .build()));

        Recipe saved = recipeRepository.save(recipe);
        shoppingListService.addIngredients(saved.getIngredients());

        return saved;
    }
}
