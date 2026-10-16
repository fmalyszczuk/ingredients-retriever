package com.malyszczuk.ingredients_retriever.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.RequestCacheConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.session.DisableEncodeUrlFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

import java.io.IOException;
import java.util.List;

/**
 * Who may do what. Everything is denied to anonymous users except creating an account, logging in, and fetching a
 * CSRF token; there is no "public" part of this application. Login is a session: an HttpOnly cookie (which scripts on
 * a page cannot read, so an XSS bug cannot steal it) plus CSRF protection for everything that changes data.
 */
@Configuration
public class SecurityConfig {

    public static final String SESSION_COOKIE = "PANTRYPAL_SESSION";
    public static final String CSRF_COOKIE = "XSRF-TOKEN";
    public static final String CSRF_HEADER = "X-XSRF-TOKEN";

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, CsrfTokenRepository csrfTokenRepository,
                                                   SecurityContextRepository securityContextRepository,
                                                   @Value("${pantrypal.security.require-https:false}") boolean requireHttps) throws Exception {
        if (requireHttps) {
            // Plain-HTTP requests are refused before anything else happens. Behind a reverse proxy that terminates TLS,
            // a request counts as secure through X-Forwarded-Proto (see PANTRYPAL_FORWARD_HEADERS).
            http.addFilterBefore(new RequireHttpsFilter(), DisableEncodeUrlFilter.class);
        }
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, "/auth/register", "/auth/login", "/auth/verify-email",
                                "/auth/resend-verification", "/auth/forgot-password", "/auth/reset-password").permitAll()
                        .requestMatchers(HttpMethod.GET, "/auth/csrf").permitAll()
                        .anyRequest().authenticated())
                // Cookie-based CSRF tokens in the form single-page apps expect: the frontend reads the XSRF-TOKEN cookie
                // and sends its value back in the X-XSRF-TOKEN header on every POST, PATCH, PUT and DELETE.
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository).csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .requestCache(RequestCacheConfigurer::disable)
                .logout(logout -> logout
                        .logoutUrl("/auth/logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies(SESSION_COOKIE, CSRF_COOKIE)
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                plainText(response, HttpStatus.UNAUTHORIZED, "Authentication required"))
                        .accessDeniedHandler((request, response, exception) -> plainText(response, HttpStatus.FORBIDDEN,
                                exception instanceof CsrfException ? "Missing or invalid CSRF token" : "Access denied")))
                // The API only returns data, never pages: nothing may be framed, sniffed, or loaded from elsewhere.
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                        // Sent on HTTPS responses only: from then on browsers refuse to talk to this site over plain HTTP. Not for subdomains: other sites on the same domain are not this application's to decide for.
                        .httpStrictTransportSecurity(hsts -> hsts.maxAgeInSeconds(31_536_000).includeSubDomains(false))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER)))
                .cors(AbstractHttpConfigurer::disable);
        return http.build();
    }

    @Bean
    public CsrfTokenRepository csrfTokenRepository(@Value("${pantrypal.security.cookie-secure:false}") boolean cookieSecure) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse(); // the SPA must read it
        repository.setCookieName(CSRF_COOKIE);
        repository.setHeaderName(CSRF_HEADER);
        // setCookieCustomizer REPLACES the one withHttpOnlyFalse() installed, so httpOnly(false) has to be repeated here.
        repository.setCookieCustomizer(cookie -> {
            cookie.httpOnly(false).sameSite("Lax").path("/");
            if (cookieSecure) {
                cookie.secure(true);
            }
        });
        return repository;
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /** Run on every successful login: a new session id (so a planted one is useless) and a new CSRF token. */
    @Bean
    public SessionAuthenticationStrategy sessionAuthenticationStrategy(CsrfTokenRepository csrfTokenRepository) {
        CsrfAuthenticationStrategy rotateCsrfToken = new CsrfAuthenticationStrategy(csrfTokenRepository);
        // so the new token's cookie is written in the login response itself, not left for a later request
        rotateCsrfToken.setRequestHandler(new SpaCsrfTokenRequestHandler());
        return new CompositeSessionAuthenticationStrategy(List.of(new ChangeSessionIdAuthenticationStrategy(), rotateCsrfToken));
    }

    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        // Defaults worth naming: an unknown email takes as long to answer as a wrong password (a dummy hash is
        // checked), and its failure is reported as "bad credentials", not "no such user".
        provider.setHideUserNotFoundExceptions(true);
        return new ProviderManager(provider);
    }

    private static void plainText(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.TEXT_PLAIN_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(message);
    }
}
