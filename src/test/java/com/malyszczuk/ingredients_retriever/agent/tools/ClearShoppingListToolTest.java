package com.malyszczuk.ingredients_retriever.agent.tools;

import com.malyszczuk.ingredients_retriever.service.ShoppingListService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ClearShoppingListToolTest {

    @Mock
    private ShoppingListService shoppingListService;

    private ClearShoppingListTool clearShoppingListTool;

    @BeforeEach
    void setUp() {
        clearShoppingListTool = new ClearShoppingListTool(shoppingListService);
    }

    @Test
    void execute_clearsListAndReturnsConfirmation() {
        Object result = clearShoppingListTool.execute(Map.of());

        verify(shoppingListService).clearItems();
        assertEquals(Map.of("cleared", true), result);
    }
}
