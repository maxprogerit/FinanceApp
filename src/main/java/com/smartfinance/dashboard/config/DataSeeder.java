package com.smartfinance.dashboard.config;

import com.smartfinance.dashboard.model.IncomeSource;
import com.smartfinance.dashboard.model.StorageType;
import com.smartfinance.dashboard.repository.IncomeSourceRepository;
import com.smartfinance.dashboard.repository.StorageTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

    private final StorageTypeRepository storageTypeRepository;
    private final IncomeSourceRepository incomeSourceRepository;

    @Override
    public void run(ApplicationArguments args) {
        seedStorageTypes();
        seedIncomeSources();
    }

    private void seedStorageTypes() {
        if (storageTypeRepository.count() > 0) return;
        List.of(
            new StorageType(null, "Cash",         "💵", true),
            new StorageType(null, "Bank Card",    "💳", true),
            new StorageType(null, "Bank Account", "🏦", true),
            new StorageType(null, "Savings",      "💰", true),
            new StorageType(null, "Crypto",       "₿",  true)
        ).forEach(storageTypeRepository::save);
    }

    private void seedIncomeSources() {
        if (incomeSourceRepository.count() > 0) return;
        List.of(
            new IncomeSource(null, "Salary",      "💼", true),
            new IncomeSource(null, "Freelance",   "🖥️", true),
            new IncomeSource(null, "Reselling",   "🛍️", true),
            new IncomeSource(null, "Investments", "📈", true)
        ).forEach(incomeSourceRepository::save);
    }
}
