package com.malyszczuk.ingredients_retriever.repository;

import com.malyszczuk.ingredients_retriever.domain.ShoppingListItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ShoppingListItemRepository extends JpaRepository<ShoppingListItem, Long> {

    Optional<ShoppingListItem> findByNameIgnoreCase(String name);
}
