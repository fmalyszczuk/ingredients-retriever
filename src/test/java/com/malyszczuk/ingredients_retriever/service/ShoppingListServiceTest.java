package com.malyszczuk.ingredients_retriever.service;

import com.malyszczuk.ingredients_retriever.domain.ShoppingListItem;
import com.malyszczuk.ingredients_retriever.repository.ShoppingListItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShoppingListServiceTest {

    @Mock
    private ShoppingListItemRepository shoppingListItemRepository;

    private ShoppingListService shoppingListService;

    @BeforeEach
    void setUp() {
        shoppingListService = new ShoppingListService(shoppingListItemRepository, new UnitConverter());
    }

    @Test
    void addItem_createsNewEntry_whenNoneExists() {
        when(shoppingListItemRepository.findByNameIgnoreCase("eggs")).thenReturn(Optional.empty());
        when(shoppingListItemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ShoppingListItem result = shoppingListService.addItem("eggs", BigDecimal.valueOf(2), "pcs");

        assertEquals("eggs", result.getName());
        assertEquals(0, BigDecimal.valueOf(2).compareTo(result.getQuantity()));
        assertEquals("pcs", result.getUnit());
    }

    @Test
    void addItem_sumsQuantities_whenEntryAlreadyExists() {
        ShoppingListItem existing = ShoppingListItem.builder()
                .name("eggs")
                .quantity(BigDecimal.valueOf(2))
                .unit("pcs")
                .build();
        when(shoppingListItemRepository.findByNameIgnoreCase("eggs")).thenReturn(Optional.of(existing));
        when(shoppingListItemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ShoppingListItem result = shoppingListService.addItem("eggs", BigDecimal.valueOf(3), "pcs");

        assertEquals(0, BigDecimal.valueOf(5).compareTo(result.getQuantity()));
    }

    @Test
    void addItem_resultsInUnspecifiedQuantity_whenEitherSideIsUnspecified() {
        ShoppingListItem existing = ShoppingListItem.builder()
                .name("salt")
                .quantity(null)
                .build();
        when(shoppingListItemRepository.findByNameIgnoreCase("salt")).thenReturn(Optional.of(existing));
        when(shoppingListItemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ShoppingListItem result = shoppingListService.addItem("salt", BigDecimal.valueOf(1), "tsp");

        assertNull(result.getQuantity());
    }

    @Test
    void addItem_rejectsBlankName() {
        assertThrows(IllegalArgumentException.class,
                () -> shoppingListService.addItem("  ", BigDecimal.ONE, "pcs"));
    }

    @Test
    void updateItem_marksPurchased_whenOnlyPurchasedGiven() {
        ShoppingListItem existing = ShoppingListItem.builder()
                .name("eggs")
                .quantity(BigDecimal.valueOf(2))
                .unit("pcs")
                .purchased(false)
                .build();
        when(shoppingListItemRepository.findByNameIgnoreCase("eggs")).thenReturn(Optional.of(existing));
        when(shoppingListItemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ShoppingListItem result = shoppingListService.updateItem("eggs", true, null, null);

        assertTrue(result.isPurchased());
        assertEquals(0, BigDecimal.valueOf(2).compareTo(result.getQuantity()));
        assertEquals("pcs", result.getUnit());
    }

    @Test
    void updateItem_updatesQuantityAndUnit_whenGiven() {
        ShoppingListItem existing = ShoppingListItem.builder()
                .name("flour")
                .quantity(BigDecimal.valueOf(200))
                .unit("g")
                .build();
        when(shoppingListItemRepository.findByNameIgnoreCase("flour")).thenReturn(Optional.of(existing));
        when(shoppingListItemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ShoppingListItem result = shoppingListService.updateItem("flour", null, BigDecimal.valueOf(500), "kg");

        assertEquals(0, BigDecimal.valueOf(500).compareTo(result.getQuantity()));
        assertEquals("kg", result.getUnit());
    }

    @Test
    void updateItem_throws_whenItemDoesNotExist() {
        when(shoppingListItemRepository.findByNameIgnoreCase("eggs")).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> shoppingListService.updateItem("eggs", true, null, null));
    }

    @Test
    void convertItemUnit_recalculatesQuantityAndUpdatesUnit() {
        ShoppingListItem existing = ShoppingListItem.builder()
                .name("flour")
                .quantity(BigDecimal.valueOf(2))
                .unit("lb")
                .build();
        when(shoppingListItemRepository.findByNameIgnoreCase("flour")).thenReturn(Optional.of(existing));
        when(shoppingListItemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ShoppingListItem result = shoppingListService.convertItemUnit("flour", "kg");

        assertEquals("kg", result.getUnit());
        assertEquals(0, BigDecimal.valueOf(0.907).compareTo(result.getQuantity()));
    }

    @Test
    void convertItemUnit_throws_whenItemDoesNotExist() {
        when(shoppingListItemRepository.findByNameIgnoreCase("flour")).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> shoppingListService.convertItemUnit("flour", "kg"));
    }

    @Test
    void convertItemUnit_throws_whenItemHasNoExistingQuantityOrUnit() {
        ShoppingListItem existing = ShoppingListItem.builder().name("salt").quantity(null).unit(null).build();
        when(shoppingListItemRepository.findByNameIgnoreCase("salt")).thenReturn(Optional.of(existing));

        assertThrows(IllegalArgumentException.class, () -> shoppingListService.convertItemUnit("salt", "kg"));
    }

    @Test
    void removeItem_deletesEntry_whenPresent() {
        ShoppingListItem existing = ShoppingListItem.builder().name("eggs").build();
        when(shoppingListItemRepository.findByNameIgnoreCase("eggs")).thenReturn(Optional.of(existing));

        shoppingListService.removeItem("eggs");

        verify(shoppingListItemRepository).delete(existing);
    }

    @Test
    void removeItem_doesNothing_whenAbsent() {
        when(shoppingListItemRepository.findByNameIgnoreCase("eggs")).thenReturn(Optional.empty());

        shoppingListService.removeItem("eggs");

        verify(shoppingListItemRepository, never()).delete(any());
    }
}
