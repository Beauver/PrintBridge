package com.beauver.printbridge.slicing;

import com.beauver.printbridge.slicing.client.SlicerClient;
import com.beauver.printbridge.slicing.client.SlicerException;
import com.beauver.printbridge.slicing.dto.SlicerPrinterDto;
import com.beauver.printbridge.slicing.dto.SlicerProfilesDto;
import com.beauver.printbridge.slicing.dto.SlicerStatusDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.util.List;

/** What the slicer service offers, for the settings page. */
@ApplicationScoped
public class SlicerService {

    static final String NOT_SET_UP = "Server slicing isn't set up (SLICER_URL is missing)";

    @Inject
    SlicerClient client;

    @Inject
    SlicerMapper mapper;

    /** Whether the slicer is set up and answers; never throws, the reason is in the status. */
    public SlicerStatusDto status() {
        if (!client.isConfigured()) {
            return new SlicerStatusDto(false, false, null, NOT_SET_UP);
        }
        try {
            return new SlicerStatusDto(true, true, client.version(), null);
        } catch (SlicerException e) {
            return new SlicerStatusDto(true, false, null, e.getMessage());
        }
    }

    /** @throws SlicerException when the slicer isn't set up or can't be reached */
    public List<SlicerPrinterDto> printers() {
        return client.printers().stream().map(mapper::toDto).toList();
    }

    /** @throws SlicerException when the slicer isn't set up, can't be reached or has no such printer */
    public SlicerProfilesDto profiles(String printer, BigDecimal nozzle) {
        return mapper.toDto(client.profiles(printer, nozzle));
    }
}
