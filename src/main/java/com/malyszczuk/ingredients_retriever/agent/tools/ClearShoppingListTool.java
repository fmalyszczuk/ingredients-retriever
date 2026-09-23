package com.malyszczuk.ingredients_retriever.agent.tools;

import com.malyszczuk.ingredients_retriever.service.ShoppingListService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class ClearShoppingListTool implements AgentTool {

    private final ShoppingListService shoppingListService;

    @Override
    public String name() {
        return "clear_shopping_list";
    }

    @Override
    public String description() {
        return "Removes every item from the shopping list. This cannot be undone, "
                + "so only call it when the user clearly asks to clear, empty, or reset the whole list.";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of("type", "object", "properties", Map.of());
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        shoppingListService.clearItems();
        return Map.of("cleared", true);
    }
}
