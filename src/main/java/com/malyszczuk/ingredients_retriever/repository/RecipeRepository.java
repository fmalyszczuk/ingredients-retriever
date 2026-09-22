package com.malyszczuk.ingredients_retriever.repository;

import com.malyszczuk.ingredients_retriever.domain.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {
}
