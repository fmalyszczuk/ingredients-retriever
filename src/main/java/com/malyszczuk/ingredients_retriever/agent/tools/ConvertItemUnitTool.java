package com.malyszczuk.ingredients_retriever.agent.tools;

import com.malyszczuk.ingredients_retriever.domain.ShoppingListItem;
import com.malyszczuk.ingredients_retriever.service.ShoppingListService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ConvertItemUnitTool implements AgentTool {

    private final ShoppingListService shoppingListService;

    @Override
    public String name() {
        return "convert_item_unit";
    }

    @Override
    public String description() {
        return "Recalculates a shopping list item's quantity into a different mass unit (mg, g, kg, oz, lb) "
                + "and updates the item, e.g. converting lbs to kg or g.";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "name", Map.of("type", "string", "description", "Ingredient name on the shopping list, e.g. 'flour'"),
                        "target_unit", Map.of("type", "string", "description", "Unit to convert the quantity to, e.g. 'kg' or 'g'")
                ),
                "required", List.of("name", "target_unit")
        );
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        String name = (String) arguments.get("name");
        String targetUnit = (String) arguments.get("target_unit");

        ShoppingListItem item = shoppingListService.convertItemUnit(name, targetUnit);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", item.getName());
        result.put("quantity", item.getQuantity());
        result.put("unit", item.getUnit());
        return result;
    }
}
