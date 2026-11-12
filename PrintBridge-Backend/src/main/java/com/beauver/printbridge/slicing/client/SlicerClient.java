package com.beauver.printbridge.slicing.client;

import com.beauver.printbridge.slicing.SlicePlan;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * HTTP client for the slicer service (OrcaSlicer behind a small API, see PrintBridge-Slicer). Its
 * address comes from {@code SLICER_URL}; without it server slicing is unavailable.
 */
@ApplicationScoped
public class SlicerClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(15);
    /** Extra time on top of the slicing timeout for uploading the file and the reply. */
    private static final Duration SLICE_MARGIN = Duration.ofSeconds(30);

    @ConfigProperty(name = "printbridge.slicer.url")
    Optional<String> url;

    @Inject
    ObjectMapper objectMapper;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();

    public boolean isConfigured() {
        return url.filter(value -> !value.isBlank()).isPresent();
    }

    /** OrcaSlicer's version, from the service's health check. */
    public String version() {
        return get("/health", new TypeReference<JsonNode>() {
        }).path("orcaVersion").asText("unknown");
    }

    public List<SlicerPrinter> printers() {
        return get("/printers", new TypeReference<>() {
        });
    }

    public SlicerProfiles profiles(String printer, BigDecimal nozzle) {
        return get("/profiles?" + query(Map.of("printer", printer, "nozzle", nozzle.toPlainString())),
                new TypeReference<>() {
                });
    }

    /** Slices a file plate by plate; waits at most {@code timeout} (plus a little for the transfer). */
    public SliceResponse slice(Path file, SlicePlan plan, Duration timeout) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("format", plan.format());
        params.put("printer", plan.printer());
        params.put("nozzle", plan.nozzle().toPlainString());
        params.put("plates", plan.plates().stream().map(String::valueOf).collect(Collectors.joining(",")));
        params.put("timeout", String.valueOf(timeout.toSeconds()));
        if (plan.projectPrinter()) {
            params.put("projectPrinter", "1");
        }
        if (plan.process() != null) {
            params.put("process", plan.process());
        }
        if (plan.filament() != null) {
            params.put("filament", plan.filament());
            params.put("filamentSlots", String.valueOf(plan.filamentSlots()));
        }
        if (!plan.overrides().isEmpty()) {
            params.put("overrides", json(plan.overrides()));
        }
        if (plan.arrange()) {
            params.put("arrange", "1");
        }
        HttpRequest.BodyPublisher body;
        try {
            body = HttpRequest.BodyPublishers.ofFile(file);
        } catch (FileNotFoundException e) {
            throw new SlicerException("The model file is missing on disk");
        }
        HttpRequest request = HttpRequest.newBuilder(uri("/slice?" + query(params)))
                .timeout(timeout.plus(SLICE_MARGIN))
                .header("Content-Type", "application/octet-stream")
                .POST(body)
                .build();
        return send(request, new TypeReference<>() {
        });
    }

    private <T> T get(String path, TypeReference<T> type) {
        return send(HttpRequest.newBuilder(uri(path)).timeout(SHORT_TIMEOUT).GET().build(), type);
    }

    private <T> T send(HttpRequest request, TypeReference<T> type) {
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            throw new SlicerException("Slicing timed out");
        } catch (IOException e) {
            throw new SlicerException("The slicer can't be reached");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SlicerException("Slicing was interrupted");
        }
        if (response.statusCode() != 200) {
            throw new SlicerException(errorMessage(response));
        }
        try {
            return objectMapper.readValue(response.body(), type);
        } catch (JsonProcessingException e) {
            throw new SlicerException("The slicer sent an unreadable answer");
        }
    }

    private String errorMessage(HttpResponse<String> response) {
        try {
            String error = objectMapper.readTree(response.body()).path("error").asText("");
            if (!error.isBlank()) {
                return error;
            }
        } catch (JsonProcessingException e) {
            // Not our service's JSON; fall back to the status code.
        }
        return "The slicer answered with HTTP " + response.statusCode();
    }

    private URI uri(String path) {
        String base = url.filter(value -> !value.isBlank())
                .orElseThrow(() -> new SlicerException("Server slicing isn't set up (SLICER_URL is missing)"));
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return URI.create(base + path);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String query(Map<String, String> params) {
        return params.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
