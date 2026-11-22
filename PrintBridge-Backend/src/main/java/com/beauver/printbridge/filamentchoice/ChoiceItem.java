package com.beauver.printbridge.filamentchoice;

/**
 * A file or link of a request as the filament choices need it.
 *
 * @param quantity copies to print
 * @param declined declined items aren't printed, so their filament isn't needed
 */
public record ChoiceItem(long id, int quantity, boolean declined) {
}
