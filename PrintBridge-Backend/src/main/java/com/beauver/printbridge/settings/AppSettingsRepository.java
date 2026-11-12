package com.beauver.printbridge.settings;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AppSettingsRepository implements PanacheRepositoryBase<AppSettings, Short> {

    /** The settings row; the migration creates it, so it always exists. */
    public AppSettings get() {
        return findByIdOptional(AppSettings.ID)
                .orElseThrow(() -> new IllegalStateException("app_settings row is missing"));
    }
}
