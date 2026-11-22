package com.beauver.printbridge.spoolman;

import com.beauver.printbridge.common.ratelimit.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SpoolmanCatalogTest {

    final MutableClock clock = new MutableClock();
    final List<SpoolmanFilament> inSpoolman = new ArrayList<>();
    int calls;
    SpoolmanException failure;
    SpoolmanCatalog catalog;

    @BeforeEach
    void setUp() {
        catalog = new SpoolmanCatalog();
        catalog.clock = clock;
        catalog.ttl = Duration.ofSeconds(60);
        catalog.client = new SpoolmanClient() {
            @Override
            public List<SpoolmanFilament> filaments() {
                calls++;
                if (failure != null) {
                    throw failure;
                }
                return List.copyOf(inSpoolman);
            }
        };
        inSpoolman.add(new SpoolmanFilament(1, "Red", "Acme", "PLA", "#FF0000", List.of(), 100.0, null, null, null));
    }

    @Test
    void keepsTheListForTheTtl() {
        assertEquals(1, catalog.filaments().size());
        inSpoolman.add(new SpoolmanFilament(2, "Blue", "Acme", "PLA", "#0000FF", List.of(), 100.0, null, null, null));
        clock.advance(Duration.ofSeconds(59));
        assertEquals(1, catalog.filaments().size());
        assertEquals(1, calls);

        clock.advance(Duration.ofSeconds(1));
        assertEquals(2, catalog.filaments().size());
        assertEquals(2, calls);
        assertTrue(catalog.find(2).isPresent());
        assertTrue(catalog.find(3).isEmpty());
        assertEquals(2, calls);

        catalog.clear();
        catalog.filaments();
        assertEquals(3, calls);
    }

    @Test
    void doesNotKeepFailures() {
        failure = new SpoolmanException("Spoolman can't be reached");
        assertThrows(SpoolmanException.class, catalog::filaments);
        assertEquals(Optional.empty(), catalog.filamentsIfAvailable());

        failure = null;
        assertEquals(1, catalog.filamentsIfAvailable().orElseThrow().size());
        assertEquals(3, calls);
    }

    @Test
    void knowsWhenThereIsNotEnough() {
        SpoolmanFilament some = new SpoolmanFilament(1, null, null, null, null, List.of(), 100.0, null, null, null);
        assertFalse(some.notEnoughFor(100.0));
        assertTrue(some.notEnoughFor(100.1));
        assertFalse(some.notEnoughFor(null));
        SpoolmanFilament none = new SpoolmanFilament(1, null, null, null, null, List.of(), 0.0, null, null, null);
        assertTrue(none.notEnoughFor(null));
        SpoolmanFilament unknown = new SpoolmanFilament(1, null, null, null, null, List.of(), null, null, null, null);
        assertFalse(unknown.notEnoughFor(1000.0));
    }
}
