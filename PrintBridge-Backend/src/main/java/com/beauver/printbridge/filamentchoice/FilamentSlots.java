package com.beauver.printbridge.filamentchoice;

import com.beauver.printbridge.modelinfo.ModelInfoStatus;
import com.beauver.printbridge.modelinfo.dto.FilamentDto;
import com.beauver.printbridge.modelinfo.dto.ModelInfoDto;
import com.beauver.printbridge.modelinfo.dto.PlateDto;

import java.util.ArrayList;
import java.util.List;

/** What filaments can be chosen for in one file or link, from what the model info knows. */
public final class FilamentSlots {

    private FilamentSlots() {
    }

    /**
     * Every colour of every plate, with its grams for one copy: the sliced grams of that colour,
     * else the plate's grams when it is the plate's only colour (exact or estimated), else unknown.
     * A file without colours (not sliced STL, not analysed, unreadable) or a link ({@code info}
     * {@code null}) has one slot for the whole item, with the file's total grams.
     */
    public static List<FilamentSlot> of(ModelInfoDto info) {
        List<FilamentSlot> slots = new ArrayList<>();
        if (info != null && info.status() == ModelInfoStatus.ANALYZED) {
            for (PlateDto plate : info.plates()) {
                List<FilamentDto> filaments = plate.filaments();
                for (FilamentDto filament : filaments) {
                    Double grams = filament.grams();
                    if (grams == null && filaments.size() == 1) {
                        grams = plate.grams();
                    }
                    slots.add(new FilamentSlot(plate.index(), filament.type(), filament.color(), grams));
                }
            }
        }
        if (slots.isEmpty()) {
            Double total = null;
            if (info != null) {
                total = info.totalGrams();
            }
            slots.add(FilamentSlot.wholeItem(total));
        }
        return slots;
    }
}
