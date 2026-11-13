package com.beauver.printbridge.settings;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class PrinterRepository implements PanacheRepository<Printer> {

    /** In the order they were added. */
    public List<Printer> listInOrder() {
        return listAll(Sort.by("id"));
    }

    public Optional<Printer> findByModel(String model) {
        return find("lower(model) = lower(?1)", model).firstResultOptional();
    }
}
