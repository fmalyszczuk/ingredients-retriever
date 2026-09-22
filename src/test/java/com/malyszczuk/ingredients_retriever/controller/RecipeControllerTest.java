package com.malyszczuk.ingredients_retriever.controller;

import com.malyszczuk.ingredients_retriever.domain.Ingredient;
import com.malyszczuk.ingredients_retriever.domain.Recipe;
import com.malyszczuk.ingredients_retriever.domain.RecipeSource;
import com.malyszczuk.ingredients_retriever.dto.CreateRecipeRequest;
import com.malyszczuk.ingredients_retriever.dto.IngredientRequest;
import com.malyszczuk.ingredients_retriever.service.RecipeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RecipeController.class)
class RecipeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RecipeService recipeService;

    @Test
    void addRecipe_returnsCreatedRecipeWithIngredients() throws Exception {
        Recipe recipe = Recipe.builder()
                .id(1L)
                .title("Pancakes")
                .sourceType(RecipeSource.MANUAL)
                .build();
        Ingredient ingredient = Ingredient.builder()
                .id(1L)
                .name("eggs")
                .quantity(BigDecimal.valueOf(2))
                .unit("pcs")
                .recipe(recipe)
                .build();
        recipe.setIngredients(List.of(ingredient));

        CreateRecipeRequest request = new CreateRecipeRequest("Pancakes", null,
                List.of(new IngredientRequest("eggs", BigDecimal.valueOf(2), "pcs")));

        when(recipeService.addRecipe(eq("Pancakes"), eq(RecipeSource.MANUAL), isNull(), any()))
                .thenReturn(recipe);

        mockMvc.perform(post("/recipes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Pancakes"))
                .andExpect(jsonPath("$.ingredients[0].name").value("eggs"));
    }

    @Test
    void addRecipe_rejectsEmptyIngredientList() throws Exception {
        CreateRecipeRequest request = new CreateRecipeRequest("Pancakes", null, List.of());

        mockMvc.perform(post("/recipes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listRecipes_returnsAllRecipes() throws Exception {
        Recipe recipe = Recipe.builder()
                .id(1L)
                .title("Pancakes")
                .sourceType(RecipeSource.MANUAL)
                .build();

        when(recipeService.listRecipes()).thenReturn(List.of(recipe));

        mockMvc.perform(get("/recipes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Pancakes"));
    }
}
