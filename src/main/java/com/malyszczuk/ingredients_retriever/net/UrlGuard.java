package com.malyszczuk.ingredients_retriever.net;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import java.util.function.IntPredicate;

/**
 * The single gate every user-supplied URL passes before the server fetches it (SSRF protection).
 *
 * <p>{@link #validate} checks the URL's shape: only http(s), only the standard web ports, no credentials, and a host
 * that resolves exclusively to public addresses. {@link #resolveSafely} is the DNS lookup itself, and the HTTP client
 * is configured to use it for its real connection, so the addresses that get connected to are exactly the ones that
 * were checked. That closes the DNS-rebinding gap (a name that answers "public" to the check and "internal" to the
 * connection).
 */
@Component
public class UrlGuard {

    static final int MAX_URL_LENGTH = 2048;

    private static final String PRIVATE_ADDRESS_MESSAGE =
            "The URL points to a private or internal network address, so it can't be fetched";

    // Names that can only mean "this machine / this network". Resolution would block them too; this gives a clear error.
    private static final List<String> INTERNAL_SUFFIXES = List.of(
            ".localhost", ".local", ".internal", ".localdomain", ".home.arpa");

    @FunctionalInterface
    public interface HostResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    private final AddressPolicy addressPolicy;
    private final IntPredicate portPolicy;
    private final HostResolver resolver;

    /** Production configuration: public addresses only, ports 80 and 443 only, the system's DNS. */
    @Autowired
    public UrlGuard() {
        this(AddressPolicy.publicOnly(), port -> port == 80 || port == 443, InetAddress::getAllByName);
    }

    public UrlGuard(AddressPolicy addressPolicy, IntPredicate portPolicy, HostResolver resolver) {
        this.addressPolicy = addressPolicy;
        this.portPolicy = portPolicy;
        this.resolver = resolver;
    }

    /**
     * @return the parsed URL, if it is safe to fetch
     * @throws UnsafeUrlException   if the URL (or the address it resolves to) must not be fetched
     * @throws UrlFetchException    if the host can't be found at all
     */
    public URI validate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new UnsafeUrlException("Give the URL of a recipe page");
        }
        // strip(), not trim(): trim() also removes control characters such as NUL, which must be rejected, not hidden.
        String trimmed = raw.strip();
        if (trimmed.length() > MAX_URL_LENGTH) {
            throw new UnsafeUrlException("The URL is too long");
        }

        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException e) {
            throw new UnsafeUrlException("That is not a valid URL");
        }

        String scheme = uri.getScheme();
        boolean https = "https".equalsIgnoreCase(scheme);
        if (!https && !"http".equalsIgnoreCase(scheme)) {
            throw new UnsafeUrlException("Only http and https URLs can be fetched");
        }
        if (uri.getRawUserInfo() != null) {
            throw new UnsafeUrlException("URLs containing a user name or password are not allowed");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new UnsafeUrlException("That is not a valid URL");
        }
        int port = uri.getPort() != -1 ? uri.getPort() : (https ? 443 : 80);
        if (!portPolicy.test(port)) {
            throw new UnsafeUrlException("Only the standard web ports (80 and 443) can be used");
        }
        if (isInternalName(host)) {
            throw new UnsafeUrlException(PRIVATE_ADDRESS_MESSAGE);
        }

        try {
            resolveSafely(host);
        } catch (UnknownHostException e) {
            throw new UrlFetchException("The host '" + host + "' could not be found", e);
        }
        return uri;
    }

    /**
     * Resolves a host name, and refuses unless EVERY address it resolves to is allowed (a name that mixes public and
     * internal addresses is a rebinding technique). Also used as the HTTP client's DNS resolver.
     */
    public InetAddress[] resolveSafely(String host) throws UnknownHostException {
        InetAddress[] addresses = resolver.resolve(host);
        if (addresses == null || addresses.length == 0) {
            throw new UnknownHostException(host);
        }
        for (InetAddress address : addresses) {
            if (!addressPolicy.allows(address)) {
                throw new UnsafeUrlException(PRIVATE_ADDRESS_MESSAGE);
            }
        }
        return addresses;
    }

    private boolean isInternalName(String host) {
        String name = host.toLowerCase(Locale.ROOT);
        while (name.endsWith(".")) {
            name = name.substring(0, name.length() - 1);
        }
        return name.equals("localhost") || INTERNAL_SUFFIXES.stream().anyMatch(name::endsWith);
    }
}
