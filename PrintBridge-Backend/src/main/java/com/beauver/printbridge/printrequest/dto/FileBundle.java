package com.beauver.printbridge.printrequest.dto;

import java.util.List;

/**
 * All files of a request, ready to be zipped.
 *
 * @param name  name of the ZIP file
 * @param files each with a name that is unique within the ZIP
 */
public record FileBundle(String name, List<StoredFile> files) {
}
