package com.smartfinance.dashboard.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "income_sources")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IncomeSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String name;

    private String icon;

    @Column(name = "built_in")
    private boolean builtIn;
}
