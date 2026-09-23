package com.malyszczuk.ingredients_retriever.agent.tools;

import com.malyszczuk.ingredients_retriever.domain.ShoppingListItem;
import com.malyszczuk.ingredients_retriever.service.ShoppingListService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateItemToolTest {

    @Mock
    private ShoppingListService shoppingListService;

    private UpdateItemTool updateItemTool;

    @BeforeEach
    void setUp() {
        updateItemTool = new UpdateItemTool(shoppingListService);
    }

    @Test
    void execute_convertsArgumentsAndDelegatesToService() {
        ShoppingListItem saved = ShoppingListItem.builder()
                .name("flour").quantity(BigDecimal.valueOf(500)).unit("g").purchased(true).build();
        when(shoppingListService.updateItem("flour", true, BigDecimal.valueOf(500), "g")).thenReturn(saved);

        Object result = updateItemTool.execute(Map.of("name", "flour", "quantity", 500, "unit", "g", "purchased", true));

        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("name", "flour");
        expected.put("quantity", BigDecimal.valueOf(500));
        expected.put("unit", "g");
        expected.put("purchased", true);
        assertEquals(expected, result);
    }

    @Test
    void execute_handlesOnlyNameGiven() {
        ShoppingListItem saved = ShoppingListItem.builder().name("eggs").quantity(null).purchased(false).build();
        when(shoppingListService.updateItem("eggs", null, null, null)).thenReturn(saved);

        Object result = updateItemTool.execute(Map.of("name", "eggs"));

        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("name", "eggs");
        expected.put("quantity", null);
        expected.put("unit", null);
        expected.put("purchased", false);
        assertEquals(expected, result);
    }
}
