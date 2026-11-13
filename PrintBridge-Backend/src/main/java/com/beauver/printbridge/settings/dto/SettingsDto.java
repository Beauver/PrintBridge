package com.beauver.printbridge.settings.dto;

import java.util.List;

/**
 * All application settings.
 *
 * @param defaultPrinterId the printer for STL files and projects made for a printer that isn't owned
 * @param printers         the owned printers, in the order they were added
 */
public record SettingsDto(SlicingSettingsDto slicing, Long defaultPrinterId, List<PrinterDto> printers) {
}
