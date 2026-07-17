package com.giri.oms.auth.config;

import com.giri.oms.auth.entity.AppUser;
import com.giri.oms.auth.entity.Role;
import com.giri.oms.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Registration is ADMIN-only (see AuthController), which is a chicken-and-egg
 * problem on a brand new database — there's no ADMIN yet to create the first
 * one. This runs once at startup and creates a default admin account if, and
 * only if, no users exist at all, so a fresh install always has exactly one
 * way in.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminUserSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.security.default-admin-username}")
    private String defaultAdminUsername;

    @Value("${app.security.default-admin-password}")
    private String defaultAdminPassword;

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return;
        }

        AppUser admin = new AppUser();
        admin.setUsername(defaultAdminUsername);
        admin.setPassword(passwordEncoder.encode(defaultAdminPassword));
        admin.setEmail(defaultAdminUsername + "@oms.local");
        admin.setRole(Role.ADMIN);
        admin.setEnabled(true);
        userRepository.save(admin);

        log.warn("No users found — bootstrapped default admin account '{}'. "
                + "Change its password (or disable it) before using this outside local dev.", defaultAdminUsername);
    }
}
