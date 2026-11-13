package com.beauver.printbridge.about;

/**
 * What the app's footer shows.
 *
 * @param sourceUrl where the source of the running version is (AGPL-3.0 §13)
 * @param licence   SPDX identifier of PrintBridge's licence
 */
public record AboutDto(String sourceUrl, String licence) {
}
