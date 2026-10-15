package com.malyszczuk.ingredients_retriever.repository;

import com.malyszczuk.ingredients_retriever.domain.ShoppingListItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ShoppingListItemRepository extends JpaRepository<ShoppingListItem, Long> {

    /**
     * Finds an item by name, ignoring case, and locks its row until the surrounding transaction ends, so two
     * requests changing the same item queue up instead of overwriting each other. Must be called inside a
     * transaction. Matches exactly what the unique index on lower(name) enforces.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from ShoppingListItem i where lower(i.name) = lower(:name)")
    Optional<ShoppingListItem> findByNameIgnoreCase(@Param("name") String name);
}
