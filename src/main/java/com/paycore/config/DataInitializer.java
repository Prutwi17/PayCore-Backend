package com.paycore.config;

import com.paycore.user.entity.Role;
import com.paycore.user.entity.User;
import com.paycore.user.entity.UserStatus;
import com.paycore.user.repository.RoleRepository;
import com.paycore.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(RoleRepository roleRepository, UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        Role adminRole = createRoleIfNotExists(Role.ROLE_ADMIN);
        createRoleIfNotExists(Role.ROLE_MERCHANT);
        createRoleIfNotExists(Role.ROLE_CUSTOMER);

        // Seed default platform admin account if not already present
        String adminEmail = "admin@paycore.internal";
        Optional<User> existingAdmin = userRepository.findByEmail(adminEmail);
        if (existingAdmin.isEmpty()) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setEmail(adminEmail);
            admin.setFullName("Platform Administrator");
            admin.setPasswordHash(passwordEncoder.encode("AdminPassword123!"));
            admin.setStatus(UserStatus.ACTIVE);
            admin.addRole(adminRole);

            userRepository.save(admin);
            log.info("Initialized default administrator account: [{}]", adminEmail);
        }
    }

    private Role createRoleIfNotExists(String roleName) {
        return roleRepository.findByName(roleName)
                .orElseGet(() -> {
                    Role role = roleRepository.save(new Role(roleName));
                    log.info("Initialized system role: [{}]", roleName);
                    return role;
                });
    }
}
