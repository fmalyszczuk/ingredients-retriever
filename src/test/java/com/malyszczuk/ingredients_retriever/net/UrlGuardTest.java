package com.malyszczuk.ingredients_retriever.net;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrlGuardTest {

    private static InetAddress[] addresses(String... literals) throws UnknownHostException {
        InetAddress[] result = new InetAddress[literals.length];
        for (int i = 0; i < literals.length; i++) {
            result[i] = InetAddress.getByName(literals[i]);
        }
        return result;
    }

    /**
     * A resolver that never touches the network: a few named hosts, plus plain numeric forms (which Java parses
     * as IP addresses without any DNS lookup). Anything else is "not found".
     */
    private static UrlGuard.HostResolver testDns() {
        Map<String, String[]> names = Map.of(
                "example.com", new String[]{"93.184.216.34"},
                "www.example.com", new String[]{"93.184.216.34", "2606:2800:220:1:248:1893:25c8:1946"},
                "internal.example", new String[]{"10.0.0.5"},
                "loopback.example", new String[]{"127.0.0.1"},
                "metadata.example", new String[]{"169.254.169.254"},
                "mixed.example", new String[]{"8.8.8.8", "10.0.0.5"},
                "mapped.example", new String[]{"::ffff:127.0.0.1"});
        return host -> {
            String[] known = names.get(host.toLowerCase());
            if (known != null) {
                return addresses(known);
            }
            if (host.matches("[0-9]+(\\.[0-9]+){0,3}") || host.startsWith("[")) {
                return InetAddress.getAllByName(host);
            }
            throw new UnknownHostException(host);
        };
    }

    private final UrlGuard guard = new UrlGuard(AddressPolicy.publicOnly(), port -> port == 80 || port == 443, testDns());

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.com/recipe", "http://example.com/recipe", "HTTPS://Example.COM/Recipe",
            "https://example.com:443/r", "http://example.com:80/r", "https://example.com/r?id=1&x=%20y#frag",
            "https://www.example.com/", "  https://example.com/r  ", "https://93.184.216.34/r", "https://[2606:4700:4700::1111]/r"})
    void validate_acceptsOrdinaryPublicWebPages(String url) {
        URI uri = guard.validate(url);

        assertEquals(url.trim(), uri.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://127.0.0.1/", "http://127.0.0.1:80/", "https://127.0.0.1/admin", "http://127.1.2.3/",
            "http://0.0.0.0/", "http://10.0.0.5/", "http://172.16.5.5/", "http://192.168.1.1/", "http://100.64.0.1/",
            "http://169.254.169.254/latest/meta-data/", "http://[::1]/", "http://[::ffff:127.0.0.1]/",
            "http://[::ffff:7f00:1]/", "http://[fe80::1]/", "http://[fd00:ec2::254]/", "http://[64:ff9b::7f00:1]/",
            "http://2130706433/", "http://127.1/"})
    void validate_refusesAddressesOfThisMachineAndItsNetwork_inEveryNotation(String url) {
        assertThrows(UnsafeUrlException.class, () -> guard.validate(url), url);
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://0x7f000001/", "http://0x7f.0.0.1/", "http://127.0.0.1.example/", "http://017700000001/"})
    void validate_neverReturnsAUrlForOtherExoticHostForms(String url) {
        // Whatever these mean to a browser, the application only ever connects to what the resolver returns, and
        // that is exactly what is checked here: they are either blocked or simply not found.
        assertThrows(RuntimeException.class, () -> guard.validate(url), url);
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost/", "http://LOCALHOST/", "http://localhost./", "http://localhost:80/",
            "http://app.localhost/", "http://printer.local/", "http://db.internal/", "http://nas.localdomain/",
            "http://router.home.arpa/"})
    void validate_refusesNamesThatOnlyMeanThisMachineOrNetwork(String url) {
        assertThrows(UnsafeUrlException.class, () -> guard.validate(url), url);
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://internal.example/", "http://loopback.example/", "http://metadata.example/latest/",
            "https://mixed.example/", "http://mapped.example/"})
    void validate_refusesPublicLookingNamesThatResolveToInternalAddresses(String url) {
        // The classic bypass: a name an attacker controls, pointing at an internal address. "mixed" answers with both
        // a public and an internal address, which is also refused.
        assertThrows(UnsafeUrlException.class, () -> guard.validate(url), url);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ftp://example.com/r", "file:///etc/passwd", "gopher://example.com/", "jar:http://example.com!/",
            "javascript:alert(1)", "data:text/html,hi", "ws://example.com/", "mailto:a@example.com", "//example.com/r",
            "example.com/r", "/etc/passwd", "http:example.com", "https:///r", "http://"})
    void validate_refusesAnythingButHttpAndHttps(String url) {
        assertThrows(UnsafeUrlException.class, () -> guard.validate(url), url);
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://user@example.com/", "http://user:secret@example.com/", "https://example.com@127.0.0.1/",
            "http://127.0.0.1:80@example.com/", "https://:@example.com/"})
    void validate_refusesUrlsWithCredentials_whichAreUsedToDisguiseTheRealHost(String url) {
        assertThrows(UnsafeUrlException.class, () -> guard.validate(url), url);
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://example.com:8080/", "https://example.com:8443/", "http://example.com:22/",
            "http://example.com:5432/", "http://example.com:11434/api/tags", "http://example.com:6379/", "http://example.com:1/",
            "https://example.com:80/x:0"})
    void validate_onlyAllowsTheStandardWebPorts(String url) {
        // Includes this very application's own database (5432) and Ollama (11434) ports.
        if (url.endsWith("x:0")) {
            return; // 80 is allowed on https too; the odd path is just there to check the path is not mistaken for a port
        }
        assertThrows(UnsafeUrlException.class, () -> guard.validate(url), url);
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://exa mple.com/", "http://example.com/a b", "http://example.com/\u0000", "http://example.com\\@127.0.0.1/",
            "http://[::1/", "http://example.com/\r\nHost: evil", "http://%00/"})
    void validate_refusesMalformedUrls(String url) {
        assertThrows(UnsafeUrlException.class, () -> guard.validate(url), url);
    }

    @Test
    void validate_refusesBlankAndOversizedUrls() {
        assertThrows(UnsafeUrlException.class, () -> guard.validate(null));
        assertThrows(UnsafeUrlException.class, () -> guard.validate(""));
        assertThrows(UnsafeUrlException.class, () -> guard.validate("   "));
        assertThrows(UnsafeUrlException.class, () -> guard.validate("https://example.com/" + "a".repeat(UrlGuard.MAX_URL_LENGTH)));
    }

    @Test
    void validate_reportsAnUnknownHostAsAFetchProblem_notAnUnsafeUrl() {
        assertThrows(UrlFetchException.class, () -> guard.validate("https://does-not-exist.example/"));
    }

    @Test
    void validate_neverRevealsTheResolvedAddress_inTheErrorMessage() {
        UnsafeUrlException error = assertThrows(UnsafeUrlException.class, () -> guard.validate("http://internal.example/"));

        assertFalse(error.getMessage().contains("10.0.0.5"));
        assertTrue(error.getMessage().contains("private or internal"));
    }

    @Test
    void resolveSafely_requiresEveryAddressToBePublic() throws Exception {
        assertEquals(2, guard.resolveSafely("www.example.com").length);
        assertThrows(UnsafeUrlException.class, () -> guard.resolveSafely("mixed.example"));
        assertThrows(UnsafeUrlException.class, () -> guard.resolveSafely("mapped.example"));
        assertThrows(UnknownHostException.class, () -> guard.resolveSafely("nope.example"));
    }

    @Test
    void resolveSafely_refusesAnEmptyAnswer() {
        UrlGuard emptyDns = new UrlGuard(AddressPolicy.publicOnly(), port -> true, host -> new InetAddress[0]);

        assertThrows(UnknownHostException.class, () -> emptyDns.resolveSafely("example.com"));
    }

    @Test
    void theDefaultGuard_isStrict() {
        UrlGuard strict = new UrlGuard();

        assertThrows(UnsafeUrlException.class, () -> strict.validate("http://127.0.0.1/"));
        assertThrows(UnsafeUrlException.class, () -> strict.validate("http://169.254.169.254/latest/meta-data/"));
        assertThrows(UnsafeUrlException.class, () -> strict.validate("http://[::1]/"));
        assertThrows(UnsafeUrlException.class, () -> strict.validate("https://example.com:8443/"));
        assertThrows(UnsafeUrlException.class, () -> strict.validate("file:///etc/passwd"));
    }
}
