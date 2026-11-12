package com.beauver.printbridge.settings;

import com.beauver.printbridge.settings.dto.SettingsDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.Optional;

/** Settings the admin edits in the browser instead of through environment variables. */
@ApplicationScoped
public class SettingsService {

    @Inject
    AppSettingsRepository repository;

    public SettingsDto get() {
        return toDto(repository.get());
    }

    @Transactional
    public SettingsDto update(SettingsDto input) {
        AppSettings settings = repository.get();
        settings.buildPlateX = input.buildPlateX();
        settings.buildPlateY = input.buildPlateY();
        settings.buildPlateZ = input.buildPlateZ();
        repository.flush();
        return toDto(settings);
    }

    /** The build plate, once the admin has set all three sizes. */
    public Optional<BuildPlate> buildPlate() {
        AppSettings settings = repository.get();
        if (settings.buildPlateX == null || settings.buildPlateY == null || settings.buildPlateZ == null) {
            return Optional.empty();
        }
        return Optional.of(new BuildPlate(settings.buildPlateX, settings.buildPlateY, settings.buildPlateZ));
    }

    private static SettingsDto toDto(AppSettings settings) {
        return new SettingsDto(settings.buildPlateX, settings.buildPlateY, settings.buildPlateZ);
    }
}
