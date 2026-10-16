package com.malyszczuk.ingredients_retriever.support;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A minimal browser for tests that talk to a real running server: it keeps cookies the way a browser would (and
 * drops ones the server expires), and nothing else. Cookie handling is done by hand so tests can see every Set-Cookie.
 */
public class HttpBrowser {

    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private final int port;
    private final Map<String, String> cookies = new LinkedHashMap<>();
    private List<String> lastSetCookies = List.of();

    public HttpBrowser(int port) {
        this.port = port;
    }

    public Map<String, String> cookies() {
        return cookies;
    }

    public List<String> lastSetCookies() {
        return lastSetCookies;
    }

    /** The Set-Cookie header for one cookie name from the last response, or null. */
    public String lastSetCookie(String name) {
        return lastSetCookies.stream().filter(header -> header.startsWith(name + "=")).findFirst().orElse(null);
    }

    public HttpResponse<String> send(String method, String path, String json, Map<String, String> headers) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (json == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", "application/json");
            request.method(method, HttpRequest.BodyPublishers.ofString(json));
        }
        headers.forEach(request::header);
        if (!cookies.isEmpty()) {
            request.header("Cookie", cookies.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining("; ")));
        }

        HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        remember(response);
        return response;
    }

    public HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return send("GET", path, null, Map.of());
    }

    /** A change request carrying the CSRF token exactly as the frontend does: cookie value copied into the header. */
    public HttpResponse<String> sendWithCsrf(String method, String path, String json) throws IOException, InterruptedException {
        String token = cookies.get("XSRF-TOKEN");
        return send(method, path, json, token == null ? Map.of() : Map.of("X-XSRF-TOKEN", token));
    }

    private void remember(HttpResponse<String> response) {
        lastSetCookies = new ArrayList<>(response.headers().allValues("Set-Cookie"));
        for (String header : lastSetCookies) {
            String pair = header.split(";", 2)[0];
            int equals = pair.indexOf('=');
            String name = pair.substring(0, equals).trim();
            String value = pair.substring(equals + 1).trim();
            boolean expired = header.toLowerCase().contains("max-age=0") || value.isEmpty();
            if (expired) {
                cookies.remove(name);
            } else {
                cookies.put(name, value);
            }
        }
    }
}
