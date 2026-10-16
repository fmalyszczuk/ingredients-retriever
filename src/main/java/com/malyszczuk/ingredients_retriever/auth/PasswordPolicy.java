package com.malyszczuk.ingredients_retriever.auth;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * What makes a password acceptable. Follows current guidance (NIST SP 800-63B): length matters, composition rules
 * ("one digit, one symbol") don't, and the obvious choices are refused. The upper limit exists because BCrypt only
 * looks at the first 72 bytes: a longer password would be silently truncated, so it is refused instead.
 */
@Component
public class PasswordPolicy {

    public static final int MIN_LENGTH = 10;
    public static final int MAX_BYTES = 72;

    private static final Set<String> COMMON_PASSWORDS = Set.of(
            "password12", "password123", "password1234", "password12345", "passw0rd12", "passw0rd123", "1234567890",
            "12345678910", "0123456789", "qwertyuiop", "qwerty1234", "qwerty12345", "1q2w3e4r5t", "iloveyou12",
            "letmein123", "welcome123", "admin12345", "abc1234567", "changeme123", "trustno1234", "monkey1234",
            "football123", "baseball123", "superman123", "pantrypal1", "pantrypal12", "pantrypal123");

    /**
     * @param email the already-normalised email address of the account being created, so that "your email" is refused
     * @throws InvalidPasswordException if the password is not acceptable; the message says why, never what the password was
     */
    public void check(String password, String email) {
        if (password == null || password.isEmpty()) {
            throw new InvalidPasswordException("A password is required");
        }
        if (password.codePointCount(0, password.length()) < MIN_LENGTH) {
            throw new InvalidPasswordException("The password must be at least " + MIN_LENGTH + " characters long");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new InvalidPasswordException("The password is too long (at most " + MAX_BYTES + " bytes, which is about "
                    + MAX_BYTES + " ordinary characters)");
        }
        if (password.indexOf('\u0000') >= 0) {
            throw new InvalidPasswordException("The password contains an invalid character");
        }

        String lower = password.toLowerCase(Locale.ROOT);
        if (COMMON_PASSWORDS.contains(lower) || isOneRepeatedCharacter(password)) {
            throw new InvalidPasswordException("That password is too common or too easy to guess");
        }
        if (email != null && !email.isBlank()) {
            String local = email.substring(0, email.indexOf('@') >= 0 ? email.indexOf('@') : email.length());
            if (lower.equals(email.toLowerCase(Locale.ROOT)) || (local.length() >= MIN_LENGTH && lower.equals(local.toLowerCase(Locale.ROOT)))) {
                throw new InvalidPasswordException("The password must not be your email address");
            }
        }
    }

    private boolean isOneRepeatedCharacter(String password) {
        return password.codePoints().distinct().count() == 1;
    }
}
