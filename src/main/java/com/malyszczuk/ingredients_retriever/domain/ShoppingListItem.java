package com.malyszczuk.ingredients_retriever.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "shopping_list_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ShoppingListItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    /** The owner. Every query is scoped by it, see ShoppingListItemRepository. */
    @Column(nullable = false, updatable = false)
    private Long userId;

    // Uniqueness (per user, ignoring case) is enforced by a database index, see db/migration/V4__own_data_per_user.sql.
    @Column(nullable = false)
    private String name;

    @Builder.Default
    @Column(precision = 12, scale = 3)
    private BigDecimal quantity = BigDecimal.ZERO;

    private String unit;

    @Builder.Default
    private boolean purchased = false;
}
