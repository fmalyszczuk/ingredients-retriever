package com.malyszczuk.ingredients_retriever.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

@Service
public class LoginService {

    private final AuthenticationManager authenticationManager;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final SecurityContextRepository securityContextRepository;
    private final SecurityContextHolderStrategy securityContextHolderStrategy = SecurityContextHolder.getContextHolderStrategy();
    private final boolean requireVerifiedEmail;

    public LoginService(AuthenticationManager authenticationManager, SessionAuthenticationStrategy sessionAuthenticationStrategy,
                        SecurityContextRepository securityContextRepository,
                        @Value("${pantrypal.security.require-verified-email:true}") boolean requireVerifiedEmail) {
        this.authenticationManager = authenticationManager;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
        this.securityContextRepository = securityContextRepository;
        this.requireVerifiedEmail = requireVerifiedEmail;
    }

    /**
     * Checks the credentials and, if they are right, starts a logged-in session.
     *
     * @throws InvalidCredentialsException whatever was wrong, with one and the same answer
     * @throws EmailNotVerifiedException the credentials are right but the address was never confirmed (no session is started)
     */
    public AppUserPrincipal login(String email, String password, HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(AccountService.normalizeEmail(email), password));
        } catch (AuthenticationException e) {
            throw new InvalidCredentialsException();
        }

        AppUserPrincipal principal = (AppUserPrincipal) authentication.getPrincipal();
        if (requireVerifiedEmail && !principal.isEmailVerified()) {
            throw new EmailNotVerifiedException();
        }

        // Replace any session the client already had with a fresh id (session fixation protection) and rotate the
        // CSRF token, then remember the login in the new session.
        sessionAuthenticationStrategy.onAuthentication(authentication, request, response);
        SecurityContext context = securityContextHolderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        securityContextHolderStrategy.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        return principal;
    }
}
