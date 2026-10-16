package com.malyszczuk.ingredients_retriever.auth;

import com.malyszczuk.ingredients_retriever.domain.AccountToken;
import com.malyszczuk.ingredients_retriever.domain.AppUser;
import com.malyszczuk.ingredients_retriever.domain.TokenType;
import com.malyszczuk.ingredients_retriever.mail.AccountMailService;
import com.malyszczuk.ingredients_retriever.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Optional;

/**
 * Confirming an email address and getting back into an account whose password was forgotten, both through a one-time
 * link sent by email. The requests that start these ("send me a link") never say whether the address has an account.
 */
@Service
@RequiredArgsConstructor
public class AccountRecoveryService {

    static final Duration VERIFICATION_LIFETIME = Duration.ofHours(24);
    static final Duration RESET_LIFETIME = Duration.ofHours(1);

    private final UserRepository userRepository;
    private final AccountTokens tokens;
    private final AccountMailService mail;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final UserSessions userSessions;

    @Transactional
    public void sendVerification(AppUser user) {
        mail.sendVerification(user.getEmail(), tokens.issue(user.getId(), TokenType.VERIFY_EMAIL, VERIFICATION_LIFETIME));
    }

    /**
     * Somebody signed up with an address that already has an account. Answers nothing to them; tells the owner by
     * email. An account that never confirmed its address gets the new password (so that whoever signed up first with
     * somebody else's address cannot leave their own password on it for the real owner to confirm) and a fresh link.
     * The password is hashed in every case, so this takes as long as a real sign-up.
     */
    @Transactional
    public void signedUpAgain(String normalizedEmail, String password) {
        String hash = passwordEncoder.encode(password);
        findUser(normalizedEmail).ifPresent(user -> {
            if (user.isEmailVerified()) {
                mail.sendAlreadyRegistered(user.getEmail());
            } else {
                user.setPasswordHash(hash);
                userRepository.save(user);
                sendVerification(user);
            }
        });
    }

    /** Sends a new link if the address belongs to an account that still needs one. Silent otherwise. */
    @Transactional
    public void resendVerification(String email) {
        findUser(email).filter(user -> !user.isEmailVerified()).ifPresent(this::sendVerification);
    }

    /** @throws InvalidTokenException if the link is wrong, expired or has been used */
    @Transactional
    public void verifyEmail(String token) {
        Long userId = tokens.consume(token, TokenType.VERIFY_EMAIL).orElseThrow(InvalidTokenException::new);
        userRepository.findById(userId).ifPresent(user -> {
            user.setEmailVerified(true);
            userRepository.save(user);
        });
    }

    /** Sends a reset link if the address belongs to an account. Silent otherwise. */
    @Transactional
    public void forgotPassword(String email) {
        findUser(email).ifPresent(user ->
                mail.sendPasswordReset(user.getEmail(), tokens.issue(user.getId(), TokenType.RESET_PASSWORD, RESET_LIFETIME)));
    }

    /**
     * Sets a new password with a reset link. Receiving the link proves the address is the owner's, so it also counts as
     * verifying it. Every session of the account ends, because whoever knew the old password (or had a session) must
     * not stay in.
     *
     * @throws InvalidTokenException if the link is wrong, expired or has been used
     * @throws InvalidPasswordException if the new password is not acceptable; the link is then still usable
     */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        AccountToken found = tokens.find(token, TokenType.RESET_PASSWORD).orElseThrow(InvalidTokenException::new);
        AppUser user = userRepository.findById(found.getUserId()).orElseThrow(InvalidTokenException::new);
        passwordPolicy.check(newPassword, user.getEmail());

        // Only now is the link spent, and only one of two simultaneous requests can spend it.
        tokens.consume(token, TokenType.RESET_PASSWORD).orElseThrow(InvalidTokenException::new);

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setEmailVerified(true);
        userRepository.save(user);
        userSessions.endAll(user.getEmail(), null);
        mail.sendPasswordChanged(user.getEmail());
    }

    private Optional<AppUser> findUser(String email) {
        return userRepository.findByEmail(AccountService.normalizeEmail(email)).filter(AppUser::isEnabled);
    }
}
