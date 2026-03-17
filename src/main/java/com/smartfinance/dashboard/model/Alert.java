package com.smartfinance.dashboard.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "alerts")
@Data
public class Alert {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String type; // BUDGET_WARNING, GOAL_REMINDER, INVESTMENT_ALERT
    
    @Column(nullable = false)
    private String severity; // INFO, WARNING, CRITICAL
    
    @Column(nullable = false)
    private String title;
    
    @Column(nullable = false, length = 1000)
    private String message;
    
    private Long relatedEntityId; // ID of related budget, goal, or investment
    
    private String relatedEntityType; // BUDGET, GOAL, INVESTMENT
    
    @Column(nullable = false)
    private Boolean isRead = false;
    
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    private LocalDateTime readAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
