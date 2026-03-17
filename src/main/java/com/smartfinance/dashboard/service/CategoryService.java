package com.smartfinance.dashboard.service;

import com.smartfinance.dashboard.model.Category;
import com.smartfinance.dashboard.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {
    
    private final CategoryRepository categoryRepository;
    
    @Transactional
    public Category createCategory(Category category) {
        return categoryRepository.save(category);
    }
    
    @Transactional
    public Category updateCategory(Long id, Category category) {
        Category existing = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found"));
        
        existing.setName(category.getName());
        existing.setType(category.getType());
        existing.setIcon(category.getIcon());
        existing.setColor(category.getColor());
        existing.setDescription(category.getDescription());
        existing.setIsActive(category.getIsActive());
        
        return categoryRepository.save(existing);
    }
    
    @Transactional
    public void deleteCategory(Long id) {
        categoryRepository.deleteById(id);
    }
    
    public Category getCategoryById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found"));
    }
    
    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }
    
    public List<Category> getCategoriesByType(String type) {
        return categoryRepository.findByTypeAndIsActive(type, true);
    }
    
    public List<Category> getActiveCategories() {
        return categoryRepository.findByIsActive(true);
    }
    
    @Transactional
    public void initializeDefaultCategories() {
        // Expense categories
        createDefaultCategory("Food & Dining", "EXPENSE", "🍔", "#FF6B6B");
        createDefaultCategory("Transportation", "EXPENSE", "🚗", "#4ECDC4");
        createDefaultCategory("Housing", "EXPENSE", "🏠", "#95E1D3");
        createDefaultCategory("Utilities", "EXPENSE", "💡", "#F38181");
        createDefaultCategory("Healthcare", "EXPENSE", "🏥", "#AA96DA");
        createDefaultCategory("Entertainment", "EXPENSE", "🎬", "#FCBAD3");
        createDefaultCategory("Shopping", "EXPENSE", "🛍️", "#FFFFD2");
        createDefaultCategory("Education", "EXPENSE", "📚", "#A8D8EA");
        createDefaultCategory("Insurance", "EXPENSE", "🛡️", "#FFAAA5");
        createDefaultCategory("Others", "EXPENSE", "📦", "#CCCCCC");

        // Income categories
        createDefaultCategory("Salary", "INCOME", "💼", "#51CF66");
        createDefaultCategory("Freelance", "INCOME", "💻", "#74C0FC");
        createDefaultCategory("Investment Returns", "INCOME", "📈", "#FFD93D");
        createDefaultCategory("Business", "INCOME", "🏢", "#6BCF7F");
        createDefaultCategory("Other Income", "INCOME", "💰", "#95D5B2");
    }

    private void createDefaultCategory(String name, String type, String icon, String color) {
        if (categoryRepository.findByName(name).isPresent()) {
            return;
        }
        Category category = new Category();
        category.setName(name);
        category.setType(type);
        category.setIcon(icon);
        category.setColor(color);
        category.setIsDefault(true);
        category.setIsActive(true);
        categoryRepository.save(category);
    }
}
