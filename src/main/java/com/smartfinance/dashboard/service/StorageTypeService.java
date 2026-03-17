package com.smartfinance.dashboard.service;

import com.smartfinance.dashboard.model.StorageType;
import com.smartfinance.dashboard.repository.StorageTypeRepository;
import com.smartfinance.dashboard.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StorageTypeService {

    private final StorageTypeRepository storageTypeRepository;
    private final TransactionRepository transactionRepository;

    public List<StorageType> getAll() {
        return storageTypeRepository.findAll();
    }

    public StorageType create(StorageType storageType) {
        if (storageTypeRepository.existsByName(storageType.getName())) {
            throw new IllegalArgumentException("Storage type with this name already exists.");
        }
        storageType.setBuiltIn(false);
        return storageTypeRepository.save(storageType);
    }

    public StorageType update(Long id, StorageType updated) {
        StorageType existing = storageTypeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Storage type not found"));
        if (existing.isBuiltIn()) {
            throw new IllegalArgumentException("Cannot rename a built-in storage type.");
        }
        if (!existing.getName().equals(updated.getName())
                && storageTypeRepository.existsByName(updated.getName())) {
            throw new IllegalArgumentException("Storage type with this name already exists.");
        }
        existing.setName(updated.getName());
        existing.setIcon(updated.getIcon());
        return storageTypeRepository.save(existing);
    }

    public void delete(Long id) {
        StorageType existing = storageTypeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Storage type not found"));
        if (existing.isBuiltIn()) {
            throw new IllegalArgumentException("Cannot delete a built-in storage type.");
        }
        long usageCount = transactionRepository.countByStorageType(existing.getName());
        if (usageCount > 0) {
            throw new IllegalStateException(
                "Cannot delete: " + usageCount + " transaction(s) use this storage type.");
        }
        storageTypeRepository.deleteById(id);
    }
}
