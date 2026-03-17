package com.smartfinance.dashboard.repository;

import com.smartfinance.dashboard.model.StorageType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StorageTypeRepository extends JpaRepository<StorageType, Long> {
    Optional<StorageType> findByName(String name);
    boolean existsByName(String name);
}
