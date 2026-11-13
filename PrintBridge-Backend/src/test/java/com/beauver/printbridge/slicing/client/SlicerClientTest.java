package com.beauver.printbridge.slicing.client;

import com.beauver.printbridge.slicing.SlicePlan;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** The client against a small local HTTP server that plays the slicer service. */
class SlicerClientTest {

    @TempDir
    Path dir;

    HttpServer server;
    SlicerClient client;
    int status = 200;
    String body = "{}";
    String lastQuery;
    byte[] lastBody;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::answer);
        server.start();
        client = new SlicerClient();
        client.objectMapper = new ObjectMapper();
        client.url = Optional.of("http://127.0.0.1:" + server.getAddress().getPort() + "/");
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private void answer(HttpExchange exchange) throws IOException {
        lastQuery = URLDecoder.decode(exchange.getRequestURI().getRawQuery() == null ? ""
                : exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8);
        lastBody = exchange.getRequestBody().readAllBytes();
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Test
    void readsHealthPrintersAndProfiles() {
        body = "{\"status\": \"ok\", \"orcaVersion\": \"2.5.0\"}";
        assertTrue(client.isConfigured());
        assertEquals("2.5.0", client.version());

        body = """
                [{"model": "Bambu Lab P1S", "vendor": "BBL", "nozzles": [{"diameter": 0.4,
                  "machine": "Bambu Lab P1S 0.4 nozzle", "bed": {"x": 256, "y": 256, "z": 250}}]}]
                """;
        List<SlicerPrinter> printers = client.printers();
        assertEquals(new SlicerPrinter.Bed(256, 256, 250), printers.getFirst().nozzles().getFirst().bed());

        body = "{\"machine\": \"m\", \"processes\": [\"p\"], \"filaments\": [\"f\"]}";
        assertEquals(new SlicerProfiles("m", List.of("p"), List.of("f")),
                client.profiles("Bambu Lab P1S", new BigDecimal("0.40")));
        assertEquals("printer=Bambu Lab P1S&nozzle=0.40", sortedQuery(lastQuery, "printer", "nozzle"));
    }

    @Test
    void sendsTheFileAndThePlan() throws IOException {
        Path file = Files.write(dir.resolve("model.3mf"), new byte[]{1, 2, 3});
        body = """
                {"plates": [{"index": 1, "grams": 33.08, "seconds": 2904,
                  "filaments": [{"type": "PLA", "color": "#FFFFFF", "grams": 33.08}]},
                 {"index": 2, "error": "Refused"}]}
                """;
        SlicePlan plan = new SlicePlan("3mf", List.of(1, 2), "Bambu Lab P1S", new BigDecimal("0.4"), true,
                "0.20mm Standard", "PLA Basic", 3, Map.of("wall_loops", "4"), true, new BigDecimal("0.8"), null);

        SliceResponse response = client.slice(file, plan, Duration.ofMinutes(2));

        assertEquals(33.08, response.plates().getFirst().grams());
        assertEquals("Refused", response.plates().get(1).error());
        assertArrayEquals(new byte[]{1, 2, 3}, lastBody);
        assertTrue(lastQuery.contains("format=3mf&printer=Bambu Lab P1S&nozzle=0.4&plates=1,2&timeout=120"));
        assertTrue(lastQuery.contains("projectPrinter=1"));
        assertTrue(lastQuery.contains("process=0.20mm Standard"));
        assertTrue(lastQuery.contains("filament=PLA Basic&filamentSlots=3"));
        assertTrue(lastQuery.contains("overrides={\"wall_loops\":\"4\"}"));
        assertTrue(lastQuery.contains("arrange=1"));
    }

    @Test
    void leavesOutWhatThePlanDoesNotSet() throws IOException {
        Path file = Files.write(dir.resolve("model.stl"), new byte[]{1});
        body = "{\"plates\": []}";

        client.slice(file, new SlicePlan("stl", List.of(1), "P", new BigDecimal("0.4"), false, null, null, 1,
                Map.of(), false, null, null), Duration.ofMinutes(1));

        assertEquals("format=stl&printer=P&nozzle=0.4&plates=1&timeout=60", lastQuery);
    }

    @Test
    void reportsTheServicesErrors() {
        status = 400;
        body = "{\"error\": \"Unknown process profile: x\"}";
        assertEquals("Unknown process profile: x", assertThrows(SlicerException.class, client::printers).getMessage());

        status = 500;
        body = "<html>oops</html>";
        assertEquals("The slicer answered with HTTP 500", assertThrows(SlicerException.class, client::printers).getMessage());

        body = "{\"error\": \"\"}";
        assertEquals("The slicer answered with HTTP 500", assertThrows(SlicerException.class, client::printers).getMessage());

        status = 200;
        body = "not json";
        assertEquals("The slicer sent an unreadable answer", assertThrows(SlicerException.class, client::printers).getMessage());
    }

    @Test
    void reportsUnreachableOrMissingSetup() {
        Path missing = dir.resolve("missing.stl");
        SlicePlan plan = new SlicePlan("stl", List.of(1), "P", BigDecimal.ONE, false, null, null, 1, Map.of(), false, null, null);
        assertEquals("The model file is missing on disk",
                assertThrows(SlicerException.class, () -> client.slice(missing, plan, Duration.ofMinutes(1))).getMessage());

        server.stop(0);
        assertEquals("The slicer can't be reached", assertThrows(SlicerException.class, client::printers).getMessage());

        client.url = Optional.of(" ");
        assertFalse(client.isConfigured());
        assertEquals("Server slicing isn't set up (SLICER_URL is missing)",
                assertThrows(SlicerException.class, client::version).getMessage());
    }

    private static String sortedQuery(String query, String... keys) {
        StringBuilder result = new StringBuilder();
        for (String key : keys) {
            for (String part : query.split("&")) {
                if (part.startsWith(key + "=")) {
                    if (!result.isEmpty()) {
                        result.append('&');
                    }
                    result.append(part);
                }
            }
        }
        return result.toString();
    }
}
