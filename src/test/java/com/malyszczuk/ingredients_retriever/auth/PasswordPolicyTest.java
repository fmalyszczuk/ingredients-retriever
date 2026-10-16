package com.malyszczuk.ingredients_retriever.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @ParameterizedTest
    @ValueSource(strings = {
            "correct horse battery staple", "tenletters1", "a-long-passphrase-with-dashes", "ÅÄÖ üñí çødé 123",
            "      ten spaces      ", "kłopotliwe hasło 2026", "0123456789a", "😀😃😄😁😆😅😂🤣😊😇"})
    void check_acceptsLongEnoughPasswords_whateverTheyContain(String password) {
        assertDoesNotThrow(() -> policy.check(password, "me@example.com"), password);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"short", "123456789", "ninechars"})
    void check_requiresAtLeastTenCharacters(String password) {
        assertThrows(InvalidPasswordException.class, () -> policy.check(password, "me@example.com"));
    }

    @Test
    void check_countsCharactersNotBytes_forTheMinimum() {
        // ten emoji are ten characters even though they are 40 bytes
        String tenDistinctEmoji = "😀😃😄😁😆😅😂🤣😊😇";
        assertDoesNotThrow(() -> policy.check(tenDistinctEmoji, "me@example.com"));
        assertThrows(InvalidPasswordException.class, () -> policy.check(tenDistinctEmoji.substring(0, tenDistinctEmoji.offsetByCodePoints(0, 9)), "me@example.com"));
    }

    @Test
    void check_refusesPasswordsBcryptWouldSilentlyTruncate() {
        assertDoesNotThrow(() -> policy.check("a1".repeat(36), "me@example.com")); // exactly 72 bytes
        assertThrows(InvalidPasswordException.class, () -> policy.check("a1".repeat(36) + "x", "me@example.com")); // 73
        // 25 emoji are only 25 characters, but 100 bytes
        assertThrows(InvalidPasswordException.class, () -> policy.check("😀".repeat(25), "me@example.com"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"password123", "PASSWORD123", "Password1234", "1234567890", "qwertyuiop", "aaaaaaaaaaaa", "0000000000",
            "iloveyou12", "pantrypal123"})
    void check_refusesTheObviousChoices(String password) {
        InvalidPasswordException error = assertThrows(InvalidPasswordException.class, () -> policy.check(password, "me@example.com"));

        assertTrue(error.getMessage().contains("too common") || error.getMessage().contains("easy to guess"));
    }

    @Test
    void check_refusesTheEmailAddressItselfAsThePassword() {
        assertThrows(InvalidPasswordException.class, () -> policy.check("someone@example.com", "someone@example.com"));
        assertThrows(InvalidPasswordException.class, () -> policy.check("SomeOne@Example.com", "someone@example.com"));
        assertThrows(InvalidPasswordException.class, () -> policy.check("longusername1", "longusername1@example.com"));
    }

    @Test
    void check_refusesANulCharacter() {
        assertThrows(InvalidPasswordException.class, () -> policy.check("valid-start\u0000hidden", "me@example.com"));
    }

    @Test
    void check_neverRepeatsThePasswordInTheMessage() {
        String secret = "password123";

        InvalidPasswordException error = assertThrows(InvalidPasswordException.class, () -> policy.check(secret, "me@example.com"));

        assertFalse(error.getMessage().contains(secret));
    }
}
