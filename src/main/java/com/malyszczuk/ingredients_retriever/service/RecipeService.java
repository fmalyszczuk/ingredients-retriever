package com.malyszczuk.ingredients_retriever.service;

import com.malyszczuk.ingredients_retriever.domain.Ingredient;
import com.malyszczuk.ingredients_retriever.domain.Recipe;
import com.malyszczuk.ingredients_retriever.domain.RecipeSource;
import com.malyszczuk.ingredients_retriever.dto.IngredientRequest;
import com.malyszczuk.ingredients_retriever.extraction.IngredientLineParser;
import com.malyszczuk.ingredients_retriever.extraction.ParsedIngredientLine;
import com.malyszczuk.ingredients_retriever.extraction.url.RawRecipe;
import com.malyszczuk.ingredients_retriever.extraction.url.RecipeUrlScraper;
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
    private final RecipeUrlScraper recipeUrlScraper;
    private final IngredientLineParser ingredientLineParser;

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

    @Transactional
    public Recipe addRecipeFromUrl(String url) {
        RawRecipe rawRecipe = recipeUrlScraper.scrape(url);

        List<IngredientRequest> ingredientRequests = rawRecipe.ingredientLines().stream()
                .map(ingredientLineParser::parse)
                .map(this::toIngredientRequest)
                .toList();

        return addRecipe(rawRecipe.title(), RecipeSource.URL, url, ingredientRequests);
    }

    private IngredientRequest toIngredientRequest(ParsedIngredientLine parsed) {
        return new IngredientRequest(parsed.name(), parsed.quantity(), parsed.unit());
    }
}
