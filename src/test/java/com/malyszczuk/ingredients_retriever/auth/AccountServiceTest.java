package com.malyszczuk.ingredients_retriever.auth;

import com.malyszczuk.ingredients_retriever.domain.AppUser;
import com.malyszczuk.ingredients_retriever.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(userRepository, passwordEncoder, new PasswordPolicy());
    }

    @Test
    void register_storesTheNormalisedEmail_andOnlyAHashOfThePassword() {
        when(userRepository.findByEmail("me@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("a decent passphrase")).thenReturn("{bcrypt}$2a$12$hash");
        when(userRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        accountService.register("  Me@Example.COM ", "a decent passphrase");

        ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertEquals("me@example.com", saved.getValue().getEmail());
        assertEquals("{bcrypt}$2a$12$hash", saved.getValue().getPasswordHash());
        assertNotEquals("a decent passphrase", saved.getValue().getPasswordHash());
    }

    @Test
    void register_refusesAnAddressThatIsAlreadyTaken_inAnyLetterCase() {
        when(userRepository.findByEmail("me@example.com")).thenReturn(Optional.of(AppUser.builder().email("me@example.com").build()));

        assertThrows(EmailAlreadyRegisteredException.class, () -> accountService.register("ME@example.com", "a decent passphrase"));

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void register_turnsALostRaceForTheSameAddressIntoTheSameConflict() {
        when(userRepository.findByEmail("me@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("{bcrypt}x");
        when(userRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThrows(EmailAlreadyRegisteredException.class, () -> accountService.register("me@example.com", "a decent passphrase"));
    }

    @Test
    void register_refusesAWeakPassword_beforeTouchingTheDatabaseOrHashing() {
        assertThrows(InvalidPasswordException.class, () -> accountService.register("me@example.com", "short"));
        assertThrows(InvalidPasswordException.class, () -> accountService.register("me@example.com", "password123"));

        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    void normalizeEmail_trimsAndLowerCases() {
        assertEquals("a@b.com", AccountService.normalizeEmail("  A@B.Com\t"));
        assertNull(AccountService.normalizeEmail(null));
    }
}
