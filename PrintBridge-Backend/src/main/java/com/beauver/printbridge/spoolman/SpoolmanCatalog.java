package com.beauver.printbridge.spoolman;

import com.beauver.printbridge.settings.SettingsService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * The filaments in Spoolman, kept for {@code SPOOLMAN_CACHE_TTL_SECONDS} so opening a dropdown
 * or a request page doesn't ask Spoolman every time. Failures aren't kept: the next call tries again.
 */
@ApplicationScoped
public class SpoolmanCatalog {

    @Inject
    SpoolmanClient client;

    @ConfigProperty(name = "printbridge.spoolman.cache-ttl")
    Duration ttl;

    Clock clock = Clock.systemUTC();

    private record Cached(List<SpoolmanFilament> filaments, Instant fetchedAt) {
    }

    private volatile Cached cached;

    /** @throws SpoolmanException when Spoolman isn't set up or can't be read */
    public List<SpoolmanFilament> filaments() {
        Cached current = cached;
        if (current != null && isFresh(current)) {
            return current.filaments();
        }
        synchronized (this) {
            current = cached;
            if (current != null && isFresh(current)) {
                return current.filaments();
            }
            List<SpoolmanFilament> fetched = List.copyOf(client.filaments());
            cached = new Cached(fetched, clock.instant());
            return fetched;
        }
    }

    /** One filament, if Spoolman has it. @throws SpoolmanException when Spoolman can't be read */
    public Optional<SpoolmanFilament> find(long id) {
        return filaments().stream().filter(filament -> filament.id() == id).findFirst();
    }

    /** The filaments, or empty when Spoolman can't be read right now (for showing, not choosing). */
    public Optional<List<SpoolmanFilament>> filamentsIfAvailable() {
        try {
            return Optional.of(filaments());
        } catch (SpoolmanException e) {
            return Optional.empty();
        }
    }

    /** Forgets the cached list. */
    public void clear() {
        cached = null;
    }

    /** Start from what is in Spoolman now, not a list cached before the integration was switched off. */
    void onSwitchedOn(@Observes SettingsService.SpoolmanSwitchedOn switchedOn) {
        clear();
    }

    private boolean isFresh(Cached current) {
        return clock.instant().isBefore(current.fetchedAt().plus(ttl));
    }
}
