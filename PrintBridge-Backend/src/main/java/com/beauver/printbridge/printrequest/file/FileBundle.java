package com.beauver.printbridge.printrequest.file;

import java.util.List;

/**
 * All files of a request, ready to be zipped.
 *
 * @param name      name of the ZIP file
 * @param files     each with a name that is unique within the ZIP
 * @param linksText content of {@link #LINKS_FILENAME}, listing the request's links (#59);
 *                  {@code null} when there are none
 */
public record FileBundle(String name, List<StoredFile> files, String linksText) {

    /** Can't clash with an uploaded file: those are always .stl or .3mf. */
    public static final String LINKS_FILENAME = "links.txt";
}
