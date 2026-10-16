package com.malyszczuk.ingredients_retriever.net;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The fetcher against real HTTP servers on this machine. The servers listen on loopback, so most tests use a guard
 * that allows 127.0.0.1 (and only that); the SSRF tests then use the real, strict policy and prove the servers
 * are never even contacted.
 */
class SafePageFetcherTest {

    private static final String HTML = "<html><body>hello</body></html>";

    private final List<HttpServer> servers = new ArrayList<>();
    private final List<SafePageFetcher> fetchers = new ArrayList<>();

    @AfterEach
    void shutDown() throws IOException {
        servers.forEach(server -> server.stop(0));
        for (SafePageFetcher fetcher : fetchers) {
            fetcher.close();
        }
    }

    private HttpServer startServer(String address, String path, HttpHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(address, 0), 0);
        server.createContext(path, handler);
        server.start();
        servers.add(server);
        return server;
    }

    private HttpServer startServer(String path, HttpHandler handler) throws IOException {
        return startServer("127.0.0.1", path, handler);
    }

    private static String url(HttpServer server, String host, String path) {
        return "http://" + host + ":" + server.getAddress().getPort() + path;
    }

    private static void respond(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        if (contentType != null) {
            exchange.getResponseHeaders().set("Content-Type", contentType);
        }
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        if (body.length > 0) {
            exchange.getResponseBody().write(body);
        }
        exchange.close();
    }

    private static void redirect(HttpExchange exchange, int status, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(status, -1);
        exchange.close();
    }

    /** Allows 127.0.0.1 only: the other loopback address, 127.0.0.2, stands in for "an internal address". */
    private SafePageFetcher permissiveFetcher(SafePageFetcher.Limits limits) {
        UrlGuard guard = new UrlGuard(address -> address.getHostAddress().equals("127.0.0.1"), port -> true, InetAddress::getAllByName);
        SafePageFetcher fetcher = new SafePageFetcher(guard, limits);
        fetchers.add(fetcher);
        return fetcher;
    }

    private SafePageFetcher permissiveFetcher() {
        return permissiveFetcher(SafePageFetcher.Limits.standard());
    }

    private SafePageFetcher.Limits limits(int maxBytes, int maxRedirects, Duration readTimeout) {
        return new SafePageFetcher.Limits(maxBytes, maxRedirects, Duration.ofSeconds(2), readTimeout, Duration.ofSeconds(10));
    }

    // ---------------------------------------------------------------- normal behaviour

    @Test
    void fetch_returnsThePage_itsFinalUrl_andTheDeclaredCharset() throws Exception {
        HttpServer server = startServer("/page", exchange ->
                respond(exchange, 200, "text/html; charset=ISO-8859-1", HTML.getBytes(StandardCharsets.ISO_8859_1)));

        FetchedPage page = permissiveFetcher().fetch(url(server, "127.0.0.1", "/page"));

        assertEquals(url(server, "127.0.0.1", "/page"), page.finalUrl().toString());
        assertArrayEquals(HTML.getBytes(StandardCharsets.ISO_8859_1), page.body());
        assertEquals(StandardCharsets.ISO_8859_1, page.charset());
    }

    @Test
    void fetch_reportsNoCharset_whenTheServerDidNotDeclareOne() throws Exception {
        HttpServer server = startServer("/page", exchange -> respond(exchange, 200, "text/html", HTML.getBytes(StandardCharsets.UTF_8)));

        assertNull(permissiveFetcher().fetch(url(server, "127.0.0.1", "/page")).charset());
    }

    @Test
    void fetch_acceptsXhtml_andAMissingContentType() throws Exception {
        HttpServer xhtml = startServer("/x", exchange -> respond(exchange, 200, "application/xhtml+xml", HTML.getBytes()));
        HttpServer untyped = startServer("/u", exchange -> {
            exchange.sendResponseHeaders(200, HTML.length());
            exchange.getResponseBody().write(HTML.getBytes());
            exchange.close();
        });
        SafePageFetcher fetcher = permissiveFetcher();

        assertEquals(HTML.length(), fetcher.fetch(url(xhtml, "127.0.0.1", "/x")).body().length);
        assertEquals(HTML.length(), fetcher.fetch(url(untyped, "127.0.0.1", "/u")).body().length);
    }

    @Test
    void fetch_followsRedirects_relativeAndAbsolute_andReportsTheFinalUrl() throws Exception {
        HttpServer server = startServer("/", exchange -> {
            switch (exchange.getRequestURI().getPath()) {
                case "/start" -> redirect(exchange, 301, "/middle");
                case "/middle" -> redirect(exchange, 302, "http://127.0.0.1:" + exchange.getLocalAddress().getPort() + "/final");
                default -> respond(exchange, 200, "text/html", HTML.getBytes());
            }
        });

        FetchedPage page = permissiveFetcher().fetch(url(server, "127.0.0.1", "/start"));

        assertEquals(url(server, "127.0.0.1", "/final"), page.finalUrl().toString());
        assertEquals(HTML, new String(page.body()));
    }

    @Test
    void fetch_decompressesGzip() throws Exception {
        HttpServer server = startServer("/gz", exchange -> {
            ByteArrayOutputStream compressed = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
                gzip.write(HTML.getBytes());
            }
            exchange.getResponseHeaders().set("Content-Encoding", "gzip");
            respond(exchange, 200, "text/html", compressed.toByteArray());
        });

        assertEquals(HTML, new String(permissiveFetcher().fetch(url(server, "127.0.0.1", "/gz")).body()));
    }

    @Test
    void fetch_doesNotSendCredentialsOrCookiesAndIdentifiesItself() throws Exception {
        List<String> seen = new ArrayList<>();
        HttpServer server = startServer("/h", exchange -> {
            seen.add(String.valueOf(exchange.getRequestHeaders().getFirst("User-Agent")));
            seen.add(String.valueOf(exchange.getRequestHeaders().getFirst("Cookie")));
            seen.add(String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
            exchange.getResponseHeaders().add("Set-Cookie", "session=abc");
            respond(exchange, 200, "text/html", HTML.getBytes());
        });
        SafePageFetcher fetcher = permissiveFetcher();

        fetcher.fetch(url(server, "127.0.0.1", "/h"));
        fetcher.fetch(url(server, "127.0.0.1", "/h"));

        assertTrue(seen.get(0).contains("PantryPalBot"));
        assertEquals(List.of("null", "null"), List.of(seen.get(1), seen.get(2)));
        assertEquals("null", seen.get(4), "a cookie set by the first response must not be sent back on the second request");
    }

    // ---------------------------------------------------------------- failures that are not about safety

    @Test
    void fetch_reportsHttpErrors() throws Exception {
        HttpServer server = startServer("/", exchange -> respond(exchange, exchange.getRequestURI().getPath().equals("/403") ? 403 : 404, "text/html", "no".getBytes()));
        SafePageFetcher fetcher = permissiveFetcher();

        assertTrue(assertThrows(UrlFetchException.class, () -> fetcher.fetch(url(server, "127.0.0.1", "/403"))).getMessage().contains("HTTP 403"));
        assertTrue(assertThrows(UrlFetchException.class, () -> fetcher.fetch(url(server, "127.0.0.1", "/missing"))).getMessage().contains("HTTP 404"));
    }

    @Test
    void fetch_refusesThingsThatAreNotWebPages() throws Exception {
        HttpServer json = startServer("/j", exchange -> respond(exchange, 200, "application/json", "{}".getBytes()));
        HttpServer image = startServer("/i", exchange -> respond(exchange, 200, "image/png", new byte[]{1, 2, 3}));
        HttpServer pdf = startServer("/p", exchange -> respond(exchange, 200, "application/pdf", new byte[]{1}));
        SafePageFetcher fetcher = permissiveFetcher();

        assertThrows(UrlFetchException.class, () -> fetcher.fetch(url(json, "127.0.0.1", "/j")));
        assertThrows(UrlFetchException.class, () -> fetcher.fetch(url(image, "127.0.0.1", "/i")));
        assertThrows(UrlFetchException.class, () -> fetcher.fetch(url(pdf, "127.0.0.1", "/p")));
    }

    @Test
    void fetch_stopsAtTheSizeLimit_whetherOrNotTheServerAnnouncesTheSize() throws Exception {
        byte[] big = "x".repeat(5000).getBytes();
        HttpServer announced = startServer("/a", exchange -> respond(exchange, 200, "text/html", big));
        HttpServer streamed = startServer("/s", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, 0); // chunked: the size is never announced
            exchange.getResponseBody().write(big);
            exchange.close();
        });
        SafePageFetcher fetcher = permissiveFetcher(limits(1000, 5, Duration.ofSeconds(5)));

        assertTrue(assertThrows(UrlFetchException.class, () -> fetcher.fetch(url(announced, "127.0.0.1", "/a"))).getMessage().contains("too large"));
        assertTrue(assertThrows(UrlFetchException.class, () -> fetcher.fetch(url(streamed, "127.0.0.1", "/s"))).getMessage().contains("too large"));
    }

    @Test
    void fetch_stopsADecompressionBomb_bySizeAfterDecompressing() throws Exception {
        HttpServer server = startServer("/bomb", exchange -> {
            ByteArrayOutputStream compressed = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
                gzip.write(new byte[5 * 1024 * 1024]); // 5 MB of zeros: about 5 KB on the wire
            }
            exchange.getResponseHeaders().set("Content-Encoding", "gzip");
            respond(exchange, 200, "text/html", compressed.toByteArray());
        });
        SafePageFetcher fetcher = permissiveFetcher(limits(100_000, 5, Duration.ofSeconds(5)));

        assertTrue(assertThrows(UrlFetchException.class, () -> fetcher.fetch(url(server, "127.0.0.1", "/bomb"))).getMessage().contains("too large"));
    }

    @Test
    void fetch_givesUpOnAServerThatAnswersTooSlowly() throws Exception {
        HttpServer server = startServer("/slow", exchange -> {
            try {
                Thread.sleep(2000);
                respond(exchange, 200, "text/html", HTML.getBytes());
            } catch (Exception ignored) {
                exchange.close();
            }
        });
        SafePageFetcher fetcher = permissiveFetcher(limits(10_000, 5, Duration.ofMillis(300)));

        long started = System.nanoTime();
        assertThrows(UrlFetchException.class, () -> fetcher.fetch(url(server, "127.0.0.1", "/slow")));

        assertTrue(Duration.ofNanos(System.nanoTime() - started).toMillis() < 1800, "must not wait for the slow server");
    }

    @Test
    void fetch_stopsFollowingRedirects_afterTheLimit() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = startServer("/loop", exchange -> {
            requests.incrementAndGet();
            redirect(exchange, 302, "/loop");
        });
        SafePageFetcher fetcher = permissiveFetcher(limits(10_000, 3, Duration.ofSeconds(5)));

        assertTrue(assertThrows(UrlFetchException.class, () -> fetcher.fetch(url(server, "127.0.0.1", "/loop"))).getMessage().contains("redirected too many times"));
        assertEquals(4, requests.get(), "the first request plus three redirects");
    }

    @Test
    void fetch_reportsARedirectWithoutALocation() throws Exception {
        HttpServer server = startServer("/r", exchange -> {
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });

        assertThrows(UrlFetchException.class, () -> permissiveFetcher().fetch(url(server, "127.0.0.1", "/r")));
    }

    @Test
    void fetch_reportsAnUnreachableServerAsAFetchProblem() throws Exception {
        HttpServer server = startServer("/x", exchange -> respond(exchange, 200, "text/html", HTML.getBytes()));
        String closedPortUrl = url(server, "127.0.0.1", "/x");
        server.stop(0);

        assertThrows(UrlFetchException.class, () -> permissiveFetcher().fetch(closedPortUrl));
    }

    // ---------------------------------------------------------------- SSRF: the point of all this

    @Test
    void fetch_withTheRealPolicy_neverContactsALoopbackServer() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer secret = startServer("/", exchange -> {
            requests.incrementAndGet();
            respond(exchange, 200, "text/html", "internal admin panel".getBytes());
        });
        // Strict address policy; the port restriction is lifted only because the test server has a random port.
        SafePageFetcher strict = new SafePageFetcher(new UrlGuard(AddressPolicy.publicOnly(), port -> true, InetAddress::getAllByName));
        fetchers.add(strict);

        assertThrows(UnsafeUrlException.class, () -> strict.fetch(url(secret, "127.0.0.1", "/admin")));
        assertThrows(UnsafeUrlException.class, () -> strict.fetch(url(secret, "localhost", "/admin")));
        assertThrows(UnsafeUrlException.class, () -> strict.fetch("http://[::1]:" + secret.getAddress().getPort() + "/admin"));
        assertEquals(0, requests.get(), "the internal server must never have been contacted");
    }

    @Test
    void fetch_refusesARedirectToAnInternalAddress_andNeverContactsIt() throws Exception {
        AtomicInteger internalRequests = new AtomicInteger();
        HttpServer internal;
        try {
            internal = startServer("127.0.0.2", "/", exchange -> {
                internalRequests.incrementAndGet();
                respond(exchange, 200, "text/html", "secret".getBytes());
            });
        } catch (BindException e) {
            Assumptions.abort("127.0.0.2 cannot be bound on this machine");
            return;
        }
        HttpServer attacker = startServer("/", exchange -> redirect(exchange, 302, "http://127.0.0.2:" + internal.getAddress().getPort() + "/secret"));

        assertThrows(UnsafeUrlException.class, () -> permissiveFetcher().fetch(url(attacker, "127.0.0.1", "/recipe")));

        assertEquals(0, internalRequests.get(), "the redirect target must never have been contacted");
    }

    @Test
    void fetch_refusesRedirectsToOtherSchemesAndToUrlsWithCredentials() throws Exception {
        for (String target : List.of("file:///etc/passwd", "ftp://127.0.0.1/x", "gopher://127.0.0.1/x", "javascript:alert(1)",
                "http://user:pw@127.0.0.1/x", "//127.0.0.1@evil/x")) {
            HttpServer attacker = startServer("/", exchange -> redirect(exchange, 302, target));

            assertThrows(UnsafeUrlException.class, () -> permissiveFetcher().fetch(url(attacker, "127.0.0.1", "/")), target);
        }
    }

    @Test
    void fetch_isNotFooledByDnsRebinding_becauseItConnectsToTheAddressesItChecked() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer internal = startServer("/", exchange -> {
            requests.incrementAndGet();
            respond(exchange, 200, "text/html", "internal admin panel".getBytes());
        });
        // The attacker's DNS answers "8.8.8.8" (public) when the URL is checked, and "127.0.0.1" afterwards, when
        // the connection is made. A check-then-connect implementation would be fooled; resolving through the guard is not.
        AtomicInteger lookups = new AtomicInteger();
        UrlGuard guard = new UrlGuard(AddressPolicy.publicOnly(), port -> true, host -> {
            if (host.equals("rebind.example")) {
                return new InetAddress[]{InetAddress.getByName(lookups.incrementAndGet() == 1 ? "8.8.8.8" : "127.0.0.1")};
            }
            return InetAddress.getAllByName(host);
        });
        SafePageFetcher fetcher = new SafePageFetcher(guard);
        fetchers.add(fetcher);

        Throwable thrown = assertThrows(Throwable.class, () -> fetcher.fetch(url(internal, "rebind.example", "/admin")));

        assertTrue(lookups.get() >= 2, "the name must have been looked up again for the connection");
        assertEquals(0, requests.get(), "the internal server must never have been contacted; got: " + thrown);
        assertTrue(thrown instanceof UnsafeUrlException, "should be reported as an unsafe URL, got: " + thrown);
    }

    @Test
    void fetch_refusesANameThatResolvesToBothPublicAndInternalAddresses() throws Exception {
        UrlGuard guard = new UrlGuard(AddressPolicy.publicOnly(), port -> true, host ->
                new InetAddress[]{InetAddress.getByName("8.8.8.8"), InetAddress.getByName("127.0.0.1")});
        SafePageFetcher fetcher = new SafePageFetcher(guard);
        fetchers.add(fetcher);

        assertThrows(UnsafeUrlException.class, () -> fetcher.fetch("http://mixed.example/"));
    }
}
