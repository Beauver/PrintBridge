package com.beauver.printbridge.settings;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * The server slicing settings as the slicing code uses them.
 *
 * @param printers       the owned printers, in the order they were added
 * @param defaultPrinter for STL files and projects made for a printer that isn't owned; {@code null} without printers
 */
public record SlicingSettings(
        boolean enabled,
        Duration timeout,
        List<PrinterSettings> printers,
        PrinterSettings defaultPrinter) {

    /**
     * The printer the fit check and the preview bed use: the one a file was sliced for while it
     * is still owned, else the default printer; empty without printers.
     *
     * @param slicedFor printer of a finished server slicing result; {@code null} when not sliced
     */
    public Optional<PrinterSettings> fitPrinter(String slicedFor) {
        return printer(slicedFor).or(() -> Optional.ofNullable(defaultPrinter));
    }

    /** The owned printer of this model (case-insensitive), if any. */
    public Optional<PrinterSettings> printer(String model) {
        return printers.stream().filter(printer -> printer.isModel(model)).findFirst();
    }
}
