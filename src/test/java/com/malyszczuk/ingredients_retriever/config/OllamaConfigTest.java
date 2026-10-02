package com.malyszczuk.ingredients_retriever.config;

import com.malyszczuk.ingredients_retriever.agent.OllamaProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OllamaConfigTest {

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/slow", exchange -> {
            try {
                Thread.sleep(1500);
                exchange.sendResponseHeaders(200, -1);
            } catch (Exception ignored) {
                // the client gave up; nothing to answer
            } finally {
                exchange.close();
            }
        });
        server.createContext("/fast", exchange -> {
            byte[] body = "ok".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private RestClient clientWithReadTimeout(Duration readTimeout) {
        OllamaProperties properties = new OllamaProperties(baseUrl, null, null, null, Duration.ofSeconds(2), readTimeout);
        return new OllamaConfig().ollamaRestClient(properties);
    }

    @Test
    void ollamaRestClient_givesUpWithATimeout_whenOllamaAnswersTooSlowly() {
        RestClient client = clientWithReadTimeout(Duration.ofMillis(300));

        ResourceAccessException exception = assertThrows(ResourceAccessException.class,
                () -> client.get().uri("/slow").retrieve().toBodilessEntity());

        assertTrue(hasCause(exception, HttpTimeoutException.class), "expected a timeout cause, got " + exception);
    }

    @Test
    void ollamaRestClient_waitsAsLongAsTheReadTimeoutAllows() {
        RestClient client = clientWithReadTimeout(Duration.ofSeconds(5));

        assertEquals("ok", client.get().uri("/fast").retrieve().body(String.class));
    }

    @Test
    void properties_haveSensibleDefaultTimeouts() {
        OllamaProperties defaults = new OllamaProperties(null, null, null, null, null, null);

        assertEquals(Duration.ofSeconds(5), defaults.connectTimeout());
        assertEquals(Duration.ofSeconds(180), defaults.readTimeout());
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return true;
            }
        }
        return false;
    }
}
