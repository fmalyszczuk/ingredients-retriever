package com.malyszczuk.ingredients_retriever.controller;

import com.malyszczuk.ingredients_retriever.domain.ShoppingListItem;
import com.malyszczuk.ingredients_retriever.dto.AddShoppingListItemRequest;
import com.malyszczuk.ingredients_retriever.dto.UpdateShoppingListItemRequest;
import com.malyszczuk.ingredients_retriever.service.ShoppingListService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ShoppingListController.class)
class ShoppingListControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ShoppingListService shoppingListService;

    @Test
    void listItems_returnsCurrentShoppingList() throws Exception {
        ShoppingListItem item = ShoppingListItem.builder()
                .id(1L)
                .name("eggs")
                .quantity(BigDecimal.valueOf(5))
                .unit("pcs")
                .build();
        when(shoppingListService.listItems()).thenReturn(List.of(item));

        mockMvc.perform(get("/shopping-list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("eggs"))
                .andExpect(jsonPath("$[0].quantity").value(5))
                .andExpect(jsonPath("$[0].unit").value("pcs"));
    }

    @Test
    void addItem_returnsCreatedItem() throws Exception {
        AddShoppingListItemRequest request = new AddShoppingListItemRequest("flour", BigDecimal.valueOf(200), "g");
        ShoppingListItem saved = ShoppingListItem.builder()
                .id(2L)
                .name("flour")
                .quantity(BigDecimal.valueOf(200))
                .unit("g")
                .build();
        when(shoppingListService.addItem("flour", BigDecimal.valueOf(200), "g")).thenReturn(saved);

        mockMvc.perform(post("/shopping-list/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.name").value("flour"));
    }

    @Test
    void addItem_rejectsBlankName() throws Exception {
        AddShoppingListItemRequest request = new AddShoppingListItemRequest(" ", BigDecimal.ONE, "pcs");

        mockMvc.perform(post("/shopping-list/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateItem_marksPurchased_andReturnsUpdatedItem() throws Exception {
        UpdateShoppingListItemRequest request = new UpdateShoppingListItemRequest(true, null, null);
        ShoppingListItem updated = ShoppingListItem.builder()
                .id(1L)
                .name("eggs")
                .quantity(BigDecimal.valueOf(2))
                .unit("pcs")
                .purchased(true)
                .build();
        when(shoppingListService.updateItem(eq("eggs"), eq(true), isNull(), isNull())).thenReturn(updated);

        mockMvc.perform(patch("/shopping-list/items/eggs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purchased").value(true));
    }

    @Test
    void updateItem_returnsNotFound_whenItemDoesNotExist() throws Exception {
        UpdateShoppingListItemRequest request = new UpdateShoppingListItemRequest(true, null, null);
        when(shoppingListService.updateItem(eq("missing"), eq(true), isNull(), isNull()))
                .thenThrow(new NoSuchElementException("No shopping list item named 'missing'"));

        mockMvc.perform(patch("/shopping-list/items/missing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void removeItem_returnsNoContent() throws Exception {
        mockMvc.perform(delete("/shopping-list/items/eggs"))
                .andExpect(status().isNoContent());

        verify(shoppingListService).removeItem("eggs");
    }

    @Test
    void clearItems_returnsNoContent() throws Exception {
        mockMvc.perform(delete("/shopping-list"))
                .andExpect(status().isNoContent());

        verify(shoppingListService).clearItems();
    }
}
