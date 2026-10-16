package com.malyszczuk.ingredients_retriever.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Refuses every request that did not arrive over HTTPS (403, before anything else looks at it, so not even a cookie is
 * set). Redirecting would be friendlier, but by then a password in a POST body has already crossed the network in the
 * clear, and the API is not something people type into an address bar: the proxy in front of it redirects browsers, and
 * a plain-HTTP request reaching this point means something is misconfigured. Spring Security's own channel
 * redirect is not used because it silently lets the request through when it has no HTTPS port to redirect to.
 * <p>
 * "Secure" is what the servlet container says: a TLS connection to the application itself, or, behind a reverse proxy
 * that terminates TLS, an X-Forwarded-Proto: https header from a trusted proxy (server.forward-headers-strategy=native).
 */
public class RequireHttpsFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.isSecure()) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.TEXT_PLAIN_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("HTTPS required");
    }
}
