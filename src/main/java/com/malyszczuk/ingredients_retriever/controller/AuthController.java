package com.malyszczuk.ingredients_retriever.controller;

import com.malyszczuk.ingredients_retriever.auth.AccountService;
import com.malyszczuk.ingredients_retriever.auth.AccountRecoveryService;
import com.malyszczuk.ingredients_retriever.auth.AppUserPrincipal;
import com.malyszczuk.ingredients_retriever.auth.AuthThrottle;
import com.malyszczuk.ingredients_retriever.auth.InvalidCredentialsException;
import com.malyszczuk.ingredients_retriever.auth.LoginService;
import com.malyszczuk.ingredients_retriever.config.SecurityConfig;
import com.malyszczuk.ingredients_retriever.domain.AppUser;
import com.malyszczuk.ingredients_retriever.auth.SignUpService;
import com.malyszczuk.ingredients_retriever.auth.UserSessions;
import com.malyszczuk.ingredients_retriever.auth.WrongPasswordException;
import com.malyszczuk.ingredients_retriever.dto.ChangePasswordRequest;
import com.malyszczuk.ingredients_retriever.dto.DeleteAccountRequest;
import com.malyszczuk.ingredients_retriever.dto.EmailRequest;
import com.malyszczuk.ingredients_retriever.dto.LoginRequest;
import com.malyszczuk.ingredients_retriever.dto.RegisterRequest;
import com.malyszczuk.ingredients_retriever.dto.RegisterResponse;
import com.malyszczuk.ingredients_retriever.dto.ResetPasswordRequest;
import com.malyszczuk.ingredients_retriever.dto.TokenRequest;
import com.malyszczuk.ingredients_retriever.dto.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.CompositeLogoutHandler;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Account endpoints. Logging out is handled by Spring Security itself: POST /auth/logout (see SecurityConfig). */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AccountService accountService;
    private final LoginService loginService;
    private final AuthThrottle authThrottle;
    private final UserSessions userSessions;
    private final AccountRecoveryService recoveryService;
    private final SignUpService signUpService;

    // What logging out does, for when the account is deleted: the session is destroyed and the cookies expire.
    private final LogoutHandler logoutHandler = new CompositeLogoutHandler(new SecurityContextLogoutHandler(),
            new CookieClearingLogoutHandler(SecurityConfig.SESSION_COOKIE, SecurityConfig.CSRF_COOKIE));

    /**
     * Signs up. Answers 202 and the same body whether the address is new or already has an account: the owner is told by
     * email which of the two it was, so that this endpoint cannot be used to find out who is registered.
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        String address = httpRequest.getRemoteAddr();
        authThrottle.registrationAttempt(address);
        // every sign-up attempt makes the application send an email to the address, so it shares the email limits
        authThrottle.mailRequest(address, AccountService.normalizeEmail(request.email()));
        return new RegisterResponse(signUpService.signUp(request.email(), request.password()));
    }

    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String address = httpRequest.getRemoteAddr();
        String account = AccountService.normalizeEmail(request.email());
        // Refused before the password is even looked at, so a blocked guess is never told whether it was right.
        authThrottle.checkLogin(address, account);
        AppUserPrincipal user;
        try {
            user = loginService.login(request.email(), request.password(), httpRequest, httpResponse);
        } catch (InvalidCredentialsException e) {
            authThrottle.loginFailed(address, account);
            throw e;
        }
        authThrottle.loginSucceeded(address, account);
        return new UserResponse(user.getId(), user.getEmail());
    }

    /** Who is logged in. Answers 401 when nobody is, which is how the frontend finds out whether it must show the login page. */
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AppUserPrincipal user) {
        return new UserResponse(user.getId(), user.getEmail());
    }

    /** Opens the link from the verification email. The frontend's page for that link posts its token here. */
    @PostMapping("/verify-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifyEmail(@Valid @RequestBody TokenRequest request) {
        recoveryService.verifyEmail(request.token());
    }

    /** Sends the verification link again. Answers 204 whether or not the address has an account that needs one. */
    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resendVerification(@Valid @RequestBody EmailRequest request, HttpServletRequest httpRequest) {
        authThrottle.mailRequest(httpRequest.getRemoteAddr(), AccountService.normalizeEmail(request.email()));
        recoveryService.resendVerification(request.email());
    }

    /** Emails a password reset link. Answers 204 whether or not the address has an account. */
    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void forgotPassword(@Valid @RequestBody EmailRequest request, HttpServletRequest httpRequest) {
        authThrottle.mailRequest(httpRequest.getRemoteAddr(), AccountService.normalizeEmail(request.email()));
        recoveryService.forgotPassword(request.email());
    }

    /** Sets a new password with the token from the reset link. Logs the account out everywhere; it does not log anyone in. */
    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        recoveryService.resetPassword(request.token(), request.newPassword());
    }

    /**
     * Changes the password of the logged-in user, who must give the current one again (so that a stolen session alone
     * is not enough). Every other session of the user is ended; this one stays logged in.
     */
    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal AppUserPrincipal user, @Valid @RequestBody ChangePasswordRequest request,
                               HttpServletRequest httpRequest) {
        confirmingPassword(user, httpRequest,
                () -> accountService.changePassword(user.getId(), request.currentPassword(), request.newPassword()));
        userSessions.endAll(user.getEmail(), httpRequest.getSession().getId());
    }

    /** Deletes the logged-in user's account and all of its data, for good, and logs them out. Needs the password. */
    @PostMapping("/delete-account")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(@AuthenticationPrincipal AppUserPrincipal user, @Valid @RequestBody DeleteAccountRequest request,
                              HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        confirmingPassword(user, httpRequest, () -> accountService.deleteAccount(user.getId(), request.password()));
        logoutHandler.logout(httpRequest, httpResponse, SecurityContextHolder.getContext().getAuthentication());
    }

    /**
     * Runs an action that needs the password again. Wrong passwords count against the same limits as wrong passwords
     * at login, or a stolen session could be used to guess the password here.
     */
    private void confirmingPassword(AppUserPrincipal user, HttpServletRequest httpRequest, Runnable action) {
        String address = httpRequest.getRemoteAddr();
        authThrottle.checkLogin(address, user.getEmail());
        try {
            action.run();
        } catch (WrongPasswordException e) {
            authThrottle.loginFailed(address, user.getEmail());
            throw e;
        }
        authThrottle.loginSucceeded(address, user.getEmail());
    }

    /**
     * Hands out the CSRF token as the XSRF-TOKEN cookie. Call it before the first POST (register or login), since those
     * need the token too. The token is only an argument here so that Spring loads it and sets the cookie.
     */
    @GetMapping("/csrf")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void csrf(CsrfToken token) {
        // nothing to return: the cookie is the answer
    }
}
