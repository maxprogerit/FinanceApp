package com.smartfinance.dashboard.config;

import com.smartfinance.dashboard.security.JwtAuthenticationFilter;
import com.smartfinance.dashboard.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Value("${app.frontend-url:http://localhost:8081}")
    private String frontendUrl;

    // ── Authentication provider ───────────────────────────────────────────────

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager() {
        return new ProviderManager(authenticationProvider());
    }

    // ── Security filter chain ─────────────────────────────────────────────────

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // CORS — allow configured origins only
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // Public routes
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/", "/login", "/register",
                    "/verify-email", "/forgot-password", "/reset-password",
                    "/resend-verification",
                    "/api/auth/token",
                    "/api/payments/webhook",  // Stripe webhooks are verified by signature
                    "/css/**", "/js/**", "/images/**",
                    "/actuator/health"
                ).permitAll()
                .requestMatchers("/admin", "/admin/**", "/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )

            // JWT filter runs before username/password filter
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

            // Form login (kept for Thymeleaf page navigation)
            .authenticationProvider(authenticationProvider())
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("email")   // login form posts 'email' field
                .defaultSuccessUrl("/dashboard", true)
                .failureHandler(loginFailureHandler())
                .permitAll()
            )

            // OAuth2 Login (Google)
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/login")
                .userInfoEndpoint(info -> info.userService(oAuth2UserService()))
                .successHandler((request, response, authentication) -> {
                    // After OAuth2 login, redirect to dashboard
                    response.sendRedirect("/dashboard");
                })
                .failureUrl("/login?error=oauth")
            )

            // Logout
            .logout(logout -> logout
                .logoutRequestMatcher(new AntPathRequestMatcher("/logout", "GET"))
                .logoutSuccessUrl("/login?logout")
                .deleteCookies("JSESSIONID")
                .permitAll()
            )

            // CSRF — disabled for API and webhooks (APIs use JWT; webhook uses Stripe signature)
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/**")
            )

            // Security headers
            .headers(headers -> headers
                .frameOptions(frame -> frame.sameOrigin())
                .contentTypeOptions(ct -> {})
                .xssProtection(xss -> {})
            );

        return http.build();
    }

    // ── CORS configuration ────────────────────────────────────────────────────

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // In production, restrict to your actual domain
        config.setAllowedOrigins(List.of(frontendUrl, "http://localhost:8081", "http://localhost:3000"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    // ── Login failure handler ─────────────────────────────────────────────────

    @Bean
    public AuthenticationFailureHandler loginFailureHandler() {
        return (request, response, exception) -> {
            // Distinguish "account not verified" from wrong credentials so the UI
            // can show a targeted error message instead of a generic one.
            String errorParam = (exception instanceof DisabledException) ? "unverified" : "invalid";
            response.sendRedirect("/login?error=" + errorParam);
        };
    }

    // ── OAuth2 user service (Google login) ────────────────────────────────────

    @Bean
    public OAuth2UserService<OAuth2UserRequest, OAuth2User> oAuth2UserService() {
        DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
        return request -> {
            OAuth2User oAuth2User = delegate.loadUser(request);
            String provider = request.getClientRegistration().getRegistrationId(); // "google"
            String providerId = oAuth2User.getName();
            String email = oAuth2User.getAttribute("email");
            String name = oAuth2User.getAttribute("name");

            // Find or create the user in our database
            userService.findOrCreateOAuthUser(provider, providerId, email, name);

            return new DefaultOAuth2User(
                    oAuth2User.getAuthorities(),
                    oAuth2User.getAttributes(),
                    "email"
            );
        };
    }
}
