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
class AddItemToolTest {

    @Mock
    private ShoppingListService shoppingListService;

    private AddItemTool addItemTool;

    @BeforeEach
    void setUp() {
        addItemTool = new AddItemTool(shoppingListService);
    }

    @Test
    void execute_convertsNumericArgumentAndDelegatesToService() {
        ShoppingListItem saved = ShoppingListItem.builder().name("eggs").quantity(BigDecimal.valueOf(2)).unit("pcs").build();
        when(shoppingListService.addItem("eggs", BigDecimal.valueOf(2), "pcs")).thenReturn(saved);

        Object result = addItemTool.execute(Map.of("name", "eggs", "quantity", 2, "unit", "pcs"));

        assertEquals(Map.of("name", "eggs", "quantity", BigDecimal.valueOf(2), "unit", "pcs"), result);
    }

    @Test
    void execute_handlesMissingQuantityAndUnit() {
        ShoppingListItem saved = ShoppingListItem.builder().name("salt").quantity(null).build();
        when(shoppingListService.addItem("salt", null, null)).thenReturn(saved);

        Object result = addItemTool.execute(Map.of("name", "salt"));

        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("name", "salt");
        expected.put("quantity", null);
        expected.put("unit", null);
        assertEquals(expected, result);
    }
}
