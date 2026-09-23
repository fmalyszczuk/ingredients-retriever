package com.malyszczuk.ingredients_retriever.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class UnitConverterTest {

    private final UnitConverter unitConverter = new UnitConverter();

    @Test
    void convert_lbsToKg() {
        BigDecimal result = unitConverter.convert(BigDecimal.valueOf(2), "lbs", "kg");

        assertEquals(0, BigDecimal.valueOf(0.907).compareTo(result));
    }

    @Test
    void convert_lbsToGrams() {
        BigDecimal result = unitConverter.convert(BigDecimal.ONE, "lb", "g");

        assertEquals(0, BigDecimal.valueOf(453.592).compareTo(result));
    }

    @Test
    void convert_isCaseInsensitiveAndTrimsWhitespace() {
        BigDecimal result = unitConverter.convert(BigDecimal.valueOf(1000), " G ", "KG");

        assertEquals(0, BigDecimal.ONE.compareTo(result));
    }

    @Test
    void convert_sameUnitReturnsSameQuantity() {
        BigDecimal result = unitConverter.convert(BigDecimal.valueOf(5), "kg", "kg");

        assertEquals(0, BigDecimal.valueOf(5).compareTo(result));
    }

    @Test
    void convert_throws_whenQuantityIsNull() {
        assertThrows(IllegalArgumentException.class, () -> unitConverter.convert(null, "lb", "kg"));
    }

    @Test
    void convert_throws_whenUnitIsUnsupported() {
        assertThrows(IllegalArgumentException.class,
                () -> unitConverter.convert(BigDecimal.ONE, "lb", "pcs"));
    }

    @Test
    void convert_throws_whenUnitsAreDifferentTypes() {
        assertThrows(IllegalArgumentException.class,
                () -> unitConverter.convert(BigDecimal.ONE, "lb", "cups"));
    }

    @Test
    void convert_cupsToMl() {
        BigDecimal result = unitConverter.convert(BigDecimal.valueOf(2), "cups", "ml");

        assertEquals(0, BigDecimal.valueOf(473.176).compareTo(result));
    }

    @Test
    void convert_tbspToTsp() {
        BigDecimal result = unitConverter.convert(BigDecimal.ONE, "tbsp", "tsp");

        assertEquals(0, BigDecimal.valueOf(3).compareTo(result));
    }

    @Test
    void supports_recognizesKnownWeightAndVolumeUnitsCaseInsensitively() {
        assertTrue(unitConverter.supports("LB"));
        assertTrue(unitConverter.supports(" kg "));
        assertTrue(unitConverter.supports("Tbsp"));
    }

    @Test
    void supports_rejectsUnknownOrNullUnits() {
        assertFalse(unitConverter.supports("pcs"));
        assertFalse(unitConverter.supports(null));
    }

    @Test
    void canConvert_isTrue_forSameTypeUnits() {
        assertTrue(unitConverter.canConvert("lb", "kg"));
        assertTrue(unitConverter.canConvert("cup", "ml"));
    }

    @Test
    void canConvert_isFalse_forDifferentTypesOrUnsupportedUnits() {
        assertFalse(unitConverter.canConvert("lb", "ml"));
        assertFalse(unitConverter.canConvert("pcs", "box"));
        assertFalse(unitConverter.canConvert("lb", null));
    }
}
