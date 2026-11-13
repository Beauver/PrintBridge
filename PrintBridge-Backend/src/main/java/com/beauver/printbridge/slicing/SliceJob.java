package com.beauver.printbridge.slicing;

import java.nio.file.Path;
import java.time.Duration;

/** A claimed job: everything the worker needs to call the slicer outside a transaction. */
public record SliceJob(long fileId, Path file, SlicePlan plan, Duration timeout) {
}
