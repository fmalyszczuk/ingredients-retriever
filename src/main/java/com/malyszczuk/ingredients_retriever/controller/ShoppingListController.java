package com.malyszczuk.ingredients_retriever.controller;

import com.malyszczuk.ingredients_retriever.domain.ShoppingListItem;
import com.malyszczuk.ingredients_retriever.dto.AddShoppingListItemRequest;
import com.malyszczuk.ingredients_retriever.dto.ShoppingListItemResponse;
import com.malyszczuk.ingredients_retriever.dto.UpdateShoppingListItemRequest;
import com.malyszczuk.ingredients_retriever.service.ShoppingListService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/shopping-list")
@RequiredArgsConstructor
public class ShoppingListController {

    private final ShoppingListService shoppingListService;

    @GetMapping
    public List<ShoppingListItemResponse> listItems() {
        return shoppingListService.listItems().stream()
                .map(this::toResponse)
                .toList();
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public ShoppingListItemResponse addItem(@Valid @RequestBody AddShoppingListItemRequest request) {
        ShoppingListItem item = shoppingListService.addItem(request.name(), request.quantity(), request.unit());
        return toResponse(item);
    }

    @PatchMapping("/items/{name}")
    public ShoppingListItemResponse updateItem(@PathVariable String name, @RequestBody UpdateShoppingListItemRequest request) {
        ShoppingListItem item = shoppingListService.updateItem(name, request.purchased(), request.quantity(), request.unit());
        return toResponse(item);
    }

    @DeleteMapping("/items/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeItem(@PathVariable String name) {
        shoppingListService.removeItem(name);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearItems() {
        shoppingListService.clearItems();
    }

    private ShoppingListItemResponse toResponse(ShoppingListItem item) {
        return new ShoppingListItemResponse(
                item.getId(),
                item.getName(),
                item.getQuantity(),
                item.getUnit(),
                item.isPurchased()
        );
    }
}
