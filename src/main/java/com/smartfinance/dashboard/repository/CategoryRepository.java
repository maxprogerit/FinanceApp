package com.smartfinance.dashboard.repository;

import com.smartfinance.dashboard.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    
    Optional<Category> findByName(String name);
    
    List<Category> findByType(String type);
    
    List<Category> findByIsActive(Boolean isActive);
    
    List<Category> findByTypeAndIsActive(String type, Boolean isActive);
    
    List<Category> findByIsDefault(Boolean isDefault);
}
