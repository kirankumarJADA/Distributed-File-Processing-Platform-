package com.dfpp.upload.config;

import com.dfpp.upload.user.UserEntity;
import com.dfpp.upload.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Seeds two demo accounts on first boot so the platform is usable immediately:
 * <ul>
 *   <li>{@code admin / admin123}  - ROLE_ADMIN + ROLE_USER</li>
 *   <li>{@code demo  / demo1234}  - ROLE_USER</li>
 * </ul>
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public DataSeeder(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @Override
    public void run(String... args) {
        seed("admin", "admin123", Set.of("ROLE_ADMIN", "ROLE_USER"));
        seed("demo", "demo1234", Set.of("ROLE_USER"));
    }

    private void seed(String username, String rawPassword, Set<String> roles) {
        if (users.existsByUsername(username)) {
            return;
        }
        users.save(new UserEntity(username, encoder.encode(rawPassword), roles));
        log.info("Seeded demo user '{}' with roles {}", username, roles);
    }
}
