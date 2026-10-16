package com.malyszczuk.ingredients_retriever.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

@Configuration
public class PasswordConfig {

    /**
     * BCrypt with a work factor of 12 by default (a deliberately slow hash, roughly a quarter of a second). Hashes are
     * stored with a "{bcrypt}" prefix, so a stronger algorithm can be introduced later while existing users keep
     * working. Tests lower the work factor with pantrypal.security.bcrypt-strength to stay fast.
     */
    @Bean
    public PasswordEncoder passwordEncoder(@Value("${pantrypal.security.bcrypt-strength:12}") int strength) {
        return new DelegatingPasswordEncoder("bcrypt", Map.of("bcrypt", new BCryptPasswordEncoder(strength)));
    }
}
