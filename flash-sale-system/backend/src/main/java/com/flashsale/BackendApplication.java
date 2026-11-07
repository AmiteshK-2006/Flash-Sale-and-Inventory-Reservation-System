package com.flashsale;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.flashsale.auth.User;
import com.flashsale.auth.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.OffsetDateTime;

@SpringBootApplication
@EnableScheduling
public class BackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }

    @Bean
    public CommandLineRunner initAdmin(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            userRepository.findByEmail("admin@flashsale.local").ifPresentOrElse(
                admin -> {
                    admin.setPasswordHash(passwordEncoder.encode("admin123"));
                    admin.setRole("ADMIN");
                    userRepository.save(admin);
                },
                () -> {
                    User admin = new User();
                    admin.setName("Administrator");
                    admin.setEmail("admin@flashsale.local");
                    admin.setPasswordHash(passwordEncoder.encode("admin123"));
                    admin.setRole("ADMIN");
                    admin.setCreatedAt(OffsetDateTime.now());
                    userRepository.save(admin);
                }
            );
        };
    }
}
