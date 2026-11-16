package com.beauver.printbridge.modelinfo;

import com.beauver.printbridge.common.transaction.RecordingTransactions;
import com.beauver.printbridge.modelfile.ModelAnalyzer;
import com.beauver.printbridge.modelfile.ModelFileType;
import com.beauver.printbridge.modelfile.StoredModelFile;
import com.beauver.printbridge.modelfile.StoredModelFiles;
import com.beauver.printbridge.modelfile.analysis.ModelAnalysis;
import com.beauver.printbridge.modelfile.analysis.ModelSource;
import com.beauver.printbridge.modelfile.exception.ModelAnalysisException;
import com.beauver.printbridge.storage.FileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** How a file is analysed: outside a transaction, with the result saved in a short one. */
@ExtendWith(MockitoExtension.class)
class ModelInfoServiceTest {

    static final StoredModelFile FILE = new StoredModelFile(7, "4/model.stl", ModelFileType.STL);

    @Mock
    ModelAnalyzer analyzer;
    @Mock
    ModelInfoRepository repository;
    @Mock
    FileStorage storage;
    @Mock
    StoredModelFiles files;

    @TempDir
    Path dir;

    final RecordingTransactions transactions = new RecordingTransactions();
    ModelInfoService service;
    Path model;

    @BeforeEach
    void setUp() throws Exception {
        service = new ModelInfoService();
        service.analyzer = analyzer;
        service.repository = repository;
        service.storage = storage;
        service.files = files;
        service.transactions = transactions;
        model = Files.createFile(dir.resolve("model.stl"));
        lenient().when(storage.resolve("4/model.stl")).thenReturn(model);
        lenient().when(files.find(7)).thenReturn(Optional.of(FILE));
        lenient().when(repository.findByIdOptional(7L)).thenReturn(Optional.empty());
    }

    private ModelInfo saved() {
        ArgumentCaptor<ModelInfo> captor = ArgumentCaptor.forClass(ModelInfo.class);
        verify(repository).persist(captor.capture());
        return captor.getValue();
    }

    @Test
    void analysesOutsideATransactionAndSavesInOne() throws Exception {
        when(analyzer.analyze(model, ModelFileType.STL)).thenAnswer(inv -> {
            assertFalse(transactions.active(), "the analysis must not run inside a transaction");
            return new ModelAnalysis(ModelSource.MESH, 12.5, List.of());
        });
        doAnswer(inv -> {
            assertTrue(transactions.active(), "the result is saved inside a transaction");
            return null;
        }).when(repository).persist(any(ModelInfo.class));

        service.analyze(7);

        ModelInfo info = saved();
        assertEquals(7L, info.fileId);
        assertEquals(ModelInfoStatus.ANALYZED, info.status);
        assertEquals(ModelSource.MESH, info.source);
        assertEquals(12.5, info.volumeMm3);
        // One to look the file up, one to save the result.
        assertEquals(2, transactions.count());
    }

    @Test
    void anUnreadableModelIsSavedAsFailed() throws Exception {
        when(analyzer.analyze(any(), any())).thenThrow(new ModelAnalysisException("broken"));

        service.analyze(7);

        assertEquals(ModelInfoStatus.FAILED, saved().status);
    }

    @Test
    void aFileMissingOnDiskIsSavedAsFailed() throws Exception {
        Files.delete(model);

        service.analyze(7);

        assertEquals(ModelInfoStatus.FAILED, saved().status);
        verifyNoInteractions(analyzer);
    }

    @Test
    void skipsFilesThatAreGoneOrAlreadyHaveAResult() {
        when(files.find(8)).thenReturn(Optional.empty());
        when(repository.findByIdOptional(8L)).thenReturn(Optional.empty());
        service.analyze(8);

        when(repository.findByIdOptional(7L)).thenReturn(Optional.of(new ModelInfo()));
        service.analyze(7);

        verifyNoInteractions(analyzer);
        verify(repository, never()).persist(any(ModelInfo.class));
    }

    @Test
    void doesNotSaveWhenTheFileWasDeletedDuringTheAnalysis() throws Exception {
        when(analyzer.analyze(any(), any())).thenAnswer(inv -> {
            when(files.find(7)).thenReturn(Optional.empty());
            return new ModelAnalysis(ModelSource.MESH, 1.0, List.of());
        });

        service.analyze(7);

        verify(repository, never()).persist(any(ModelInfo.class));
    }

    @Test
    void doesNotSaveASecondResultWhenAnotherAnalysisFinishedFirst() throws Exception {
        when(analyzer.analyze(any(), any())).thenAnswer(inv -> {
            when(repository.findByIdOptional(7L)).thenReturn(Optional.of(new ModelInfo()));
            return new ModelAnalysis(ModelSource.MESH, 1.0, List.of());
        });

        service.analyze(7);

        verify(repository, never()).persist(any(ModelInfo.class));
    }
}
