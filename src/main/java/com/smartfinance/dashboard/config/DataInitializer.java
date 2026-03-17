package com.smartfinance.dashboard.config;

import com.smartfinance.dashboard.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {
    
    private final CategoryService categoryService;
    
    @Override
    public void run(String... args) throws Exception {
        categoryService.initializeDefaultCategories();
        System.out.println("✅ Default categories initialized");
    }
}
