package com.beauver.printbridge.modelfile;

import java.util.Optional;

/**
 * Looks up stored model files by id. Model info and slicing work on files in the background
 * (backfill, slicing queue) and only know the id; the print requests own the files and implement
 * this, so those packages don't depend on them.
 */
public interface StoredModelFiles {

    /** The file with this id; empty when it was deleted in the meantime. */
    Optional<StoredModelFile> find(long fileId);
}
