package com.smartfinance.dashboard.repository;

import com.smartfinance.dashboard.model.Debt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DebtRepository extends JpaRepository<Debt, Long> {
    List<Debt> findByStatus(String status);
    List<Debt> findByDirectionAndStatus(String direction, String status);
}
