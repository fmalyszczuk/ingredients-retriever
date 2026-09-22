package com.malyszczuk.ingredients_retriever.agent.tools;

import com.malyszczuk.ingredients_retriever.service.ShoppingListService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class RemoveItemTool implements AgentTool {

    private final ShoppingListService shoppingListService;

    @Override
    public String name() {
        return "remove_item";
    }

    @Override
    public String description() {
        return "Removes an ingredient from the shopping list by name.";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "name", Map.of("type", "string", "description", "Ingredient name to remove, e.g. 'eggs'")
                ),
                "required", List.of("name")
        );
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        String name = (String) arguments.get("name");
        shoppingListService.removeItem(name);
        return Map.of("removed", name);
    }
}
