package com.beauver.printbridge.filamentchoice;

import com.beauver.printbridge.common.error.InvalidInputException;
import com.beauver.printbridge.common.error.ResourceNotFoundException;
import com.beauver.printbridge.filamentchoice.dto.FilamentChoiceDto;
import com.beauver.printbridge.filamentchoice.dto.FilamentChoiceRequest;
import com.beauver.printbridge.modelinfo.dto.ModelInfoDto;
import com.beauver.printbridge.settings.SettingsService;
import com.beauver.printbridge.spoolman.SpoolmanCatalog;
import com.beauver.printbridge.spoolman.SpoolmanFilament;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The Spoolman filaments chosen for the colours of a request's files (#11). Who may choose, and
 * when, is checked by the print request service before it calls in here.
 */
@ApplicationScoped
public class FilamentChoiceService {

    static final String NOT_FOUND = "Filament choice not found";

    @Inject
    FilamentChoiceRepository repository;

    @Inject
    FilamentChoiceMapper mapper;

    @Inject
    SpoolmanCatalog catalog;

    @Inject
    SettingsService settings;

    /**
     * Chooses (or replaces) the filament for one colour of a plate, or for the whole item when the
     * file has no colours or is a link. A filament with too little left may be chosen; the request
     * then shows that it will be ordered.
     *
     * @param info what is known about the file; {@code null} for a link
     * @throws InvalidInputException when the file has no such colour or Spoolman has no such filament
     */
    public void choose(long fileId, ModelInfoDto info, FilamentChoiceRequest input) {
        FilamentSlot slot = slotFor(FilamentSlots.of(info), input);
        SpoolmanFilament filament = catalog.find(input.spoolmanFilamentId())
                .orElseThrow(() -> new InvalidInputException("This filament isn't in Spoolman"));
        FilamentChoice choice = repository.listByFileId(fileId).stream()
                .filter(existing -> existing.isFor(slot))
                .findFirst()
                .orElseGet(FilamentChoice::new);
        choice.fileId = fileId;
        choice.plateIndex = slot.plateIndex();
        choice.originalType = slot.type();
        choice.originalColor = slot.color();
        mapper.copy(filament, choice);
        choice.chosenAt = Instant.now();
        if (choice.id == null) {
            repository.persist(choice);
        }
        repository.flush();
    }

    /** Removes a choice of this file or link; the colour goes back to the file's own. */
    public void remove(long fileId, long choiceId) {
        FilamentChoice choice = repository.findByIdOptional(choiceId)
                .filter(found -> found.fileId == fileId)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        repository.delete(choice);
        repository.flush();
    }

    /**
     * The choices of a request's items by item id, each with whether the file still has that colour
     * and, while the integration is on and Spoolman can be read, how much of its filament is left
     * against what the whole request needs.
     *
     * @param infos what is known about each file, by file id; links have none
     */
    public Map<Long, List<FilamentChoiceDto>> describe(Collection<ChoiceItem> items, Map<Long, ModelInfoDto> infos) {
        List<FilamentChoice> choices = repository.listByFileIds(items.stream().map(ChoiceItem::id).toList());
        if (choices.isEmpty()) {
            return Map.of();
        }
        Map<Long, ChoiceItem> itemsById = items.stream().collect(Collectors.toMap(ChoiceItem::id, Function.identity()));
        Map<Long, List<FilamentSlot>> slots = new HashMap<>();
        for (ChoiceItem item : items) {
            slots.put(item.id(), FilamentSlots.of(infos.get(item.id())));
        }
        Map<Long, Double> needed = gramsNeeded(choices, itemsById, slots);
        Map<Long, SpoolmanFilament> stock = stock();
        Map<Long, List<FilamentChoiceDto>> described = new HashMap<>();
        for (FilamentChoice choice : choices) {
            boolean orphaned = slots.get(choice.fileId).stream().noneMatch(choice::isFor);
            Double gramsNeeded = needed.get(choice.spoolmanFilamentId);
            Optional<SpoolmanFilament> current = Optional.empty();
            Boolean inSpoolman = null;
            if (stock != null) {
                current = Optional.ofNullable(stock.get(choice.spoolmanFilamentId));
                inSpoolman = current.isPresent();
            }
            described.computeIfAbsent(choice.fileId, id -> new ArrayList<>())
                    .add(mapper.toDto(choice, orphaned, inSpoolman, current.orElse(null), gramsNeeded));
        }
        return described;
    }

    /**
     * Grams of each Spoolman filament the request needs: every colour it was chosen for, times the
     * item's quantity. Declined items and choices the file no longer has don't count, nor do
     * colours whose grams aren't known. Filaments with no known grams at all are left out.
     */
    static Map<Long, Double> gramsNeeded(List<FilamentChoice> choices, Map<Long, ChoiceItem> items,
                                         Map<Long, List<FilamentSlot>> slots) {
        Map<Long, Double> needed = new HashMap<>();
        for (FilamentChoice choice : choices) {
            ChoiceItem item = items.get(choice.fileId);
            if (item.declined()) {
                continue;
            }
            slots.get(choice.fileId).stream()
                    .filter(choice::isFor)
                    .findFirst()
                    .map(FilamentSlot::grams)
                    .ifPresent(grams -> needed.merge(choice.spoolmanFilamentId, grams * item.quantity(), Double::sum));
        }
        return needed;
    }

    /** Spoolman's filaments by id while the integration is on and Spoolman can be read, else {@code null}. */
    private Map<Long, SpoolmanFilament> stock() {
        if (!settings.getSpoolman().enabled()) {
            return null;
        }
        return catalog.filamentsIfAvailable()
                .map(filaments -> filaments.stream().collect(Collectors.toMap(SpoolmanFilament::id,
                        Function.identity())))
                .orElse(null);
    }

    /** The slot the input points at; the whole item only when the file has no colours. */
    private static FilamentSlot slotFor(List<FilamentSlot> slots, FilamentChoiceRequest input) {
        if (input.wholeItem()) {
            if (input.plateIndex() != null || input.originalType() != null || input.originalColor() != null) {
                throw new InvalidInputException("A filament for the whole item has no plate or colour");
            }
            return slots.stream()
                    .filter(FilamentSlot::isWholeItem)
                    .findFirst()
                    .orElseThrow(() -> new InvalidInputException(
                            "This file has colours: choose a filament per colour"));
        }
        if (input.plateIndex() == null) {
            throw new InvalidInputException("Choose a plate for this colour");
        }
        return slots.stream()
                .filter(slot -> slot.matches(input.plateIndex(), input.originalType(), input.originalColor()))
                .findFirst()
                .orElseThrow(() -> new InvalidInputException("This file has no such colour on that plate"));
    }
}
