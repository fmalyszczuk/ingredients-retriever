package com.malyszczuk.ingredients_retriever.service;

import com.malyszczuk.ingredients_retriever.domain.Ingredient;
import com.malyszczuk.ingredients_retriever.domain.ShoppingListItem;
import com.malyszczuk.ingredients_retriever.repository.ShoppingListItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
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

        Optional<ShoppingListItem> existing = shoppingListItemRepository.findByNameIgnoreCase(normalizedName);

        ShoppingListItem item;
        if (existing.isPresent()) {
            item = existing.get();
            item.setQuantity(sumQuantities(item.getQuantity(), quantity));
            if (item.getUnit() == null) {
                item.setUnit(normalize(unit));
            }
        } else {
            item = ShoppingListItem.builder()
                    .name(normalizedName)
                    .quantity(quantity)
                    .unit(normalize(unit))
                    .build();
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
     * the item once nothing is left. Ingredients that can't be subtracted safely are left alone: no matching item,
     * an unspecified quantity on either side, or units that are different and not convertible.
     */
    @Transactional
    public void removeIngredients(List<Ingredient> ingredients) {
        for (Ingredient ingredient : ingredients) {
            shoppingListItemRepository.findByNameIgnoreCase(normalize(ingredient.getName()))
                    .ifPresent(item -> subtract(item, ingredient));
        }
    }

    private void subtract(ShoppingListItem item, Ingredient ingredient) {
        if (item.getQuantity() == null || ingredient.getQuantity() == null) {
            return;
        }

        BigDecimal amount = ingredient.getQuantity();
        if (!sameUnit(item.getUnit(), ingredient.getUnit())) {
            if (!unitConverter.canConvert(ingredient.getUnit(), item.getUnit())) {
                return;
            }
            amount = unitConverter.convert(amount, ingredient.getUnit(), item.getUnit());
        }

        BigDecimal remaining = item.getQuantity().subtract(amount);
        if (remaining.signum() <= 0) {
            shoppingListItemRepository.delete(item);
        } else {
            item.setQuantity(remaining);
            shoppingListItemRepository.save(item);
        }
    }

    private boolean sameUnit(String first, String second) {
        return first == null ? second == null : first.equalsIgnoreCase(second);
    }

    @Transactional
    public void clearItems() {
        shoppingListItemRepository.deleteAll();
    }

    private BigDecimal sumQuantities(BigDecimal current, BigDecimal addition) {
        if (current == null || addition == null) {
            return null;
        }
        return current.add(addition);
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
