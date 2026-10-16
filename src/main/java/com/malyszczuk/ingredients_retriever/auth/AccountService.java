package com.malyszczuk.ingredients_retriever.auth;

import com.malyszczuk.ingredients_retriever.domain.AppUser;
import com.malyszczuk.ingredients_retriever.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;

    /** Email addresses are compared ignoring case and surrounding spaces, and stored in that normal form. */
    public static String normalizeEmail(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    @Transactional
    public AppUser register(String email, String password) {
        String normalizedEmail = normalizeEmail(email);
        passwordPolicy.check(password, normalizedEmail);

        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new EmailAlreadyRegisteredException();
        }

        AppUser user = AppUser.builder()
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(password))
                .build();
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Two registrations for the same address at the same instant: the unique index let exactly one through.
            throw new EmailAlreadyRegisteredException();
        }
    }
}
