package com.malyszczuk.ingredients_retriever.service;

import com.malyszczuk.ingredients_retriever.domain.Ingredient;
import com.malyszczuk.ingredients_retriever.domain.Recipe;
import com.malyszczuk.ingredients_retriever.domain.RecipeSource;
import com.malyszczuk.ingredients_retriever.dto.IngredientRequest;
import com.malyszczuk.ingredients_retriever.extraction.IngredientLineParser;
import com.malyszczuk.ingredients_retriever.extraction.ParsedIngredientLine;
import com.malyszczuk.ingredients_retriever.extraction.file.RecipeFileExtractor;
import com.malyszczuk.ingredients_retriever.extraction.text.ExtractedRecipe;
import com.malyszczuk.ingredients_retriever.extraction.text.RecipeTextExtractor;
import com.malyszczuk.ingredients_retriever.extraction.url.RawRecipe;
import com.malyszczuk.ingredients_retriever.extraction.url.RecipeUrlScraper;
import com.malyszczuk.ingredients_retriever.repository.RecipeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class RecipeService {

    private final RecipeRepository recipeRepository;
    private final ShoppingListService shoppingListService;
    private final RecipeUrlScraper recipeUrlScraper;
    private final IngredientLineParser ingredientLineParser;
    private final RecipeTextExtractor recipeTextExtractor;
    private final RecipeFileExtractor recipeFileExtractor;

    @Transactional(readOnly = true)
    public List<Recipe> listRecipes() {
        return recipeRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Recipe getRecipe(Long id) {
        return recipeRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("No recipe with id " + id));
    }

    @Transactional
    public void deleteRecipe(Long id, boolean removeFromShoppingList) {
        Recipe recipe = getRecipe(id);

        if (removeFromShoppingList) {
            shoppingListService.removeIngredients(recipe.getIngredients());
        }
        recipeRepository.delete(recipe);
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

    @Transactional
    public Recipe addRecipeFromText(String text) {
        String trimmedText = text.trim();
        ExtractedRecipe extracted = recipeTextExtractor.extract(trimmedText);

        return addRecipe(extracted.title(), RecipeSource.TEXT, trimmedText, extracted.ingredients());
    }

    @Transactional
    public Recipe addRecipeFromFile(String filename, byte[] content) {
        ExtractedRecipe extracted = recipeFileExtractor.extract(filename, content);

        return addRecipe(extracted.title(), RecipeSource.FILE, filename, extracted.ingredients());
    }

    private IngredientRequest toIngredientRequest(ParsedIngredientLine parsed) {
        return new IngredientRequest(parsed.name(), parsed.quantity(), parsed.unit());
    }
}
