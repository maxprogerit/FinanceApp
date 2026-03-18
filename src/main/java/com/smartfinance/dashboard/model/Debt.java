package com.smartfinance.dashboard.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "debts")
@Data
public class Debt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String currency;

    private LocalDate dueDate;

    @Column(nullable = false)
    private String direction; // I_OWE or THEY_OWE

    private String description;

    @Column(nullable = false)
    private String status = "ACTIVE"; // ACTIVE or SETTLED

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime settledAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
