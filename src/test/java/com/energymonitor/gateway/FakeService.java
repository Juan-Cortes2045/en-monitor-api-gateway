package com.energymonitor.gateway;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * A service behind the gateway, on a free local port: it records every request it receives and
 * answers 200 with {@code {"service": name}}. A path containing {@code /slow} answers after
 * two seconds.
 */
public final class FakeService implements AutoCloseable {

    /** A request as the service received it; header names in lower case. */
    public record Received(String method, String path, Map<String, List<String>> headers, String body) {

        public String header(String name) {
            List<String> values = headers.get(name.toLowerCase());
            return values == null ? null : String.join(",", values);
        }
    }

    private final String name;
    private final HttpServer server;
    private final List<Received> received = new CopyOnWriteArrayList<>();

    public FakeService(String name) {
        this.name = name;
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/", this::handle);
        server.start();
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public List<Received> received() {
        return received;
    }

    public Received last() {
        return received.isEmpty() ? null : received.get(received.size() - 1);
    }

    public void reset() {
        received.clear();
    }

    private void handle(HttpExchange exchange) throws IOException {
        Map<String, List<String>> headers = exchange.getRequestHeaders().entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().toLowerCase(), Map.Entry::getValue,
                        (a, b) -> a));
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String path = exchange.getRequestURI().getRawPath();
        String query = exchange.getRequestURI().getRawQuery();
        received.add(new Received(exchange.getRequestMethod(), query == null ? path : path + "?" + query,
                headers, body));
        if (path.contains("/slow")) {
            try {
                Thread.sleep(2_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        byte[] answer = ("{\"service\":\"" + name + "\"}").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, answer.length);
        exchange.getResponseBody().write(answer);
        exchange.close();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
