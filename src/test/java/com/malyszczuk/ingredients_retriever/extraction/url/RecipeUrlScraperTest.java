package com.malyszczuk.ingredients_retriever.extraction.url;

import com.malyszczuk.ingredients_retriever.extraction.RecipeExtractionException;
import com.malyszczuk.ingredients_retriever.net.FetchedPage;
import com.malyszczuk.ingredients_retriever.net.SafePageFetcher;
import com.malyszczuk.ingredients_retriever.net.UnsafeUrlException;
import com.malyszczuk.ingredients_retriever.net.UrlFetchException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecipeUrlScraperTest {

    private final SafePageFetcher pageFetcher = mock(SafePageFetcher.class);
    private final RecipeUrlScraper scraper = new RecipeUrlScraper(new ObjectMapper(), pageFetcher);

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

    private static final String RECIPE_PAGE = """
            <html><head><script type="application/ld+json">
            {"@type":"Recipe","name":"Crêpes","recipeIngredient":["2 œufs","250 g farine"]}
            </script></head><body></body></html>""";

    @Test
    void scrape_readsTheRecipeFromTheFetchedPage_usingTheCharsetTheServerSent() {
        byte[] body = RECIPE_PAGE.getBytes(StandardCharsets.ISO_8859_1);
        when(pageFetcher.fetch("https://example.com/crepes"))
                .thenReturn(new FetchedPage(URI.create("https://example.com/crepes"), body, StandardCharsets.UTF_8));

        RawRecipe recipe = scraper.scrape("https://example.com/crepes");

        // ISO-8859-1 bytes decoded as the declared UTF-8 would be garbage, so this proves the declared charset is used
        assertEquals(2, recipe.ingredientLines().size());
    }

    @Test
    void scrape_detectsTheCharset_whenTheServerDidNotSayWhichOne() {
        byte[] body = RECIPE_PAGE.getBytes(StandardCharsets.UTF_8);
        when(pageFetcher.fetch("https://example.com/crepes"))
                .thenReturn(new FetchedPage(URI.create("https://example.com/crepes"), body, null));

        RawRecipe recipe = scraper.scrape("https://example.com/crepes");

        assertEquals("Crêpes", recipe.title());
        assertEquals("2 œufs", recipe.ingredientLines().getFirst());
    }

    @Test
    void scrape_reportsAFailedFetchAsAnExtractionFailure() {
        when(pageFetcher.fetch("https://example.com/x")).thenThrow(new UrlFetchException("The page answered with HTTP 403"));

        RecipeExtractionException error = assertThrows(RecipeExtractionException.class, () -> scraper.scrape("https://example.com/x"));

        assertTrue(error.getMessage().contains("HTTP 403"));
    }

    @Test
    void scrape_letsAnUnsafeUrlThrough_untouched_soItIsAnsweredAs400NotAsAnExtractionFailure() {
        when(pageFetcher.fetch("http://169.254.169.254/")).thenThrow(new UnsafeUrlException("private or internal"));

        assertThrows(UnsafeUrlException.class, () -> scraper.scrape("http://169.254.169.254/"));
    }
}
