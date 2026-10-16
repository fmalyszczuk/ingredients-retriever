package com.malyszczuk.ingredients_retriever.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpsSettingsCheckTest {

    private MockEnvironment httpsRequired() {
        return new MockEnvironment()
                .withProperty("pantrypal.security.require-https", "true")
                .withProperty("pantrypal.security.cookie-secure", "true");
    }

    @Test
    void whenHttpsIsNotRequired_anythingGoes() {
        assertDoesNotThrow(() -> HttpsSettingsCheck.check(new MockEnvironment()));
        assertDoesNotThrow(() -> HttpsSettingsCheck.check(new MockEnvironment().withProperty("pantrypal.security.cookie-secure", "false")));
    }

    @Test
    void requiringHttps_withInsecureCookies_isRefused() {
        MockEnvironment env = httpsRequired().withProperty("pantrypal.security.cookie-secure", "false")
                .withProperty("server.forward-headers-strategy", "native");

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> HttpsSettingsCheck.check(env));
        assertTrue(failure.getMessage().contains("secure cookies"), failure.getMessage());
    }

    @Test
    void requiringHttps_withNothingThatProvidesTls_isRefused_insteadOfRefusingEveryRequestForever() {
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> HttpsSettingsCheck.check(httpsRequired()));

        assertTrue(failure.getMessage().contains("PANTRYPAL_FORWARD_HEADERS"), failure.getMessage());
    }

    @Test
    void behindATlsTerminatingProxy_isFine() {
        assertDoesNotThrow(() -> HttpsSettingsCheck.check(httpsRequired().withProperty("server.forward-headers-strategy", "native")));
        assertDoesNotThrow(() -> HttpsSettingsCheck.check(httpsRequired().withProperty("server.forward-headers-strategy", "framework")));
    }

    @Test
    void tlsInTheApplicationItself_isFine() {
        assertDoesNotThrow(() -> HttpsSettingsCheck.check(httpsRequired().withProperty("server.ssl.bundle", "web")));
        assertDoesNotThrow(() -> HttpsSettingsCheck.check(httpsRequired().withProperty("server.ssl.key-store", "keystore.p12")));
    }

    @Test
    void tlsThatIsSwitchedOff_doesNotCount() {
        MockEnvironment env = httpsRequired().withProperty("server.ssl.bundle", "web").withProperty("server.ssl.enabled", "false");

        assertThrows(IllegalStateException.class, () -> HttpsSettingsCheck.check(env));
    }
}
