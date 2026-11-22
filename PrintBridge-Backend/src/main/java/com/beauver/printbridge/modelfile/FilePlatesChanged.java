package com.beauver.printbridge.modelfile;

/**
 * The plates and colours of a stored file became known or changed: it was analysed, or sliced on
 * the server (#76). Fired outside any transaction, after the result is saved.
 */
public record FilePlatesChanged(long fileId) {
}
