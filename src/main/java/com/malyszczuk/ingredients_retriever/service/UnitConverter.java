package com.malyszczuk.ingredients_retriever.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

@Component
public class UnitConverter {

    private static final int SCALE = 3;

    public enum UnitType { WEIGHT, VOLUME }

    private record UnitDefinition(UnitType type, BigDecimal factorToBaseUnit) {
    }

    // Base unit is grams for WEIGHT, millilitres for VOLUME.
    private static final Map<String, UnitDefinition> UNITS = buildUnits();

    private static Map<String, UnitDefinition> buildUnits() {
        Map<String, UnitDefinition> units = new HashMap<>();

        register(units, UnitType.WEIGHT, BigDecimal.valueOf(0.001), "mg", "milligram", "milligrams");
        register(units, UnitType.WEIGHT, BigDecimal.ONE, "g", "gram", "grams");
        register(units, UnitType.WEIGHT, BigDecimal.valueOf(1000), "kg", "kilogram", "kilograms");
        register(units, UnitType.WEIGHT, BigDecimal.valueOf(453.59237), "lb", "lbs", "pound", "pounds");

        register(units, UnitType.VOLUME, BigDecimal.ONE, "ml", "milliliter", "milliliters", "millilitre", "millilitres");
        register(units, UnitType.VOLUME, BigDecimal.valueOf(1000), "l", "litre", "litres", "liter", "liters");
        register(units, UnitType.VOLUME, BigDecimal.valueOf(473.176473), "pint", "pints");
        register(units, UnitType.VOLUME, BigDecimal.valueOf(29.5735295625), "oz", "fl oz", "fluid ounce", "fluid ounces");
        register(units, UnitType.VOLUME, BigDecimal.valueOf(4.92892159375), "tsp", "teaspoon", "teaspoons");
        register(units, UnitType.VOLUME, BigDecimal.valueOf(14.78676478125), "tbsp", "tablespoon", "tablespoons");
        register(units, UnitType.VOLUME, BigDecimal.valueOf(236.5882365), "cup", "cups");

        return Map.copyOf(units);
    }

    private static void register(Map<String, UnitDefinition> units, UnitType type, BigDecimal factorToBaseUnit, String... aliases) {
        UnitDefinition definition = new UnitDefinition(type, factorToBaseUnit);
        for (String alias : aliases) {
            units.put(alias, definition);
        }
    }

    public boolean supports(String unit) {
        return lookup(unit) != null;
    }

    /**
     * True when both units are recognized and belong to the same unit type (e.g. both WEIGHT, or both VOLUME).
     * Custom/count units (pcs, box, ...) and cross-type pairs (e.g. lb to ml) return false.
     */
    public boolean canConvert(String fromUnit, String toUnit) {
        UnitDefinition from = lookup(fromUnit);
        UnitDefinition to = lookup(toUnit);
        return from != null && to != null && from.type() == to.type();
    }

    public BigDecimal convert(BigDecimal quantity, String fromUnit, String toUnit) {
        if (quantity == null) {
            throw new IllegalArgumentException("Quantity to convert must not be null");
        }

        UnitDefinition from = requireUnit(fromUnit);
        UnitDefinition to = requireUnit(toUnit);
        if (from.type() != to.type()) {
            throw new IllegalArgumentException(
                    "Cannot convert '" + fromUnit + "' (" + from.type() + ") to '" + toUnit + "' (" + to.type()
                            + "); units must be the same type");
        }

        BigDecimal baseAmount = quantity.multiply(from.factorToBaseUnit());
        return baseAmount.divide(to.factorToBaseUnit(), SCALE, RoundingMode.HALF_UP);
    }

    private UnitDefinition requireUnit(String unit) {
        if (unit == null) {
            throw new IllegalArgumentException("Unit must not be null");
        }
        UnitDefinition definition = lookup(unit);
        if (definition == null) {
            throw new IllegalArgumentException(
                    "Unsupported unit for conversion: '" + unit + "'. Supported units: "
                            + "weight (mg, g, kg, lb), volume (ml, l, pint, oz, tsp, tbsp, cup)");
        }
        return definition;
    }

    private UnitDefinition lookup(String unit) {
        return unit == null ? null : UNITS.get(unit.trim().toLowerCase());
    }
}
