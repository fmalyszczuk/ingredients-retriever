package com.malyszczuk.ingredients_retriever.extraction;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class IngredientLineParserTest {

    private final IngredientLineParser parser = new IngredientLineParser();

    @Test
    void parse_extractsIntegerQuantityAndName_withNoUnit() {
        ParsedIngredientLine result = parser.parse("2 eggs");

        assertEquals("eggs", result.name());
        assertEquals(0, BigDecimal.valueOf(2).compareTo(result.quantity()));
        assertNull(result.unit());
    }

    @Test
    void parse_extractsQuantityUnitAndName() {
        ParsedIngredientLine result = parser.parse("200 g flour");

        assertEquals("flour", result.name());
        assertEquals(0, BigDecimal.valueOf(200).compareTo(result.quantity()));
        assertEquals("g", result.unit());
    }

    @Test
    void parse_stripsLeadingOf_afterUnit() {
        ParsedIngredientLine result = parser.parse("2 cups of sugar");

        assertEquals("sugar", result.name());
        assertEquals(0, BigDecimal.valueOf(2).compareTo(result.quantity()));
        assertEquals("cups", result.unit());
    }

    @Test
    void parse_handlesSimpleFractionQuantity() {
        ParsedIngredientLine result = parser.parse("1/2 cup milk");

        assertEquals("milk", result.name());
        assertEquals(0, new BigDecimal("0.50").compareTo(result.quantity()));
        assertEquals("cup", result.unit());
    }

    @Test
    void parse_handlesMixedNumberQuantity() {
        ParsedIngredientLine result = parser.parse("1 1/2 cups milk");

        assertEquals("milk", result.name());
        assertEquals(0, new BigDecimal("1.50").compareTo(result.quantity()));
        assertEquals("cups", result.unit());
    }

    @Test
    void parse_handlesUnicodeMixedFraction_immediatelyFollowingWholeNumber() {
        ParsedIngredientLine result = parser.parse("1½ cups all-purpose flour (spooned and leveled)");

        assertEquals("all-purpose flour", result.name());
        assertEquals(0, new BigDecimal("1.50").compareTo(result.quantity()));
        assertEquals("cups", result.unit());
    }

    @Test
    void parse_stripsParentheticalAside_regardlessOfPositionInName() {
        ParsedIngredientLine result = parser.parse(
                "3 tablespoons unsalted butter (melted and cooled slightly, or neutral oil, plus more for the pan)");

        assertEquals("unsalted butter", result.name());
        assertEquals(0, BigDecimal.valueOf(3).compareTo(result.quantity()));
        assertEquals("tablespoons", result.unit());
    }

    @Test
    void parse_stripsParenthetical_whenLineHasNoLeadingQuantity() {
        ParsedIngredientLine result = parser.parse("Maple syrup (for serving)");

        assertEquals("Maple syrup", result.name());
        assertNull(result.quantity());
        assertNull(result.unit());
    }

    @Test
    void parse_handlesStandaloneUnicodeFraction() {
        ParsedIngredientLine result = parser.parse("½ teaspoon sea salt");

        assertEquals("sea salt", result.name());
        assertEquals(0, new BigDecimal("0.50").compareTo(result.quantity()));
        assertEquals("teaspoon", result.unit());
    }

    @Test
    void parse_stripsNestedParentheticals() {
        ParsedIngredientLine result = parser.parse("1 onion (, finely chopped (brown, yellow or white))");

        assertEquals("onion", result.name());
        assertEquals(0, BigDecimal.valueOf(1).compareTo(result.quantity()));
    }

    @Test
    void parse_stripsDoublyNestedParentheticals_leavingNoEmptyShell() {
        ParsedIngredientLine result = parser.parse("1/2 cup (125 ml) dry red wine ((sub water or beef broth/stock))");

        assertEquals("dry red wine", result.name());
    }

    @Test
    void parse_stripsMultipleAdjacentParentheticalGroups() {
        ParsedIngredientLine result = parser.parse("2 beef bouillon cubes (, crumbled OR granulated beef bouillon (Note 2))");

        assertEquals("beef bouillon cubes", result.name());
        assertEquals(0, BigDecimal.valueOf(2).compareTo(result.quantity()));
    }

    @Test
    void parse_stripsAlternateMetricMeasurement_afterImperialQuantityAndUnit() {
        ParsedIngredientLine result = parser.parse("1 lb / 500g beef mince (ground beef) (OR half pork, half beef (Note 1))");

        assertEquals("beef mince", result.name());
        assertEquals(0, BigDecimal.valueOf(1).compareTo(result.quantity()));
        assertEquals("lb", result.unit());
    }

    @Test
    void parse_stripsAlternateMeasurement_whenNoSpaceBetweenQuantityAndOriginalUnit() {
        ParsedIngredientLine result = parser.parse("800g / 28 oz can crushed tomato ((or tomato passata))");

        assertEquals("can crushed tomato", result.name());
        assertEquals(0, BigDecimal.valueOf(800).compareTo(result.quantity()));
        assertEquals("g", result.unit());
    }

    @Test
    void parse_leavesTextUnchanged_whenSlashIsNotFollowedByRecognizedAlternateUnit() {
        ParsedIngredientLine result = parser.parse("2 cans / jars roasted red peppers");

        assertEquals("/ jars roasted red peppers", result.name());
        assertEquals(0, BigDecimal.valueOf(2).compareTo(result.quantity()));
        assertEquals("cans", result.unit());
    }

    @Test
    void parse_returnsWholeLineUnparsed_whenNoLeadingQuantity() {
        ParsedIngredientLine result = parser.parse("a pinch of salt");

        assertEquals("a pinch of salt", result.name());
        assertNull(result.quantity());
        assertNull(result.unit());
    }
}
