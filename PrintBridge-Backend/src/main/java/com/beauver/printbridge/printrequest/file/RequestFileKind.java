package com.beauver.printbridge.printrequest.file;

/** What an entry of a request is (#59). */
public enum RequestFileKind {
    /** An uploaded STL/3MF, stored on disk, analysed and sliced. */
    FILE,
    /** A link to a model elsewhere (Patreon, Printables, ...); the owner downloads it themselves. */
    LINK
}
