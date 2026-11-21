package com.beauver.printbridge.spoolman;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** The client against a small local HTTP server that plays Spoolman. */
class SpoolmanClientTest {

    HttpServer server;
    SpoolmanClient client;
    int status = 200;
    final Map<String, String> bodies = new HashMap<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::answer);
        server.start();
        client = new SpoolmanClient();
        client.objectMapper = new ObjectMapper();
        client.url = Optional.of("http://127.0.0.1:" + server.getAddress().getPort() + "//");
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private void answer(HttpExchange exchange) throws IOException {
        byte[] bytes = bodies.getOrDefault(exchange.getRequestURI().getPath(), "{}").getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Test
    void readsTheVersion() {
        bodies.put("/api/v1/info", "{\"version\": \"0.22.1\", \"debug_mode\": false}");
        assertTrue(client.isConfigured());
        assertEquals("0.22.1", client.version());
        assertTrue(client.address().startsWith("http://127.0.0.1:"));
    }

    @Test
    void combinesFilamentsWithWhatIsLeftOnTheirSpools() {
        bodies.put("/api/v1/filament", """
                [{"id": 1, "name": "PLA Basic", "vendor": {"id": 3, "name": "Bambu Lab"}, "material": "PLA",
                  "color_hex": "ff0000"},
                 {"id": 2, "name": "Silk Dual", "material": "PLA", "color_hex": "000000",
                  "multi_color_hexes": "FF0000,00ff00ff,bad", "multi_color_direction": "coaxial"},
                 {"id": 3, "name": "  ", "material": "PETG", "color_hex": "#12345"},
                 {"id": 4, "material": "ABS"}]
                """);
        bodies.put("/api/v1/spool", """
                [{"id": 10, "filament": {"id": 1}, "remaining_weight": 400.5, "archived": false},
                 {"id": 11, "filament": {"id": 1}, "remaining_weight": 100, "archived": false},
                 {"id": 12, "filament": {"id": 1}, "remaining_weight": 999, "archived": true},
                 {"id": 13, "filament": {"id": 2}, "remaining_weight": -5},
                 {"id": 14, "filament": {"id": 3}, "remaining_weight": 50},
                 {"id": 15, "filament": {"id": 3}}]
                """);

        List<SpoolmanFilament> filaments = client.filaments();

        assertEquals(List.of(
                new SpoolmanFilament(1, "PLA Basic", "Bambu Lab", "PLA", "#FF0000", List.of(), 500.5),
                new SpoolmanFilament(2, "Silk Dual", null, "PLA", null, List.of("#FF0000", "#00FF00"), 0.0),
                // A spool without a known weight makes the whole filament unknown.
                new SpoolmanFilament(3, null, null, "PETG", null, List.of(), null),
                new SpoolmanFilament(4, null, null, "ABS", null, List.of(), 0.0)), filaments);
    }

    @Test
    void reportsFailuresAsSpoolmanErrors() {
        status = 500;
        assertEquals("Spoolman answered with HTTP 500",
                assertThrows(SpoolmanException.class, client::version).getMessage());

        status = 200;
        bodies.put("/api/v1/info", "not json");
        assertEquals("Spoolman sent an unreadable answer",
                assertThrows(SpoolmanException.class, client::version).getMessage());

        server.stop(0);
        assertEquals("Spoolman can't be reached", assertThrows(SpoolmanException.class, client::version).getMessage());
    }

    @Test
    void needsAnAddress() {
        client.url = Optional.of(" ");
        assertFalse(client.isConfigured());
        assertNull(client.address());
        assertEquals(SpoolmanClient.NOT_SET_UP, assertThrows(SpoolmanException.class, client::filaments).getMessage());
    }

    @Test
    void readsColoursAsRrggbb() {
        assertEquals("#ABCDEF", SpoolmanClient.color("abcdef"));
        assertEquals("#ABCDEF", SpoolmanClient.color(" #abcdef80 "));
        assertNull(SpoolmanClient.color("abc"));
        assertNull(SpoolmanClient.color(null));
        assertEquals(List.of(), SpoolmanClient.colors(null));
    }
}
