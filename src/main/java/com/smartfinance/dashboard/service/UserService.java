package com.smartfinance.dashboard.service;

import com.smartfinance.dashboard.model.User;
import com.smartfinance.dashboard.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // ── UserDetailsService ────────────────────────────────────────────────────

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                new ArrayList<>()
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
     * Looks up a user by their email verification token.
     * Returns an empty Optional if no user holds that token.
     */
    public Optional<User> findByEmailVerificationToken(String token) {
        return userRepository.findByEmailVerificationToken(token);
    }

    /**
     * Marks a user's email as verified and clears the verification token.
     * Throws RuntimeException if the token is unknown.
     */
    public void verifyEmail(String token) {
        User user = userRepository.findByEmailVerificationToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid email verification token"));
        user.setEmailVerified(true);
        user.setEmailVerificationToken(null);
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
        // 1. Try to find an existing OAuth-linked account.
        Optional<User> existing = userRepository.findByProviderAndProviderId(provider, providerId);
        if (existing.isPresent()) {
            return existing.get();
        }

        // 2. Derive a unique username from the provider's display name.
        String baseUsername = (name != null && !name.isBlank())
                ? name.toLowerCase().replaceAll("[^a-z0-9]", "_")
                : email.split("@")[0].toLowerCase().replaceAll("[^a-z0-9]", "_");
        String username = baseUsername;
        int suffix = 1;
        while (userRepository.existsByUsername(username)) {
            username = baseUsername + "_" + suffix++;
        }

        // 3. Create the new user record.
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
}
