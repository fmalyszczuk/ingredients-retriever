package com.malyszczuk.ingredients_retriever.agent.tools;

import com.malyszczuk.ingredients_retriever.domain.ShoppingListItem;
import com.malyszczuk.ingredients_retriever.service.ShoppingListService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ListItemsTool implements AgentTool {

    private final ShoppingListService shoppingListService;

    @Override
    public String name() {
        return "list_items";
    }

    @Override
    public String description() {
        return "Lists every ingredient currently on the shopping list.";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of("type", "object", "properties", Map.of());
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        return shoppingListService.listItems().stream()
                .map(this::toResult)
                .toList();
    }

    private Map<String, Object> toResult(ShoppingListItem item) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", item.getName());
        result.put("quantity", item.getQuantity());
        result.put("unit", item.getUnit());
        result.put("purchased", item.isPurchased());
        return result;
    }
}
