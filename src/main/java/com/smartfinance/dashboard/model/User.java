package com.smartfinance.dashboard.model;

import jakarta.persistence.*;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @JsonIgnore
    @Column(nullable = false)
    private String password;

    private String role = "USER";

    private String baseCurrency = "USD";

    private String theme = "light";

    // OAuth2 provider (null for local accounts, "google" for OAuth2)
    private String provider;
    private String providerId;

    // Email verification
    private Boolean emailVerified = false;
    @JsonIgnore
    private String emailVerificationToken;
    @JsonIgnore
    private LocalDateTime emailVerificationTokenExpiry;

    // Password reset
    @JsonIgnore
    private String passwordResetToken;
    @JsonIgnore
    private LocalDateTime passwordResetExpiry;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
