package com.smartfinance.dashboard.repository;

import com.smartfinance.dashboard.model.Debt;
import com.smartfinance.dashboard.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DebtRepository extends JpaRepository<Debt, Long> {

    // ── User-scoped queries ───────────────────────────────────────────────────

    List<Debt> findByUser(User user);

    Optional<Debt> findByIdAndUser(Long id, User user);

    List<Debt> findByUserAndStatus(User user, String status);

    List<Debt> findByUserAndDirectionAndStatus(User user, String direction, String status);

    // ── Legacy unscoped queries ───────────────────────────────────────────────

    List<Debt> findByStatus(String status);

    List<Debt> findByDirectionAndStatus(String direction, String status);
}
