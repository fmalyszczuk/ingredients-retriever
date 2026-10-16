package com.malyszczuk.ingredients_retriever.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Refuses to start with HTTPS settings that cannot work, instead of failing in a way that is hard to diagnose later:
 * <ul>
 *   <li>HTTPS required but cookies not marked Secure: the session cookie would travel over plain HTTP too;</li>
 *   <li>HTTPS required, yet nothing is set up to make requests look secure: every request would be refused as
 *       "not HTTPS", forever.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class HttpsSettingsCheck implements InitializingBean {

    private final Environment environment;

    @Override
    public void afterPropertiesSet() {
        check(environment);
    }

    static void check(Environment env) {
        if (!env.getProperty("pantrypal.security.require-https", Boolean.class, false)) {
            return;
        }
        if (!env.getProperty("pantrypal.security.cookie-secure", Boolean.class, false)) {
            throw new IllegalStateException("PANTRYPAL_REQUIRE_HTTPS=true needs secure cookies: do not set PANTRYPAL_COOKIE_SECURE=false");
        }
        boolean tlsInTheApplication = env.getProperty("server.ssl.enabled", Boolean.class, true)
                && (hasText(env, "server.ssl.bundle") || hasText(env, "server.ssl.key-store"));
        String forwardHeaders = env.getProperty("server.forward-headers-strategy", "none");
        boolean behindTlsProxy = forwardHeaders.equalsIgnoreCase("native") || forwardHeaders.equalsIgnoreCase("framework");
        if (!tlsInTheApplication && !behindTlsProxy) {
            throw new IllegalStateException("PANTRYPAL_REQUIRE_HTTPS=true, but the application would never see a secure request, "
                    + "so it would refuse every request. Either serve HTTPS from the application itself (server.ssl.bundle, "
                    + "see README) or run it behind a reverse proxy that terminates TLS and set PANTRYPAL_FORWARD_HEADERS=native.");
        }
    }

    private static boolean hasText(Environment env, String property) {
        String value = env.getProperty(property);
        return value != null && !value.isBlank();
    }
}
