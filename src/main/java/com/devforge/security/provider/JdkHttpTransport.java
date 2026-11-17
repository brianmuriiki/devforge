package com.devforge.security.provider;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** {@link HttpTransport} over the JDK HTTP client: HTTPS only, hard timeouts, bounded responses. */
public final class JdkHttpTransport implements HttpTransport {

    private static final int MAX_RESPONSE_CHARS = 8_000_000;

    private final HttpClient client;
    private final Duration timeout;
    private final String userAgent;

    public JdkHttpTransport(Duration timeout, String appVersion) {
        this.timeout = timeout;
        this.userAgent = "DevForge/" + appVersion;
        this.client = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public String get(String url) throws IOException {
        return send(builder(url).GET().build());
    }

    @Override
    public String post(String url, String jsonBody) throws IOException {
        return send(builder(url)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build());
    }

    private HttpRequest.Builder builder(String url) throws IOException {
        URI uri = URI.create(url);
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IOException("Refusing non-HTTPS request");
        }
        return HttpRequest.newBuilder(uri).timeout(timeout).header("User-Agent", userAgent);
    }

    private String send(HttpRequest request) throws IOException {
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                throw new IOException("HTTP " + response.statusCode() + " from " + request.uri().getHost());
            }
            String body = response.body();
            if (body.length() > MAX_RESPONSE_CHARS) {
                throw new IOException("Response from " + request.uri().getHost() + " is too large");
            }
            return body;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request interrupted", e);
        }
    }
}
