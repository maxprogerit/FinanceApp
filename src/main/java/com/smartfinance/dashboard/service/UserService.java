package com.smartfinance.dashboard.service;

import com.smartfinance.dashboard.model.User;
import com.smartfinance.dashboard.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // ── UserDetailsService ────────────────────────────────────────────────────

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user;
        // Accept both email addresses and plain usernames at the login field
        if (username != null && username.contains("@")) {
            user = userRepository.findByEmail(username)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        } else {
            user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        }
        // Local accounts must verify email before they can log in.
        // OAuth2 users (provider != null) are always enabled — the provider verified them already.
        boolean enabled = Boolean.TRUE.equals(user.getEmailVerified()) || user.getProvider() != null;
        String roleWithPrefix = "ROLE_" + (user.getRole() != null ? user.getRole() : "USER");
        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                enabled, true, true, true,
                List.of(new SimpleGrantedAuthority(roleWithPrefix))
        );
    }

    // ── Registration ──────────────────────────────────────────────────────────

    public User register(String username, String email, String password) {
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already taken");
        }
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already registered");
        }
        if (password == null || password.length() < 12) {
            throw new IllegalArgumentException("Password must be at least 12 characters");
        }
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        return userRepository.save(user);
    }

    // ── Basic lookups ─────────────────────────────────────────────────────────

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public User save(User user) {
        return userRepository.save(user);
    }

    // ── Email verification ────────────────────────────────────────────────────

    /**
     * Generates a UUID email-verification token for the user, stores it with a
     * 24-hour expiry, persists the user, and returns the raw token.
     */
    public String generateVerificationToken(User user) {
        String token = UUID.randomUUID().toString();
        user.setEmailVerificationToken(token);
        user.setEmailVerificationTokenExpiry(LocalDateTime.now().plusHours(24));
        userRepository.save(user);
        return token;
    }

    /**
     * Marks a user's email as verified and clears the verification token.
     * Throws RuntimeException("EXPIRED_TOKEN") if the token has expired,
     * or RuntimeException("INVALID_TOKEN") if the token is unknown.
     */
    public void verifyEmail(String token) {
        User user = userRepository.findByEmailVerificationToken(token)
                .orElseThrow(() -> new RuntimeException("INVALID_TOKEN"));
        if (user.getEmailVerificationTokenExpiry() != null
                && user.getEmailVerificationTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("EXPIRED_TOKEN");
        }
        user.setEmailVerified(true);
        user.setEmailVerificationToken(null);
        user.setEmailVerificationTokenExpiry(null);
        userRepository.save(user);
    }

    // ── Password reset ────────────────────────────────────────────────────────

    /**
     * Generates a UUID password-reset token for the user with the given email,
     * stores it on the user record with a 1-hour expiry, and returns the raw token.
     * Throws RuntimeException if the email is not registered.
     */
    public String generatePasswordResetToken(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("No account found for email: " + email));
        String token = UUID.randomUUID().toString();
        user.setPasswordResetToken(token);
        user.setPasswordResetExpiry(LocalDateTime.now().plusHours(1));
        userRepository.save(user);
        return token;
    }

    /**
     * Validates the password-reset token and its expiry, then sets the new
     * BCrypt-encoded password and clears the token and expiry fields.
     *
     * @return true if the password was reset successfully, false if the token is
     *         invalid or expired.
     */
    public boolean resetPassword(String token, String newPassword) {
        Optional<User> opt = userRepository.findByPasswordResetToken(token);
        if (opt.isEmpty()) {
            return false;
        }
        User user = opt.get();
        if (user.getPasswordResetExpiry() == null
                || user.getPasswordResetExpiry().isBefore(LocalDateTime.now())) {
            return false;
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordResetToken(null);
        user.setPasswordResetExpiry(null);
        userRepository.save(user);
        return true;
    }

    // ── OAuth2 ────────────────────────────────────────────────────────────────

    /**
     * Finds an existing OAuth user by provider + providerId, or creates a new
     * account if none exists. The new account's email is marked as verified
     * because the OAuth provider has already verified it.
     *
     * @param provider   e.g. "google"
     * @param providerId the provider's unique user identifier
     * @param email      the email address supplied by the provider
     * @param name       the display name supplied by the provider
     * @return the existing or newly created User
     */
    public User findOrCreateOAuthUser(String provider, String providerId, String email, String name) {
        // 1. Try to find an existing OAuth-linked account by provider + providerId.
        Optional<User> existing = userRepository.findByProviderAndProviderId(provider, providerId);
        if (existing.isPresent()) {
            return existing.get();
        }

        // 2. If a local account already exists with this email, link it to the OAuth provider
        //    instead of creating a duplicate — avoids unique-email constraint violations.
        Optional<User> byEmail = userRepository.findByEmail(email);
        if (byEmail.isPresent()) {
            User user = byEmail.get();
            user.setProvider(provider);
            user.setProviderId(providerId);
            user.setEmailVerified(true); // provider has confirmed the email
            return userRepository.save(user);
        }

        // 3. Derive a unique username from the provider's display name.
        String baseUsername = (name != null && !name.isBlank())
                ? name.toLowerCase().replaceAll("[^a-z0-9]", "_")
                : email.split("@")[0].toLowerCase().replaceAll("[^a-z0-9]", "_");
        String username = baseUsername;
        int suffix = 1;
        while (userRepository.existsByUsername(username)) {
            username = baseUsername + "_" + suffix++;
        }

        // 4. Create the new user record.
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        // OAuth users do not log in with a password; store an unusable placeholder.
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setProvider(provider);
        user.setProviderId(providerId);
        user.setEmailVerified(true);

        return userRepository.save(user);
    }

    // ── Currency ──────────────────────────────────────────────────────────────

    private static final Set<String> SUPPORTED_CURRENCIES = Set.of(
            "USD", "EUR", "GBP", "JPY", "CAD", "AUD", "CHF", "CNY", "INR", "BRL", "RSD"
    );

    /**
     * Updates the user's base currency (used for new transactions and UI display).
     *
     * @throws IllegalArgumentException if the currency code is not supported.
     */
    public void updateBaseCurrency(User user, String currency) {
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("Currency code must not be blank");
        }
        String code = currency.trim().toUpperCase();
        if (!SUPPORTED_CURRENCIES.contains(code)) {
            throw new IllegalArgumentException("Unsupported currency: " + code);
        }
        user.setBaseCurrency(code);
        userRepository.save(user);
        log.info("User '{}' changed base currency to {}", user.getUsername(), code);
    }
}
