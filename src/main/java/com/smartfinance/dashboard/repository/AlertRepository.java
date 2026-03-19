package com.smartfinance.dashboard.repository;

import com.smartfinance.dashboard.model.Alert;
import com.smartfinance.dashboard.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {

    // ── User-scoped queries ───────────────────────────────────────────────────

    List<Alert> findByUserOrderByCreatedAtDesc(User user);

    Optional<Alert> findByIdAndUser(Long id, User user);

    List<Alert> findByUserAndIsReadOrderByCreatedAtDesc(User user, Boolean isRead);

    List<Alert> findTop10ByUserOrderByCreatedAtDesc(User user);

    long countByUserAndIsRead(User user, Boolean isRead);

    // ── Legacy unscoped queries ───────────────────────────────────────────────

    List<Alert> findByIsReadOrderByCreatedAtDesc(Boolean isRead);

    List<Alert> findByTypeAndIsRead(String type, Boolean isRead);

    List<Alert> findBySeverityAndIsReadOrderByCreatedAtDesc(String severity, Boolean isRead);

    List<Alert> findTop10ByOrderByCreatedAtDesc();

    long countByIsRead(Boolean isRead);
}
