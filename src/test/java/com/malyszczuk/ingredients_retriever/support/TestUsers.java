package com.malyszczuk.ingredients_retriever.support;

import com.malyszczuk.ingredients_retriever.auth.AppUserPrincipal;
import com.malyszczuk.ingredients_retriever.domain.AppUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;

/** Users for tests that call the services directly: creates the account row and "logs in" on the current thread. */
public final class TestUsers {

    private TestUsers() {
    }

    public static AppUserPrincipal create(JdbcTemplate jdbc, String email) {
        Long id = jdbc.queryForObject(
                "INSERT INTO users (email, password_hash, created_at, enabled) VALUES (?, 'not-a-real-hash', now(), true) RETURNING id",
                Long.class, email);
        return new AppUserPrincipal(AppUser.builder().id(id).email(email).passwordHash("not-a-real-hash").build());
    }

    public static AppUserPrincipal existing(JdbcTemplate jdbc, String email) {
        Long id = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        return new AppUserPrincipal(AppUser.builder().id(id).email(email).passwordHash("not-a-real-hash").build());
    }

    /** Everything on this thread that asks "who is the user?" now gets this one. */
    public static void actAs(AppUserPrincipal user) {
        SecurityContext context = new SecurityContextImpl(
                UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
        SecurityContextHolder.setContext(context);
    }

    public static void nobody() {
        SecurityContextHolder.clearContext();
    }
}
