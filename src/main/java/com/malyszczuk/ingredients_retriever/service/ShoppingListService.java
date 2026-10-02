package com.malyszczuk.ingredients_retriever.service;

import com.malyszczuk.ingredients_retriever.domain.Ingredient;
import com.malyszczuk.ingredients_retriever.domain.ShoppingListItem;
import com.malyszczuk.ingredients_retriever.repository.ShoppingListItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ShoppingListService {

    private final ShoppingListItemRepository shoppingListItemRepository;
    private final UnitConverter unitConverter;

    @Transactional(readOnly = true)
    public List<ShoppingListItem> listItems() {
        return shoppingListItemRepository.findAll();
    }

    @Transactional
    public List<ShoppingListItem> addIngredients(List<Ingredient> ingredients) {
        return ingredients.stream()
                .map(ingredient -> addItem(ingredient.getName(), ingredient.getQuantity(), ingredient.getUnit()))
                .toList();
    }

    @Transactional
    public ShoppingListItem addItem(String name, BigDecimal quantity, String unit) {
        String normalizedName = normalize(name);
        if (normalizedName == null || normalizedName.isEmpty()) {
            throw new IllegalArgumentException("Ingredient name must not be blank");
        }

        String normalizedUnit = normalize(unit);
        Optional<ShoppingListItem> existing = shoppingListItemRepository.findByNameIgnoreCase(normalizedName);

        if (existing.isEmpty()) {
            return shoppingListItemRepository.save(ShoppingListItem.builder()
                    .name(normalizedName)
                    .quantity(quantity)
                    .unit(normalizedUnit)
                    .build());
        }

        ShoppingListItem item = existing.get();
        if (quantity == null || item.getQuantity() == null) {
            item.setQuantity(null);
            if (item.getUnit() == null) {
                item.setUnit(normalizedUnit);
            }
            return shoppingListItemRepository.save(item);
        }

        if (!sameKind(item.getUnit(), normalizedUnit)) {
            // One row holds one quantity in one unit, so amounts that can't be added (e.g. pcs vs g) live in a
            // separate row instead of being summed into nonsense or dropped.
            return addItem(variantName(normalizedName, normalizedUnit), quantity, normalizedUnit);
        }

        item.setQuantity(item.getQuantity().add(amountInUnitOf(item, quantity, normalizedUnit)));
        if (item.getUnit() == null) {
            item.setUnit(normalizedUnit);
        }
        return shoppingListItemRepository.save(item);
    }

    @Transactional
    public ShoppingListItem updateItem(String name, Boolean purchased, BigDecimal quantity, String unit) {
        ShoppingListItem item = shoppingListItemRepository.findByNameIgnoreCase(normalize(name))
                .orElseThrow(() -> new NoSuchElementException("No shopping list item named '" + name + "'"));

        if (purchased != null) {
            item.setPurchased(purchased);
        }

        String normalizedUnit = normalize(unit);
        if (quantity != null) {
            // Caller supplied an explicit quantity: take both values as-is, no conversion.
            item.setQuantity(quantity);
            if (normalizedUnit != null) {
                item.setUnit(normalizedUnit);
            }
        } else if (normalizedUnit != null && !normalizedUnit.equalsIgnoreCase(item.getUnit())) {
            // Only the unit changed: recalculate the quantity instead of relabelling it 1:1,
            // but only within the same unit type (weight-to-weight, volume-to-volume).
            if (item.getQuantity() != null && unitConverter.canConvert(item.getUnit(), normalizedUnit)) {
                item.setQuantity(unitConverter.convert(item.getQuantity(), item.getUnit(), normalizedUnit));
            }
            item.setUnit(normalizedUnit);
        }

        return shoppingListItemRepository.save(item);
    }

    @Transactional
    public ShoppingListItem convertItemUnit(String name, String targetUnit) {
        ShoppingListItem item = shoppingListItemRepository.findByNameIgnoreCase(normalize(name))
                .orElseThrow(() -> new NoSuchElementException("No shopping list item named '" + name + "'"));

        if (item.getQuantity() == null || item.getUnit() == null) {
            throw new IllegalArgumentException(
                    "Cannot convert unit for '" + name + "' without an existing quantity and unit");
        }

        String normalizedTargetUnit = normalize(targetUnit);
        BigDecimal convertedQuantity = unitConverter.convert(item.getQuantity(), item.getUnit(), normalizedTargetUnit);

        item.setQuantity(convertedQuantity);
        item.setUnit(normalizedTargetUnit);

        return shoppingListItemRepository.save(item);
    }

    @Transactional
    public void removeItem(String name) {
        shoppingListItemRepository.findByNameIgnoreCase(normalize(name))
                .ifPresent(shoppingListItemRepository::delete);
    }

    /**
     * Reverses {@link #addIngredients}: subtracts each ingredient's quantity from the matching list item, and drops
     * the item once nothing is left. Ingredients that can't be subtracted safely are left alone: no matching item
     * (in the same unit family, see {@link #findMatching}) or an unspecified quantity on either side.
     */
    @Transactional
    public void removeIngredients(List<Ingredient> ingredients) {
        for (Ingredient ingredient : ingredients) {
            String unit = normalize(ingredient.getUnit());
            findMatching(normalize(ingredient.getName()), unit)
                    .ifPresent(item -> subtract(item, ingredient.getQuantity(), unit));
        }
    }

    private void subtract(ShoppingListItem item, BigDecimal quantity, String unit) {
        if (item.getQuantity() == null || quantity == null) {
            return;
        }

        BigDecimal remaining = item.getQuantity().subtract(amountInUnitOf(item, quantity, unit));
        if (remaining.signum() <= 0) {
            shoppingListItemRepository.delete(item);
        } else {
            item.setQuantity(remaining);
            shoppingListItemRepository.save(item);
        }
    }

    /** The row {@link #addItem} would have merged an ingredient with this name and unit into, if there is one. */
    private Optional<ShoppingListItem> findMatching(String name, String unit) {
        Optional<ShoppingListItem> primary = shoppingListItemRepository.findByNameIgnoreCase(name);
        if (primary.isPresent() && sameKind(primary.get().getUnit(), unit)) {
            return primary;
        }
        return shoppingListItemRepository.findByNameIgnoreCase(variantName(name, unit))
                .filter(variant -> sameKind(variant.getUnit(), unit));
    }

    @Transactional
    public void clearItems() {
        shoppingListItemRepository.deleteAll();
    }

    /** Two units can share a row when one converts to the other (same weight/volume family) or both count the same thing. */
    private boolean sameKind(String itemUnit, String unit) {
        return unitConverter.canConvert(itemUnit, unit) || countUnit(itemUnit).equals(countUnit(unit));
    }

    /** The amount expressed in the item's own unit; only converts when the units differ but are the same family. */
    private BigDecimal amountInUnitOf(ShoppingListItem item, BigDecimal quantity, String unit) {
        if (unitConverter.canConvert(unit, item.getUnit()) && !countUnit(unit).equals(countUnit(item.getUnit()))) {
            return unitConverter.convert(quantity, unit, item.getUnit());
        }
        return quantity;
    }

    /** Name of the extra row for amounts that can't be added to the main one, e.g. "onion (weight)". */
    private String variantName(String name, String unit) {
        UnitConverter.UnitType type = unitConverter.typeOf(unit);
        String kind = type != null ? type.name().toLowerCase(Locale.ROOT) : countUnit(unit);
        return name + " (" + kind + ")";
    }

    // No unit means "pieces", and singular/plural spellings of a unit are the same unit.
    private String countUnit(String unit) {
        if (unit == null || unit.isBlank()) {
            return "pcs";
        }
        String lower = unit.trim().toLowerCase(Locale.ROOT);
        if (lower.equals("pc") || lower.equals("piece") || lower.equals("pieces")) {
            return "pcs";
        }
        boolean plural = lower.length() > 3 && lower.endsWith("s") && !lower.endsWith("ss");
        return plural ? lower.substring(0, lower.length() - 1) : lower;
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
