package com.malyszczuk.ingredients_retriever.extraction.url;

import com.malyszczuk.ingredients_retriever.extraction.RecipeExtractionException;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RecipeUrlScraper {

    private final ObjectMapper objectMapper;

    public RawRecipe scrape(String url) {
        return extract(fetch(url), url);
    }

    RawRecipe extract(Document document, String sourceUrl) {
        for (Element script : document.select("script[type=application/ld+json]")) {
            JsonNode root = tryParseJson(script.data());
            if (root == null) {
                continue;
            }
            JsonNode recipeNode = findRecipeNode(root);
            if (recipeNode != null) {
                return toRawRecipe(recipeNode, sourceUrl);
            }
        }

        throw new RecipeExtractionException("No schema.org Recipe data found at " + sourceUrl);
    }

    private Document fetch(String url) {
        try {
            return Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (compatible; PantryPalBot/1.0)")
                    .timeout(10_000)
                    .get();
        } catch (IOException e) {
            throw new RecipeExtractionException(
                    "Could not fetch recipe page: " + url + " (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")", e);
        }
    }

    private JsonNode tryParseJson(String text) {
        try {
            return objectMapper.readTree(text);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private JsonNode findRecipeNode(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        if (node.isObject() && isRecipeType(node.path("@type"))) {
            return node;
        }
        if (node.isObject() && node.has("@graph")) {
            JsonNode found = findRecipeNode(node.path("@graph"));
            if (found != null) {
                return found;
            }
        }
        if (node.isArray()) {
            for (JsonNode element : node) {
                JsonNode found = findRecipeNode(element);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private boolean isRecipeType(JsonNode typeNode) {
        if (typeNode.isTextual()) {
            return "Recipe".equalsIgnoreCase(typeNode.asString());
        }
        if (typeNode.isArray()) {
            for (JsonNode element : typeNode) {
                if (element.isTextual() && "Recipe".equalsIgnoreCase(element.asString())) {
                    return true;
                }
            }
        }
        return false;
    }

    private RawRecipe toRawRecipe(JsonNode recipeNode, String url) {
        String title = recipeNode.path("name").asString("Untitled recipe");

        JsonNode ingredientsNode = recipeNode.has("recipeIngredient")
                ? recipeNode.path("recipeIngredient")
                : recipeNode.path("ingredients");

        List<String> ingredientLines = new ArrayList<>();
        if (ingredientsNode.isArray()) {
            for (JsonNode line : ingredientsNode) {
                String text = line.asString("").trim();
                if (!text.isEmpty()) {
                    ingredientLines.add(text);
                }
            }
        }

        if (ingredientLines.isEmpty()) {
            throw new RecipeExtractionException("Recipe data found at " + url + " but it contained no ingredients");
        }

        return new RawRecipe(title, ingredientLines);
    }
}
