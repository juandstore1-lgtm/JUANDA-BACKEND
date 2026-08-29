package com.jdqstore.backend.config;

import com.jdqstore.backend.entity.*;
import com.jdqstore.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private final HeroSlideRepository heroSlideRepository;
    private final RouletteSettingRepository rouletteSettingRepository;
    private final MysteryBoxSettingRepository mysteryBoxSettingRepository;
    private final ContestRepository contestRepository;
    private final HomeCategoryCollectionRepository homeCategoryCollectionRepository;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        // 1. Crear Roles si no existen
        Role globalAdminRole = roleRepository.findByName("GLOBAL_ADMIN").orElse(null);
        if (globalAdminRole == null) {
            globalAdminRole = new Role();
            globalAdminRole.setName("GLOBAL_ADMIN");
            globalAdminRole = roleRepository.save(globalAdminRole);
        }

        Role storeAdminRole = roleRepository.findByName("STORE_ADMIN").orElse(null);
        if (storeAdminRole == null) {
            storeAdminRole = new Role();
            storeAdminRole.setName("STORE_ADMIN");
            roleRepository.save(storeAdminRole);
        }

        // 2. Crear Usuario Admin Principal si no existe
        if (userRepository.findByEmail("admin@jdqstore.com").isEmpty()) {
            User admin = new User();
            admin.setName("Administrador");
            admin.setEmail("admin@jdqstore.com");
            admin.setPassword(passwordEncoder.encode("admin123")); // Contraseña por defecto
            admin.setRole(globalAdminRole);
            admin.setIsActive(true);
            userRepository.save(admin);
            System.out.println("Usuario administrador creado: admin@jdqstore.com / admin123");
        }


    }
}
