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
class ConvertItemUnitToolTest {

    @Mock
    private ShoppingListService shoppingListService;

    private ConvertItemUnitTool convertItemUnitTool;

    @BeforeEach
    void setUp() {
        convertItemUnitTool = new ConvertItemUnitTool(shoppingListService);
    }

    @Test
    void execute_delegatesToServiceAndReturnsConvertedItem() {
        ShoppingListItem converted = ShoppingListItem.builder()
                .name("flour").quantity(BigDecimal.valueOf(0.907)).unit("kg").build();
        when(shoppingListService.convertItemUnit("flour", "kg")).thenReturn(converted);

        Object result = convertItemUnitTool.execute(Map.of("name", "flour", "target_unit", "kg"));

        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("name", "flour");
        expected.put("quantity", BigDecimal.valueOf(0.907));
        expected.put("unit", "kg");
        assertEquals(expected, result);
    }
}
