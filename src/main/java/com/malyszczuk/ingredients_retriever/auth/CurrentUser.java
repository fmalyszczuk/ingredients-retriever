package com.malyszczuk.ingredients_retriever.auth;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * The id of the user whose request is being handled. Everything that reads or writes user data goes through this, so
 * data is always scoped to its owner. Fails closed: with nobody logged in it throws instead of returning anything.
 * The request is handled on one thread, which is why the security context is available here (also inside chat tools).
 */
@Component
public class CurrentUser {

    public Long id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AppUserPrincipal principal) {
            return principal.getId();
        }
        throw new AuthenticationCredentialsNotFoundException("No logged-in user");
    }
}
