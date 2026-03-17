package com.smartfinance.dashboard.repository;

import com.smartfinance.dashboard.model.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {
    
    List<Alert> findByIsReadOrderByCreatedAtDesc(Boolean isRead);
    
    List<Alert> findByTypeAndIsRead(String type, Boolean isRead);
    
    List<Alert> findBySeverityAndIsReadOrderByCreatedAtDesc(String severity, Boolean isRead);
    
    List<Alert> findTop10ByOrderByCreatedAtDesc();
    
    long countByIsRead(Boolean isRead);
}
