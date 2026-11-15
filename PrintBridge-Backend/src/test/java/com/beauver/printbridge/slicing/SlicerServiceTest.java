package com.beauver.printbridge.slicing;

import com.beauver.printbridge.slicing.client.SlicerClient;
import com.beauver.printbridge.slicing.client.SlicerException;
import com.beauver.printbridge.slicing.client.SlicerPrinter;
import com.beauver.printbridge.slicing.client.SlicerProfiles;
import com.beauver.printbridge.slicing.dto.SlicerPrinterDto;
import com.beauver.printbridge.slicing.dto.SlicerProfilesDto;
import com.beauver.printbridge.slicing.dto.SlicerStatusDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SlicerServiceTest {

    @Mock
    SlicerClient client;

    SlicerService service;

    @BeforeEach
    void setUp() {
        service = new SlicerService();
        service.client = client;
        service.mapper = new SlicerMapper();
    }

    @Test
    void statusSaysWhenTheSlicerIsNotSetUp() {
        when(client.isConfigured()).thenReturn(false);

        assertEquals(new SlicerStatusDto(false, false, null, SlicerService.NOT_SET_UP), service.status());
        verify(client, never()).version();
    }

    @Test
    void statusGivesTheVersionWhenReachable() {
        when(client.isConfigured()).thenReturn(true);
        when(client.version()).thenReturn("2.5.0");

        assertEquals(new SlicerStatusDto(true, true, "2.5.0", null), service.status());
    }

    @Test
    void statusGivesTheReasonWhenUnreachable() {
        when(client.isConfigured()).thenReturn(true);
        when(client.version()).thenThrow(new SlicerException("The slicer can't be reached"));

        assertEquals(new SlicerStatusDto(true, false, null, "The slicer can't be reached"), service.status());
    }

    @Test
    void printersAreMappedWithTheirNozzles() {
        SlicerPrinter.Nozzle nozzle = new SlicerPrinter.Nozzle(new BigDecimal("0.4"), "Bambu Lab P1S 0.4 nozzle",
                new SlicerPrinter.Bed(256, 256, 250));
        when(client.printers()).thenReturn(List.of(new SlicerPrinter("Bambu Lab P1S", "BBL", List.of(nozzle))));

        SlicerPrinterDto expected = new SlicerPrinterDto("Bambu Lab P1S", "BBL", List.of(new SlicerPrinterDto.NozzleDto(
                new BigDecimal("0.4"), "Bambu Lab P1S 0.4 nozzle", new SlicerPrinterDto.BedDto(256, 256, 250))));
        assertEquals(List.of(expected), service.printers());
    }

    @Test
    void profilesAreMapped() {
        BigDecimal nozzle = new BigDecimal("0.4");
        when(client.profiles("Bambu Lab P1S", nozzle))
                .thenReturn(new SlicerProfiles("machine", List.of("0.20mm Standard"), List.of("Bambu PLA Basic")));

        assertEquals(new SlicerProfilesDto("machine", List.of("0.20mm Standard"), List.of("Bambu PLA Basic")),
                service.profiles("Bambu Lab P1S", nozzle));
    }

    @Test
    void slicerErrorsPassThrough() {
        when(client.printers()).thenThrow(new SlicerException("The slicer can't be reached"));

        assertThrows(SlicerException.class, () -> service.printers());
    }
}
