package com.malyszczuk.ingredients_retriever.auth;

import com.malyszczuk.ingredients_retriever.domain.AppUser;
import com.malyszczuk.ingredients_retriever.domain.TokenType;
import com.malyszczuk.ingredients_retriever.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Mock
    private ApplicationEventPublisher events;

    @Mock
    private AccountRecoveryService recovery;

    @Mock
    private AccountTokens tokens;

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(userRepository, passwordEncoder, new PasswordPolicy(), events, recovery, tokens);
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
    void register_createsAnUnverifiedAccount_andSendsTheVerificationLink() {
        when(userRepository.findByEmail("me@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("a decent passphrase")).thenReturn("{bcrypt}hash");
        when(userRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AppUser created = accountService.register("me@example.com", "a decent passphrase");

        assertFalse(created.isEmailVerified());
        verify(recovery).sendVerification(created);
    }

    @Test
    void register_sendsNoEmail_whenTheAddressIsTaken() {
        when(userRepository.findByEmail("me@example.com")).thenReturn(Optional.of(AppUser.builder().email("me@example.com").build()));

        assertThrows(EmailAlreadyRegisteredException.class, () -> accountService.register("me@example.com", "a decent passphrase"));

        verifyNoInteractions(recovery);
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

    // ---------------------------------------------------------------- changing the password and deleting the account

    private AppUser existingUser() {
        AppUser user = AppUser.builder().id(5L).email("me@example.com").passwordHash("{bcrypt}old").build();
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        return user;
    }

    @Test
    void changePassword_storesAHashOfTheNewPassword_whenTheCurrentOneIsRight() {
        AppUser user = existingUser();
        when(passwordEncoder.matches("the old passphrase", "{bcrypt}old")).thenReturn(true);
        when(passwordEncoder.matches("a brand new passphrase", "{bcrypt}old")).thenReturn(false);
        when(passwordEncoder.encode("a brand new passphrase")).thenReturn("{bcrypt}new");

        accountService.changePassword(5L, "the old passphrase", "a brand new passphrase");

        assertEquals("{bcrypt}new", user.getPasswordHash());
        verify(userRepository).save(user);
        verify(tokens).invalidateAll(5L, TokenType.RESET_PASSWORD);
    }

    @Test
    void changePassword_changesNothing_whenTheCurrentPasswordIsWrong() {
        AppUser user = existingUser();
        when(passwordEncoder.matches("not the password", "{bcrypt}old")).thenReturn(false);

        assertThrows(WrongPasswordException.class, () -> accountService.changePassword(5L, "not the password", "a brand new passphrase"));

        assertEquals("{bcrypt}old", user.getPasswordHash());
        verify(userRepository, never()).save(any());
    }

    @Test
    void changePassword_appliesThePasswordPolicy_toTheNewPassword() {
        AppUser user = existingUser();
        when(passwordEncoder.matches("the old passphrase", "{bcrypt}old")).thenReturn(true);

        assertThrows(InvalidPasswordException.class, () -> accountService.changePassword(5L, "the old passphrase", "short"));

        assertEquals("{bcrypt}old", user.getPasswordHash());
    }

    @Test
    void changePassword_refusesTheSamePasswordAgain() {
        existingUser();
        when(passwordEncoder.matches("the old passphrase", "{bcrypt}old")).thenReturn(true);

        assertThrows(InvalidPasswordException.class, () -> accountService.changePassword(5L, "the old passphrase", "the old passphrase"));

        verify(userRepository, never()).save(any());
    }

    @Test
    void deleteAccount_deletesTheUser_andAnnouncesIt() {
        AppUser user = existingUser();
        when(passwordEncoder.matches("the old passphrase", "{bcrypt}old")).thenReturn(true);

        accountService.deleteAccount(5L, "the old passphrase");

        verify(userRepository).delete(user);
        verify(events).publishEvent(new AccountDeletedEvent(5L, "me@example.com"));
    }

    @Test
    void deleteAccount_deletesNothing_whenThePasswordIsWrong() {
        existingUser();
        when(passwordEncoder.matches("nope", "{bcrypt}old")).thenReturn(false);

        assertThrows(WrongPasswordException.class, () -> accountService.deleteAccount(5L, "nope"));

        verify(userRepository, never()).delete(any());
        verifyNoInteractions(events);
    }
}
