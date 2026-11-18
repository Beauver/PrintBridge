package com.beauver.printbridge.printrequest.file;

import com.beauver.printbridge.modelfile.StoredModelFile;
import com.beauver.printbridge.modelfile.StoredModelFiles;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.Optional;

/**
 * Lets model info and slicing find a request file by id without knowing about print requests.
 * Links (#59) aren't files, so they are never found: they aren't analysed or sliced.
 */
@ApplicationScoped
public class RequestFileLookup implements StoredModelFiles {

    @Inject
    EntityManager entityManager;

    @Override
    public Optional<StoredModelFile> find(long fileId) {
        return Optional.ofNullable(entityManager.find(RequestFile.class, fileId))
                .filter(file -> !file.isLink())
                .map(RequestFile::stored);
    }
}
