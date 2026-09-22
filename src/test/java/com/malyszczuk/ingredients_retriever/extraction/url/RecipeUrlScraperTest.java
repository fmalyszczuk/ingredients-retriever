package com.malyszczuk.ingredients_retriever.extraction.url;

import com.malyszczuk.ingredients_retriever.extraction.RecipeExtractionException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RecipeUrlScraperTest {

    private final RecipeUrlScraper scraper = new RecipeUrlScraper(new ObjectMapper());

    @Test
    void extract_readsTitleAndIngredients_fromPlainRecipeJsonLd() {
        String html = """
                <html><head>
                <script type="application/ld+json">
                {"@context":"https://schema.org","@type":"Recipe","name":"Pancakes",
                 "recipeIngredient":["2 eggs","200 g flour"]}
                </script>
                </head><body></body></html>
                """;

        RawRecipe result = scraper.extract(Jsoup.parse(html), "https://example.com/pancakes");

        assertEquals("Pancakes", result.title());
        assertEquals(2, result.ingredientLines().size());
        assertEquals("2 eggs", result.ingredientLines().get(0));
        assertEquals("200 g flour", result.ingredientLines().get(1));
    }

    @Test
    void extract_findsRecipe_insideGraphArray() {
        String html = """
                <html><head>
                <script type="application/ld+json">
                {"@context":"https://schema.org","@graph":[
                  {"@type":"WebPage","name":"Homepage"},
                  {"@type":"Recipe","name":"Omelette","recipeIngredient":["3 eggs"]}
                ]}
                </script>
                </head><body></body></html>
                """;

        RawRecipe result = scraper.extract(Jsoup.parse(html), "https://example.com/omelette");

        assertEquals("Omelette", result.title());
        assertEquals("3 eggs", result.ingredientLines().get(0));
    }

    @Test
    void extract_throws_whenNoRecipeJsonLdPresent() {
        Document document = Jsoup.parse("<html><head></head><body>No recipe here</body></html>");

        assertThrows(RecipeExtractionException.class, () -> scraper.extract(document, "https://example.com/not-a-recipe"));
    }
}
