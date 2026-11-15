package com.beauver.printbridge.modelfile;

/**
 * A model file in storage, as the analysis and slicing code need it.
 *
 * @param id          id of the request file
 * @param storagePath path relative to the storage root
 */
public record StoredModelFile(long id, String storagePath, ModelFileType type) {
}
