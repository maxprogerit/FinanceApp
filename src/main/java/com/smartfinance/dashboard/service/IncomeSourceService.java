package com.smartfinance.dashboard.service;

import com.smartfinance.dashboard.model.IncomeSource;
import com.smartfinance.dashboard.repository.IncomeSourceRepository;
import com.smartfinance.dashboard.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class IncomeSourceService {

    private final IncomeSourceRepository incomeSourceRepository;
    private final TransactionRepository transactionRepository;

    public List<IncomeSource> getAll() {
        return incomeSourceRepository.findAll();
    }

    public IncomeSource create(IncomeSource incomeSource) {
        if (incomeSourceRepository.existsByName(incomeSource.getName())) {
            throw new IllegalArgumentException("Income source with this name already exists.");
        }
        incomeSource.setBuiltIn(false);
        return incomeSourceRepository.save(incomeSource);
    }

    public IncomeSource update(Long id, IncomeSource updated) {
        IncomeSource existing = incomeSourceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Income source not found"));
        if (existing.isBuiltIn()) {
            throw new IllegalArgumentException("Cannot rename a built-in income source.");
        }
        if (!existing.getName().equals(updated.getName())
                && incomeSourceRepository.existsByName(updated.getName())) {
            throw new IllegalArgumentException("Income source with this name already exists.");
        }
        existing.setName(updated.getName());
        existing.setIcon(updated.getIcon());
        return incomeSourceRepository.save(existing);
    }

    public void delete(Long id) {
        IncomeSource existing = incomeSourceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Income source not found"));
        if (existing.isBuiltIn()) {
            throw new IllegalArgumentException("Cannot delete a built-in income source.");
        }
        long usageCount = transactionRepository.countByIncomeSource(existing.getName());
        if (usageCount > 0) {
            throw new IllegalStateException(
                "Cannot delete: " + usageCount + " transaction(s) use this income source.");
        }
        incomeSourceRepository.deleteById(id);
    }
}
