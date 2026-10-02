package com.malyszczuk.ingredients_retriever.controller;

import com.malyszczuk.ingredients_retriever.domain.Ingredient;
import com.malyszczuk.ingredients_retriever.domain.Recipe;
import com.malyszczuk.ingredients_retriever.domain.RecipeSource;
import com.malyszczuk.ingredients_retriever.dto.CreateRecipeFromTextRequest;
import com.malyszczuk.ingredients_retriever.dto.CreateRecipeFromUrlRequest;
import com.malyszczuk.ingredients_retriever.dto.CreateRecipeRequest;
import com.malyszczuk.ingredients_retriever.dto.IngredientResponse;
import com.malyszczuk.ingredients_retriever.dto.RecipeResponse;
import com.malyszczuk.ingredients_retriever.service.RecipeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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

    @GetMapping("/{id}")
    public RecipeResponse getRecipe(@PathVariable Long id) {
        return toResponse(recipeService.getRecipe(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRecipe(@PathVariable Long id,
                             @RequestParam(defaultValue = "false") boolean removeFromShoppingList) {
        recipeService.deleteRecipe(id, removeFromShoppingList);
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

    @PostMapping("/from-url")
    @ResponseStatus(HttpStatus.CREATED)
    public RecipeResponse addRecipeFromUrl(@Valid @RequestBody CreateRecipeFromUrlRequest request) {
        Recipe recipe = recipeService.addRecipeFromUrl(request.url());
        return toResponse(recipe);
    }

    @PostMapping("/from-text")
    @ResponseStatus(HttpStatus.CREATED)
    public RecipeResponse addRecipeFromText(@Valid @RequestBody CreateRecipeFromTextRequest request) {
        Recipe recipe = recipeService.addRecipeFromText(request.text());
        return toResponse(recipe);
    }

    @PostMapping(value = "/from-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public RecipeResponse addRecipeFromFile(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }
        Recipe recipe = recipeService.addRecipeFromFile(safeFilename(file.getOriginalFilename()), file.getBytes());
        return toResponse(recipe);
    }

    // Browsers may send a full client-side path; keep only the file name, and bound its length for storage.
    private String safeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "upload";
        }
        String name = originalFilename.substring(Math.max(originalFilename.lastIndexOf('/'), originalFilename.lastIndexOf('\\')) + 1);
        return name.length() > 200 ? name.substring(name.length() - 200) : name;
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
