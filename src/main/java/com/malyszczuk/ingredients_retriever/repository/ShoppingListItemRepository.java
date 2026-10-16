package com.malyszczuk.ingredients_retriever.repository;

import com.malyszczuk.ingredients_retriever.domain.ShoppingListItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** Every query is scoped to one owner, so one user can never see or change another user's list. */
public interface ShoppingListItemRepository extends JpaRepository<ShoppingListItem, Long> {

    List<ShoppingListItem> findAllByUserIdOrderById(Long userId);

    /**
     * Finds one of the user's items by name, ignoring case, and locks its row until the surrounding transaction ends,
     * so two requests changing the same item queue up instead of overwriting each other. Must be called inside a
     * transaction. Matches exactly what the unique index on (user_id, lower(name)) enforces.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from ShoppingListItem i where i.userId = :userId and lower(i.name) = lower(:name)")
    Optional<ShoppingListItem> findByUserIdAndNameIgnoreCase(@Param("userId") Long userId, @Param("name") String name);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ShoppingListItem i where i.userId = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
