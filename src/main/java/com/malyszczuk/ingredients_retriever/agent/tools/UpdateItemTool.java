package com.malyszczuk.ingredients_retriever.agent.tools;

import com.malyszczuk.ingredients_retriever.domain.ShoppingListItem;
import com.malyszczuk.ingredients_retriever.service.ShoppingListService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class UpdateItemTool implements AgentTool {

    private final ShoppingListService shoppingListService;

    @Override
    public String name() {
        return "update_item";
    }

    @Override
    public String description() {
        return "Updates a shopping list item's quantity, unit, and/or purchased status by name. "
                + "Only the fields provided are changed; omitted fields are left as-is.";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "name", Map.of("type", "string", "description", "Ingredient name to update, e.g. 'eggs'"),
                        "quantity", Map.of("type", "number", "description", "New quantity, e.g. 500"),
                        "unit", Map.of("type", "string", "description", "New unit of measurement, e.g. 'kg'"),
                        "purchased", Map.of("type", "boolean", "description", "Whether the item has been purchased")
                ),
                "required", List.of("name")
        );
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        String name = (String) arguments.get("name");
        Boolean purchased = toBoolean(arguments.get("purchased"));
        BigDecimal quantity = toBigDecimal(arguments.get("quantity"));
        String unit = (String) arguments.get("unit");

        ShoppingListItem item = shoppingListService.updateItem(name, purchased, quantity, unit);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", item.getName());
        result.put("quantity", item.getQuantity());
        result.put("unit", item.getUnit());
        result.put("purchased", item.isPurchased());
        return result;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        return new BigDecimal(value.toString());
    }

    private Boolean toBoolean(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(value.toString());
    }
}
