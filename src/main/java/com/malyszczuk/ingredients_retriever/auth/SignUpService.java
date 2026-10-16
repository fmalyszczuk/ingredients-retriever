package com.malyszczuk.ingredients_retriever.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * What happens when somebody signs up, built so that the caller cannot tell whether the address already had an account:
 * the answer is the same either way, and what differs goes to the address's owner by email. A new address gets a
 * verification link; an address that is already registered gets a notice (or, if it never confirmed, a fresh link).
 * Only a bad password is reported, and that is checked first, the same for everybody.
 */
@Service
@RequiredArgsConstructor
public class SignUpService {

    private final AccountService accountService;
    private final AccountRecoveryService recovery;

    /** @return the address as it was stored (trimmed, lower case) */
    public String signUp(String email, String password) {
        try {
            return accountService.register(email, password).getEmail();
        } catch (EmailAlreadyRegisteredException e) {
            recovery.signedUpAgain(AccountService.normalizeEmail(email), password);
            return AccountService.normalizeEmail(email);
        }
    }
}
