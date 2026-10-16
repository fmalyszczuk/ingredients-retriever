package com.malyszczuk.ingredients_retriever.net;

import jakarta.annotation.PreDestroy;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;

/**
 * Downloads a web page the user pointed us at, safely. Every URL, including each redirect target, goes through
 * {@link UrlGuard}; the connection itself can only go to addresses the guard approved (the HTTP client resolves
 * names through {@link UrlGuard#resolveSafely}); redirects are followed by hand, and the time, redirects and
 * download size are all capped.
 */
@Component
public class SafePageFetcher {

    public record Limits(int maxBytes, int maxRedirects, Duration connectTimeout, Duration readTimeout, Duration totalTimeout) {

        public static Limits standard() {
            return new Limits(5 * 1024 * 1024, 5, Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(30));
        }
    }

    private static final String USER_AGENT = "Mozilla/5.0 (compatible; PantryPalBot/1.0)";
    private static final Set<Integer> REDIRECT_STATUSES = Set.of(301, 302, 303, 307, 308);
    private static final Set<String> PAGE_TYPES = Set.of("text/html", "application/xhtml+xml");

    private final UrlGuard guard;
    private final Limits limits;
    private final CloseableHttpClient client;

    @Autowired
    public SafePageFetcher(UrlGuard guard) {
        this(guard, Limits.standard());
    }

    public SafePageFetcher(UrlGuard guard, Limits limits) {
        this.guard = guard;
        this.limits = limits;

        DnsResolver pinnedResolver = new DnsResolver() {
            @Override
            public java.net.InetAddress[] resolve(String host) throws java.net.UnknownHostException {
                return guard.resolveSafely(host);
            }

            @Override
            public String resolveCanonicalHostname(String host) {
                return host;
            }
        };
        PoolingHttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setDnsResolver(pinnedResolver)
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(Timeout.of(limits.connectTimeout()))
                        .setSocketTimeout(Timeout.of(limits.readTimeout()))
                        .build())
                .setMaxConnTotal(20)
                .setMaxConnPerRoute(5)
                .build();
        this.client = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setUserAgent(USER_AGENT)
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectionRequestTimeout(Timeout.of(limits.connectTimeout()))
                        .setResponseTimeout(Timeout.of(limits.readTimeout()))
                        .build())
                .disableRedirectHandling()
                .disableCookieManagement()
                .disableAuthCaching()
                .disableAutomaticRetries()
                .build();
    }

    @PreDestroy
    public void close() throws IOException {
        client.close();
    }

    /**
     * @throws UnsafeUrlException if the URL, or any redirect it leads to, must not be fetched
     * @throws UrlFetchException  for everything else that goes wrong while fetching
     */
    public FetchedPage fetch(String url) {
        long deadline = System.nanoTime() + limits.totalTimeout().toNanos();
        URI current = guard.validate(url);

        for (int redirects = 0; ; redirects++) {
            checkDeadline(deadline);
            try (ClassicHttpResponse response = client.executeOpen(null, new HttpGet(current), null)) {
                int status = response.getCode();

                if (REDIRECT_STATUSES.contains(status)) {
                    if (redirects >= limits.maxRedirects()) {
                        throw new UrlFetchException("The page redirected too many times");
                    }
                    current = guard.validate(redirectTarget(current, response));
                    EntityUtils.consumeQuietly(response.getEntity());
                    continue;
                }
                if (status < 200 || status >= 300) {
                    EntityUtils.consumeQuietly(response.getEntity());
                    throw new UrlFetchException("The page answered with HTTP " + status);
                }
                return readPage(current, response.getEntity(), deadline);
            } catch (IOException e) {
                throw new UrlFetchException("The page could not be fetched (" + e.getClass().getSimpleName()
                        + (e.getMessage() == null ? "" : ": " + e.getMessage()) + ")", e);
            }
        }
    }

    private String redirectTarget(URI current, ClassicHttpResponse response) {
        Header location = response.getFirstHeader("Location");
        if (location == null || location.getValue().isBlank()) {
            throw new UrlFetchException("The page redirected without saying where");
        }
        try {
            // Relative redirects are resolved against the current URL, then checked like any other URL.
            return current.resolve(new URI(location.getValue().trim())).toString();
        } catch (URISyntaxException e) {
            throw new UnsafeUrlException("The page redirected to something that is not a valid URL");
        }
    }

    private FetchedPage readPage(URI url, HttpEntity entity, long deadline) throws IOException {
        if (entity == null) {
            throw new UrlFetchException("The page was empty");
        }

        Charset charset = null;
        String contentType = entity.getContentType();
        if (contentType != null) {
            ContentType parsed;
            try {
                parsed = ContentType.parse(contentType);
            } catch (RuntimeException e) {
                throw new UrlFetchException("The page has an invalid content type");
            }
            String mimeType = parsed.getMimeType() == null ? "" : parsed.getMimeType().toLowerCase(Locale.ROOT);
            if (!PAGE_TYPES.contains(mimeType)) {
                throw new UrlFetchException("That URL is not a web page (it returned " + mimeType + ")");
            }
            charset = parsed.getCharset();
        }

        if (entity.getContentLength() > limits.maxBytes()) {
            throw new UrlFetchException("The page is too large");
        }

        ByteArrayOutputStream body = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        try (InputStream in = entity.getContent()) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > limits.maxBytes()) {
                    // Counted after decompression, so a tiny gzip that expands enormously is stopped here too.
                    throw new UrlFetchException("The page is too large");
                }
                checkDeadline(deadline);
                body.write(buffer, 0, read);
            }
        }
        return new FetchedPage(url, body.toByteArray(), charset);
    }

    private void checkDeadline(long deadline) {
        if (System.nanoTime() > deadline) {
            throw new UrlFetchException("The page took too long to download");
        }
    }
}
