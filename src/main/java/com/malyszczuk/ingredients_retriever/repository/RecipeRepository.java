package com.malyszczuk.ingredients_retriever.repository;

import com.malyszczuk.ingredients_retriever.domain.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Always look recipes up through the owner: there is deliberately no way to fetch one by id alone. */
public interface RecipeRepository extends JpaRepository<Recipe, Long> {

    List<Recipe> findAllByUserIdOrderById(Long userId);

    Optional<Recipe> findByIdAndUserId(Long id, Long userId);
}
