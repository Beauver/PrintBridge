package com.beauver.printbridge.spoolman;

import com.beauver.printbridge.settings.SpoolmanConnection;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Read-only HTTP client for Spoolman's REST API ({@code /api/v1/}). Its address comes from
 * {@code SPOOLMAN_URL}; without it the integration can't be switched on. PrintBridge never writes
 * to Spoolman.
 */
@ApplicationScoped
public class SpoolmanClient implements SpoolmanConnection {

    static final String NOT_SET_UP = "Spoolman isn't set up (SPOOLMAN_URL is missing)";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    /** Spoolman stores colours as RRGGBB, sometimes with alpha (RRGGBBAA). */
    private static final Pattern HEX = Pattern.compile("#?([0-9a-fA-F]{6})([0-9a-fA-F]{2})?");

    @ConfigProperty(name = "printbridge.spoolman.url")
    Optional<String> url;

    @Inject
    ObjectMapper objectMapper;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();

    @Override
    public boolean isConfigured() {
        return url.filter(value -> !value.isBlank()).isPresent();
    }

    /** The configured address, for the settings page; {@code null} when not set. */
    public String address() {
        return url.filter(value -> !value.isBlank()).orElse(null);
    }

    /** Spoolman's version, from {@code /api/v1/info}. */
    public String version() {
        return get("/api/v1/info").path("version").asText("unknown");
    }

    /**
     * Every filament with the weight left on its spools that aren't archived. Spoolman leaves
     * archived spools out of {@code /spool} by default.
     */
    public List<SpoolmanFilament> filaments() {
        JsonNode filaments = get("/api/v1/filament");
        JsonNode spools = get("/api/v1/spool");
        Map<Long, Double> left = new HashMap<>();
        Set<Long> unknown = new HashSet<>();
        for (JsonNode spool : spools) {
            if (spool.path("archived").asBoolean(false)) {
                continue;
            }
            long filamentId = spool.path("filament").path("id").asLong();
            JsonNode remaining = spool.path("remaining_weight");
            if (remaining.isNumber()) {
                left.merge(filamentId, Math.max(0, remaining.asDouble()), Double::sum);
            } else {
                unknown.add(filamentId);
            }
        }
        List<SpoolmanFilament> result = new ArrayList<>();
        for (JsonNode filament : filaments) {
            long id = filament.path("id").asLong();
            Double gramsLeft = left.getOrDefault(id, 0.0);
            if (unknown.contains(id)) {
                gramsLeft = null;
            }
            List<String> colors = colors(text(filament, "multi_color_hexes"));
            String color = null;
            if (colors.isEmpty()) {
                color = color(text(filament, "color_hex"));
            }
            result.add(new SpoolmanFilament(id, text(filament, "name"), text(filament.path("vendor"), "name"),
                    text(filament, "material"), color, colors, gramsLeft));
        }
        return result;
    }

    private JsonNode get(String path) {
        HttpRequest request = HttpRequest.newBuilder(uri(path)).timeout(TIMEOUT).GET().build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            throw new SpoolmanException("Spoolman didn't answer in time");
        } catch (IOException e) {
            throw new SpoolmanException("Spoolman can't be reached");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SpoolmanException("Reading Spoolman was interrupted");
        }
        if (response.statusCode() != 200) {
            throw new SpoolmanException("Spoolman answered with HTTP " + response.statusCode());
        }
        try {
            return objectMapper.readTree(response.body());
        } catch (JsonProcessingException e) {
            throw new SpoolmanException("Spoolman sent an unreadable answer");
        }
    }

    private URI uri(String path) {
        String base = url.filter(value -> !value.isBlank()).orElseThrow(() -> new SpoolmanException(NOT_SET_UP));
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return URI.create(base + path);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            return null;
        }
        return value.asText().trim();
    }

    /** {@code #RRGGBB} in upper case; {@code null} for anything else. */
    static String color(String hex) {
        if (hex == null) {
            return null;
        }
        var matcher = HEX.matcher(hex.trim());
        if (!matcher.matches()) {
            return null;
        }
        return "#" + matcher.group(1).toUpperCase(Locale.ROOT);
    }

    /** The readable colours of a comma-separated list, in order. */
    static List<String> colors(String hexes) {
        if (hexes == null) {
            return List.of();
        }
        List<String> colors = new ArrayList<>();
        for (String hex : hexes.split(",")) {
            String color = color(hex);
            if (color != null) {
                colors.add(color);
            }
        }
        return colors;
    }
}
