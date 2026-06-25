package com.smartfridge.repository;

import com.smartfridge.entity.GeneratedRecipes;
import com.smartfridge.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RecipeRepository extends JpaRepository<GeneratedRecipes, Long> {
    Optional<GeneratedRecipes> findByUser(User user);
}
