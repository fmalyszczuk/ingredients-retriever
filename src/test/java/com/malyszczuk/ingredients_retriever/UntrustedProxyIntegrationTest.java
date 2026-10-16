package com.malyszczuk.ingredients_retriever;

import com.malyszczuk.ingredients_retriever.support.HttpBrowser;
import com.malyszczuk.ingredients_retriever.support.PostgresIntegrationTest;
import com.malyszczuk.ingredients_retriever.support.RequiresDocker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Forwarded headers are only believed from the proxies that are trusted. Here the only trusted proxy is some other
 * machine, so the same headers sent from this one (as anyone on the internet could send them) must change nothing: they
 * cannot make a plain-HTTP request look secure, or hide the real address from the rate limits.
 */
@RequiresDocker
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "pantrypal.security.bcrypt-strength=4",
        "pantrypal.security.require-https=true",
        "server.forward-headers-strategy=native",
        "server.tomcat.remoteip.internal-proxies=10\\.99\\.99\\.99"})
class UntrustedProxyIntegrationTest extends PostgresIntegrationTest {

    @LocalServerPort
    private int port;

    @Test
    void forwardedHeadersFromAnUntrustedSender_areIgnored() throws Exception {
        HttpResponse<String> response = new HttpBrowser(port).send("GET", "/auth/csrf", null,
                Map.of("X-Forwarded-Proto", "https", "X-Forwarded-For", "203.0.113.7"));

        assertEquals(403, response.statusCode());
        assertEquals("HTTPS required", response.body());
    }
}
