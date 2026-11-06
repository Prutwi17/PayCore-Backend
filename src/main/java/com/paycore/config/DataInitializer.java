package com.paycore.config;

import com.paycore.user.entity.Role;
import com.paycore.user.entity.User;
import com.paycore.user.entity.UserStatus;
import com.paycore.user.repository.RoleRepository;
import com.paycore.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${paycore.security.seed-test-admin:false}")
    private boolean seedTestAdmin;

    public DataInitializer(RoleRepository roleRepository, UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        // Seed default platform roles
        Role adminRole = createRoleIfNotExists(Role.ROLE_ADMIN);
        createRoleIfNotExists(Role.ROLE_MERCHANT);
        createRoleIfNotExists(Role.ROLE_CUSTOMER);

        // In test and development environments, seed administrator accounts if explicitly enabled
        if (seedTestAdmin) {
            String initialAdminEmail = "omp27614@gmail.com";
            Optional<User> existingInitialAdmin = userRepository.findByEmail(initialAdminEmail);
            if (existingInitialAdmin.isEmpty()) {
                User initialAdmin = new User();
                initialAdmin.setUsername("omp27614");
                initialAdmin.setEmail(initialAdminEmail);
                initialAdmin.setFullName("Lead Platform Administrator");
                initialAdmin.setPasswordHash(passwordEncoder.encode("AdminPassword123!"));
                initialAdmin.setStatus(UserStatus.ACTIVE);
                initialAdmin.setEmailVerified(true);
                initialAdmin.addRole(adminRole);
                userRepository.save(initialAdmin);
                log.info("Initialized platform administrator account: [{}]", initialAdminEmail);
            }

            String testAdminEmail = "admin@paycore.internal";
            Optional<User> existingTestAdmin = userRepository.findByEmail(testAdminEmail);
            if (existingTestAdmin.isEmpty()) {
                User testAdmin = new User();
                testAdmin.setUsername("admin");
                testAdmin.setEmail(testAdminEmail);
                testAdmin.setFullName("Platform Administrator");
                testAdmin.setPasswordHash(passwordEncoder.encode("AdminPassword123!"));
                testAdmin.setStatus(UserStatus.ACTIVE);
                testAdmin.setEmailVerified(true);
                testAdmin.addRole(adminRole);
                userRepository.save(testAdmin);
                log.info("Initialized test administrator account: [{}]", testAdminEmail);
            }
        }

        // Verify if any platform administrator is currently provisioned
        boolean adminExists = userRepository.existsByRoles_Name(Role.ROLE_ADMIN);
        if (!adminExists) {
            log.info("No platform administrator account is currently configured.");
            log.info("Initial administrator onboarding is available for [omp27614@gmail.com] via secure setup flow.");
        } else {
            log.info("Platform administrator role is provisioned.");
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
