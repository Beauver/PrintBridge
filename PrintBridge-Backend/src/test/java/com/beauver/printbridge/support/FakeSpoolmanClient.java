package com.beauver.printbridge.support;

import com.beauver.printbridge.spoolman.SpoolmanClient;
import com.beauver.printbridge.spoolman.SpoolmanFilament;
import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Replaces Spoolman in @QuarkusTest tests: answers with the filaments it is given and counts how
 * often it was asked. A singleton (no client proxy), so tests can set its public fields directly.
 */
@Alternative
@Priority(1)
@Singleton
public class FakeSpoolmanClient extends SpoolmanClient {

    public final List<SpoolmanFilament> filaments = new ArrayList<>();
    public final AtomicInteger calls = new AtomicInteger();
    public boolean configured;
    public RuntimeException failure;

    public FakeSpoolmanClient() {
        reset();
    }

    public final void reset() {
        filaments.clear();
        calls.set(0);
        configured = true;
        failure = null;
    }

    /** A plain one-colour filament. */
    public static SpoolmanFilament filament(long id, String material, String color, Double gramsLeft) {
        return new SpoolmanFilament(id, "Basic " + color, "Acme", material, color, List.of(), gramsLeft);
    }

    @Override
    public boolean isConfigured() {
        return configured;
    }

    @Override
    public String address() {
        if (!configured) {
            return null;
        }
        return "http://spoolman.test:7912";
    }

    @Override
    public String version() {
        throwIfFailing();
        return "0.22.1";
    }

    @Override
    public List<SpoolmanFilament> filaments() {
        calls.incrementAndGet();
        throwIfFailing();
        return List.copyOf(filaments);
    }

    private void throwIfFailing() {
        if (failure != null) {
            throw failure;
        }
    }
}
