package com.beauver.printbridge.printrequest.file;

/**
 * A link as received by the resource (#59).
 *
 * @param name optional
 */
public record NewLink(String url, String name) {
}
