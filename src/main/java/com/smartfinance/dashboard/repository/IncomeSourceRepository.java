package com.smartfinance.dashboard.repository;

import com.smartfinance.dashboard.model.IncomeSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IncomeSourceRepository extends JpaRepository<IncomeSource, Long> {
    Optional<IncomeSource> findByName(String name);
    boolean existsByName(String name);
}
