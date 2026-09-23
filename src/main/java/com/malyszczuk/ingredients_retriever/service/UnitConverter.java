package com.malyszczuk.ingredients_retriever.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

@Component
public class UnitConverter {

    private static final int SCALE = 3;

    private static final Map<String, BigDecimal> GRAMS_PER_UNIT = Map.ofEntries(
            Map.entry("mg", BigDecimal.valueOf(0.001)),
            Map.entry("milligram", BigDecimal.valueOf(0.001)),
            Map.entry("milligrams", BigDecimal.valueOf(0.001)),
            Map.entry("g", BigDecimal.ONE),
            Map.entry("gram", BigDecimal.ONE),
            Map.entry("grams", BigDecimal.ONE),
            Map.entry("kg", BigDecimal.valueOf(1000)),
            Map.entry("kilogram", BigDecimal.valueOf(1000)),
            Map.entry("kilograms", BigDecimal.valueOf(1000)),
            Map.entry("oz", BigDecimal.valueOf(28.349523125)),
            Map.entry("ounce", BigDecimal.valueOf(28.349523125)),
            Map.entry("ounces", BigDecimal.valueOf(28.349523125)),
            Map.entry("lb", BigDecimal.valueOf(453.59237)),
            Map.entry("lbs", BigDecimal.valueOf(453.59237)),
            Map.entry("pound", BigDecimal.valueOf(453.59237)),
            Map.entry("pounds", BigDecimal.valueOf(453.59237))
    );

    public boolean supports(String unit) {
        return unit != null && GRAMS_PER_UNIT.containsKey(unit.trim().toLowerCase());
    }

    public BigDecimal convert(BigDecimal quantity, String fromUnit, String toUnit) {
        if (quantity == null) {
            throw new IllegalArgumentException("Quantity to convert must not be null");
        }

        BigDecimal grams = quantity.multiply(gramsPerUnit(fromUnit));
        return grams.divide(gramsPerUnit(toUnit), SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal gramsPerUnit(String unit) {
        if (unit == null) {
            throw new IllegalArgumentException("Unit must not be null");
        }

        BigDecimal factor = GRAMS_PER_UNIT.get(unit.trim().toLowerCase());
        if (factor == null) {
            throw new IllegalArgumentException(
                    "Unsupported unit for conversion: '" + unit + "'. Supported units: mg, g, kg, oz, lb");
        }
        return factor;
    }
}
