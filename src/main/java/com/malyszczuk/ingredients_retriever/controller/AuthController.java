package com.malyszczuk.ingredients_retriever.controller;

import com.malyszczuk.ingredients_retriever.auth.AccountService;
import com.malyszczuk.ingredients_retriever.auth.AppUserPrincipal;
import com.malyszczuk.ingredients_retriever.auth.LoginService;
import com.malyszczuk.ingredients_retriever.domain.AppUser;
import com.malyszczuk.ingredients_retriever.dto.LoginRequest;
import com.malyszczuk.ingredients_retriever.dto.RegisterRequest;
import com.malyszczuk.ingredients_retriever.dto.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        AppUser user = accountService.register(request.email(), request.password());
        return new UserResponse(user.getId(), user.getEmail());
    }

    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        AppUserPrincipal user = loginService.login(request.email(), request.password(), httpRequest, httpResponse);
        return new UserResponse(user.getId(), user.getEmail());
    }

    /** Who is logged in. Answers 401 when nobody is, which is how the frontend finds out whether it must show the login page. */
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AppUserPrincipal user) {
        return new UserResponse(user.getId(), user.getEmail());
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
