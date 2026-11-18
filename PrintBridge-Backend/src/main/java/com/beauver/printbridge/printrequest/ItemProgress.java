package com.beauver.printbridge.printrequest;

/**
 * How far the items of one request are, for the request lists (#61).
 *
 * @param completed files and links that are completed
 * @param active    files and links that aren't declined
 */
public record ItemProgress(int completed, int active) {

    public static final ItemProgress NONE = new ItemProgress(0, 0);
}
