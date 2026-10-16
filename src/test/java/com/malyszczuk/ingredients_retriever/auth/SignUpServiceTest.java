package com.malyszczuk.ingredients_retriever.auth;

import com.malyszczuk.ingredients_retriever.domain.AppUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SignUpServiceTest {

    @Mock
    private AccountService accountService;

    @Mock
    private AccountRecoveryService recovery;

    @InjectMocks
    private SignUpService signUpService;

    @Test
    void aNewAddress_isRegistered_andNothingElseHappens() {
        when(accountService.register(" Me@Example.com ", "a decent passphrase"))
                .thenReturn(AppUser.builder().email("me@example.com").build());

        assertEquals("me@example.com", signUpService.signUp(" Me@Example.com ", "a decent passphrase"));

        verifyNoInteractions(recovery);
    }

    @Test
    void anAddressThatExists_getsTheSameAnswer_andTheOwnerIsToldInstead() {
        when(accountService.register(" Me@Example.com ", "a decent passphrase")).thenThrow(new EmailAlreadyRegisteredException());

        assertEquals("me@example.com", signUpService.signUp(" Me@Example.com ", "a decent passphrase"));

        verify(recovery).signedUpAgain("me@example.com", "a decent passphrase");
    }

    @Test
    void aBadPassword_isReported_thesameWayForEveryone_andTellsNobodyAnything() {
        when(accountService.register("me@example.com", "short")).thenThrow(new InvalidPasswordException("too short"));

        assertThrows(InvalidPasswordException.class, () -> signUpService.signUp("me@example.com", "short"));

        verifyNoInteractions(recovery);
    }
}
