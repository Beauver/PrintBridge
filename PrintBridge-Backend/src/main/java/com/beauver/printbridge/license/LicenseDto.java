package com.beauver.printbridge.license;

/**
 * The licence of this installation, for the footer.
 *
 * @param license   SPDX identifier of PrintBridge's licence
 * @param sourceUrl where the source of the running version is (AGPL-3.0 §13)
 */
public record LicenseDto(String license, String sourceUrl) {
}
