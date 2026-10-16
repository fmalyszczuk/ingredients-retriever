package com.malyszczuk.ingredients_retriever.auth;

import com.malyszczuk.ingredients_retriever.domain.AppUser;
import com.malyszczuk.ingredients_retriever.domain.TokenType;
import com.malyszczuk.ingredients_retriever.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final ApplicationEventPublisher events;
    private final AccountRecoveryService recovery;
    private final AccountTokens tokens;

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
        AppUser saved;
        try {
            saved = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Two registrations for the same address at the same instant: the unique index let exactly one through.
            throw new EmailAlreadyRegisteredException();
        }
        recovery.sendVerification(saved);
        return saved;
    }

    /**
     * @throws WrongPasswordException if {@code currentPassword} is not the account's password
     * @throws InvalidPasswordException if the new password is not acceptable, or is the same as the current one
     */
    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        AppUser user = confirmed(userId, currentPassword);
        passwordPolicy.check(newPassword, user.getEmail());
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new InvalidPasswordException("The new password must be different from the current one");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        tokens.invalidateAll(userId, TokenType.RESET_PASSWORD); // a reset link sent earlier must not undo this
    }

    /**
     * Deletes the account together with everything it owns (recipes, ingredients, shopping list: the database removes
     * those along with the user) and, once that has been committed, ends its sessions and forgets its chats.
     *
     * @throws WrongPasswordException if {@code password} is not the account's password
     */
    @Transactional
    public void deleteAccount(Long userId, String password) {
        AppUser user = confirmed(userId, password);
        userRepository.delete(user);
        userRepository.flush();
        events.publishEvent(new AccountDeletedEvent(user.getId(), user.getEmail()));
    }

    private AppUser confirmed(Long userId, String password) {
        AppUser user = userRepository.findById(userId).orElseThrow(() -> new NoSuchElementException("No such account"));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new WrongPasswordException();
        }
        return user;
    }
}
