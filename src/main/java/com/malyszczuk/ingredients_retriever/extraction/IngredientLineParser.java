package com.malyszczuk.ingredients_retriever.extraction;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Best-effort, deterministic parsing of a free-text ingredient line (e.g. "2 cups of flour")
 * into a structured name/quantity/unit. Lines that don't start with a recognizable quantity
 * (e.g. "a pinch of salt") are returned unparsed, with the full line as the name.
 */
@Component
public class IngredientLineParser {

    private static final Map<Character, BigDecimal> UNICODE_FRACTIONS = Map.ofEntries(
            Map.entry('½', new BigDecimal("0.50")),
            Map.entry('⅓', new BigDecimal("0.33")),
            Map.entry('⅔', new BigDecimal("0.67")),
            Map.entry('¼', new BigDecimal("0.25")),
            Map.entry('¾', new BigDecimal("0.75")),
            Map.entry('⅕', new BigDecimal("0.20")),
            Map.entry('⅛', new BigDecimal("0.13")),
            Map.entry('⅜', new BigDecimal("0.38")),
            Map.entry('⅝', new BigDecimal("0.63")),
            Map.entry('⅞', new BigDecimal("0.88"))
    );

    private static final String FRACTION_CHARS = "½⅓⅔¼¾⅕⅛⅜⅝⅞";

    private static final Pattern QUANTITY_PATTERN = Pattern.compile(
            "^\\s*(\\d+\\s*[" + FRACTION_CHARS + "]|\\d+\\s+\\d+/\\d+|\\d+/\\d+|[" + FRACTION_CHARS + "]|\\d+(?:\\.\\d+)?)\\s*(.*)$");

    private static final List<String> KNOWN_UNITS = List.of(
            "cups", "cup", "tablespoons", "tablespoon", "tbsp", "teaspoons", "teaspoon", "tsp",
            "grams", "gram", "g", "kilograms", "kilogram", "kg", "milliliters", "milliliter", "ml",
            "liters", "liter", "l", "ounces", "ounce", "oz", "pounds", "pound", "lbs", "lb",
            "pieces", "piece", "pcs", "cloves", "clove", "pinches", "pinch", "cans", "can", "slices", "slice"
    );

    private static final Pattern PARENTHETICAL_PATTERN = Pattern.compile("\\s*\\([^()]*\\)");

    private static final Pattern ALTERNATE_MEASUREMENT_PATTERN =
            Pattern.compile("^/\\s*\\d+(?:\\.\\d+)?\\s*([a-zA-Z]*)\\s+(.*)$");

    public ParsedIngredientLine parse(String line) {
        String trimmed = line.trim();
        Matcher matcher = QUANTITY_PATTERN.matcher(trimmed);

        if (!matcher.matches()) {
            return new ParsedIngredientLine(stripParentheticals(trimmed), null, null);
        }

        BigDecimal quantity = parseQuantity(matcher.group(1));
        String remainder = matcher.group(2).trim();

        String unit = null;
        for (String candidate : KNOWN_UNITS) {
            if (remainder.toLowerCase(Locale.ROOT).startsWith(candidate + " ") || remainder.equalsIgnoreCase(candidate)) {
                unit = candidate;
                remainder = remainder.substring(candidate.length()).trim();
                break;
            }
        }

        remainder = stripLeadingOf(remainder);
        remainder = stripAlternateMeasurement(remainder);

        String name = remainder.isEmpty() ? trimmed : remainder;
        return new ParsedIngredientLine(stripParentheticals(name), quantity, unit);
    }

    private String stripLeadingOf(String text) {
        if (text.toLowerCase(Locale.ROOT).startsWith("of ")) {
            return text.substring(3).trim();
        }
        return text;
    }

    /**
     * Strips a leading alternate-unit measurement (e.g. "/ 500g" in "1 lb / 500g beef mince"),
     * which some sites append as an imperial/metric conversion alongside the primary quantity.
     * Only strips it when the trailing letters look like a recognized unit, to avoid eating
     * real ingredient text on lines that happen to start with "/" for another reason.
     */
    private String stripAlternateMeasurement(String text) {
        Matcher matcher = ALTERNATE_MEASUREMENT_PATTERN.matcher(text.trim());
        if (!matcher.matches()) {
            return text;
        }

        String unitToken = matcher.group(1);
        if (!unitToken.isEmpty() && !KNOWN_UNITS.contains(unitToken.toLowerCase(Locale.ROOT))) {
            return text;
        }

        return matcher.group(2).trim();
    }

    /**
     * Removes parenthetical asides (e.g. "(spooned and leveled)", "(for serving)") from an
     * ingredient name, since they're prep notes rather than part of the ingredient's identity
     * and otherwise prevent the same ingredient from merging across recipes on the shopping list.
     */
    private String stripParentheticals(String text) {
        String current = text;
        String previous;
        do {
            previous = current;
            current = PARENTHETICAL_PATTERN.matcher(current).replaceAll("").trim();
        } while (!current.equals(previous));

        current = current.replaceAll("\\s{2,}", " ").trim();
        return current.isEmpty() ? text : current;
    }

    private BigDecimal parseQuantity(String token) {
        if (token.length() == 1 && UNICODE_FRACTIONS.containsKey(token.charAt(0))) {
            return UNICODE_FRACTIONS.get(token.charAt(0));
        }
        char lastChar = token.charAt(token.length() - 1);
        if (UNICODE_FRACTIONS.containsKey(lastChar)) {
            String wholePart = token.substring(0, token.length() - 1).trim();
            BigDecimal whole = wholePart.isEmpty() ? BigDecimal.ZERO : new BigDecimal(wholePart);
            return whole.add(UNICODE_FRACTIONS.get(lastChar));
        }
        if (token.contains(" ")) {
            String[] parts = token.split("\\s+", 2);
            return parseQuantity(parts[0]).add(parseFraction(parts[1]));
        }
        if (token.contains("/")) {
            return parseFraction(token);
        }
        return new BigDecimal(token);
    }

    private BigDecimal parseFraction(String token) {
        String[] parts = token.split("/", 2);
        BigDecimal numerator = new BigDecimal(parts[0]);
        BigDecimal denominator = new BigDecimal(parts[1]);
        return numerator.divide(denominator, 2, RoundingMode.HALF_UP);
    }
}
