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
public class AddItemTool implements AgentTool {

    private final ShoppingListService shoppingListService;

    @Override
    public String name() {
        return "add_item";
    }

    @Override
    public String description() {
        return "Adds an ingredient to the shopping list, merging its quantity into any existing entry with the same name.";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "name", Map.of("type", "string", "description", "Ingredient name, e.g. 'eggs'"),
                        "quantity", Map.of("type", "number", "description", "Amount to add, e.g. 2"),
                        "unit", Map.of("type", "string", "description", "Unit of measurement, e.g. 'pcs' or 'g'")
                ),
                "required", List.of("name")
        );
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        String name = (String) arguments.get("name");
        BigDecimal quantity = toBigDecimal(arguments.get("quantity"));
        String unit = (String) arguments.get("unit");

        ShoppingListItem item = shoppingListService.addItem(name, quantity, unit);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", item.getName());
        result.put("quantity", item.getQuantity());
        result.put("unit", item.getUnit());
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
}
