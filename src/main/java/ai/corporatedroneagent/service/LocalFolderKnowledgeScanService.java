package ai.corporatedroneagent.service;

import ai.corporatedroneagent.dto.KnowledgeFolderDto;
import ai.corporatedroneagent.model.knowledge.KnowledgeRoot;
import ai.corporatedroneagent.model.knowledge.KnowledgeSource;
import ai.corporatedroneagent.repository.KnowledgeRootRepository;
import java.nio.file.Path;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

/**
 * Runs a local-folder scan by handing a {@link FolderSourceAdapter} to the generic
 * {@link KnowledgeScanEngine}. The whole scan loop lives in the engine; everything
 * folder-specific lives in the adapter (and its read/convert helpers). This class resolves
 * the folder's root, adapts the result type, and — unlike Jira — surfaces a cancellation as
 * a thrown {@link KnowledgeScanException} so the orchestrator settles it as a failed scan.
 */
@Service
public class LocalFolderKnowledgeScanService {

    private static final Consumer<String> NO_PROGRESS = item -> {
    };

    private final KnowledgeScanEngine engine;
    private final KnowledgeRootRepository rootRepository;
    private final LocalFolderKnowledgeReadService readService;
    private final LocalFolderKnowledgeConversionService conversionService;

    public LocalFolderKnowledgeScanService(
            KnowledgeScanEngine engine,
            KnowledgeRootRepository rootRepository,
            LocalFolderKnowledgeReadService readService,
            LocalFolderKnowledgeConversionService conversionService
    ) {
        this.engine = engine;
        this.rootRepository = rootRepository;
        this.readService = readService;
        this.conversionService = conversionService;
    }

    public ScanResult scan(KnowledgeFolderDto folder, Path folderPath) {
        return scan(folder, folderPath, () -> false);
    }

    public ScanResult scan(KnowledgeFolderDto folder, Path folderPath, BooleanSupplier isCancelled) {
        return scan(folder, folderPath, isCancelled, NO_PROGRESS);
    }

    public ScanResult scan(
            KnowledgeFolderDto folder,
            Path folderPath,
            BooleanSupplier isCancelled,
            Consumer<String> onProgress
    ) {
        KnowledgeRoot root = knowledgeRoot(folder);
        FolderSourceAdapter adapter = new FolderSourceAdapter(readService, conversionService, folderPath);
        KnowledgeScanEngine.ScanOutcome outcome;
        try {
            outcome = engine.scan(root, adapter, isCancelled, onProgress);
        } catch (KnowledgeScanEngine.KnowledgeScanException exception) {
            throw new KnowledgeScanException("Could not scan folder", exception.getCause());
        }
        if (outcome.cancelled()) {
            throw new KnowledgeScanException("Scan cancelled", null);
        }
        return new ScanResult(outcome.resources(), outcome.bytes());
    }

    private KnowledgeRoot knowledgeRoot(KnowledgeFolderDto folder) {
        String reference = folder.getPath().trim();
        KnowledgeRoot root = rootRepository.findBySourceAndReference(KnowledgeSource.LOCAL_FOLDER, reference)
                .orElseGet(KnowledgeRoot::new);
        root.setSource(KnowledgeSource.LOCAL_FOLDER);
        root.setReference(reference);
        root.setDisplayName(displayName(Path.of(reference)));
        return root;
    }

    private String displayName(Path path) {
        Path fileName = path.getFileName();
        return fileName == null ? path.toString() : fileName.toString();
    }

    public record ScanResult(long files, long bytes) {
    }

    public static class KnowledgeScanException extends RuntimeException {

        public KnowledgeScanException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
