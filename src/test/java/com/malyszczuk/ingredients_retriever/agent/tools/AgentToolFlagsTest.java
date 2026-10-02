package com.malyszczuk.ingredients_retriever.agent.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentToolFlagsTest {

    @Test
    void toolsThatModifyTheShoppingListSayso() {
        assertTrue(new AddItemTool(null).changesShoppingList());
        assertTrue(new RemoveItemTool(null).changesShoppingList());
        assertTrue(new UpdateItemTool(null).changesShoppingList());
        assertTrue(new ConvertItemUnitTool(null).changesShoppingList());
        assertTrue(new ClearShoppingListTool(null).changesShoppingList());
        assertTrue(new AddRecipeTool(null).changesShoppingList());
        assertTrue(new DeleteRecipeTool(null).changesShoppingList());
    }

    @Test
    void readOnlyToolsDoNot() {
        assertFalse(new ListItemsTool(null).changesShoppingList());
        assertFalse(new ListRecipesTool(null).changesShoppingList());
    }

    @Test
    void onlyDestructiveToolsRequireConfirmation() {
        assertTrue(new ClearShoppingListTool(null).requiresConfirmation());
        assertTrue(new DeleteRecipeTool(null).requiresConfirmation());
        assertFalse(new AddRecipeTool(null).requiresConfirmation());
        assertFalse(new ListRecipesTool(null).requiresConfirmation());
        assertFalse(new AddItemTool(null).requiresConfirmation());
        assertFalse(new RemoveItemTool(null).requiresConfirmation());
        assertFalse(new UpdateItemTool(null).requiresConfirmation());
        assertFalse(new ConvertItemUnitTool(null).requiresConfirmation());
        assertFalse(new ListItemsTool(null).requiresConfirmation());
    }
}
