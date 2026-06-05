package ai.corporatedroneagent.service;

import ai.corporatedroneagent.model.KnowledgeFolder;
import ai.corporatedroneagent.model.knowledge.KnowledgeResource;
import ai.corporatedroneagent.model.knowledge.KnowledgeResourceChunk;
import ai.corporatedroneagent.model.knowledge.KnowledgeResourceConversion;
import ai.corporatedroneagent.model.knowledge.KnowledgeResourceRead;
import ai.corporatedroneagent.model.knowledge.KnowledgeRoot;
import ai.corporatedroneagent.model.knowledge.KnowledgeRootScan;
import ai.corporatedroneagent.model.knowledge.KnowledgeSource;
import ai.corporatedroneagent.model.knowledge.WorkStatus;
import ai.corporatedroneagent.repository.KnowledgeResourceRepository;
import ai.corporatedroneagent.repository.KnowledgeRootRepository;
import ai.corporatedroneagent.repository.KnowledgeRootScanRepository;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LocalFolderKnowledgeScanService {

    private static final Logger log = LoggerFactory.getLogger(LocalFolderKnowledgeScanService.class);

    private final KnowledgeRootRepository rootRepository;
    private final KnowledgeRootScanRepository scanRepository;
    private final KnowledgeResourceRepository resourceRepository;
    private final LocalFolderKnowledgeReadService readService;
    private final LocalFolderKnowledgeConversionService conversionService;
    private final KnowledgeChunkingService chunkingService;
    private final KnowledgeIndexingService indexingService;

    public LocalFolderKnowledgeScanService(
            KnowledgeRootRepository rootRepository,
            KnowledgeRootScanRepository scanRepository,
            KnowledgeResourceRepository resourceRepository,
            LocalFolderKnowledgeReadService readService,
            LocalFolderKnowledgeConversionService conversionService,
            KnowledgeChunkingService chunkingService,
            KnowledgeIndexingService indexingService
    ) {
        this.rootRepository = rootRepository;
        this.scanRepository = scanRepository;
        this.resourceRepository = resourceRepository;
        this.readService = readService;
        this.conversionService = conversionService;
        this.chunkingService = chunkingService;
        this.indexingService = indexingService;
    }

    public ScanResult scan(KnowledgeFolder folder, Path folderPath) {
        return scan(folder, folderPath, () -> false);
    }

    public ScanResult scan(KnowledgeFolder folder, Path folderPath, BooleanSupplier isCancelled) {
        Instant startedAt = Instant.now();
        KnowledgeRoot root = startRootScan(knowledgeRoot(folder), startedAt);
        KnowledgeRootScan scan = startScan(root.getId(), startedAt);
        ResourceScanningVisitor visitor = new ResourceScanningVisitor(root.getId(), folderPath, startedAt, isCancelled);
        log.info("Started local folder scan for {}.", folderPath);

        try {
            Files.walkFileTree(folderPath, visitor);
            if (visitor.cancelled()) {
                completeScan(root, scan, visitor.stats(), "Scan cancelled");
                log.info(
                        "Cancelled local folder scan for {} after {} files and {} bytes.",
                        folderPath,
                        visitor.stats().files(),
                        visitor.stats().bytes()
                );
                throw new KnowledgeScanException("Scan cancelled", null);
            }
            removeDeletedResourceIndexes(root.getId(), visitor.references());
            completeScan(root, scan, visitor.stats(), null);
            log.info(
                    "Completed local folder scan for {} with {} files and {} bytes.",
                    folderPath,
                    visitor.stats().files(),
                    visitor.stats().bytes()
            );
            return visitor.stats();
        } catch (KnowledgeScanException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            completeScan(root, scan, visitor.stats(), "Could not scan folder");
            log.warn(
                    "Local folder scan failed for {} after {} files and {} bytes.",
                    folderPath,
                    visitor.stats().files(),
                    visitor.stats().bytes(),
                    exception
            );
            throw new KnowledgeScanException("Could not scan folder", exception);
        }
    }

    private KnowledgeRoot knowledgeRoot(KnowledgeFolder folder) {
        String reference = folder.getPath().trim();
        KnowledgeRoot root = rootRepository.findBySourceAndReference(KnowledgeSource.LOCAL_FOLDER, reference)
                .orElseGet(KnowledgeRoot::new);
        root.setSource(KnowledgeSource.LOCAL_FOLDER);
        root.setReference(reference);
        root.setDisplayName(displayName(Path.of(reference)));
        root.setPaused("paused".equals(folder.getStatus()));
        return root;
    }

    private KnowledgeRoot startRootScan(KnowledgeRoot root, Instant startedAt) {
        root.setScanStatus(WorkStatus.IN_PROGRESS);
        root.setScanSuccess(null);
        root.setScanMessage("");
        root.setScanStartedAt(startedAt);
        root.setScanFinishedAt(null);
        return rootRepository.save(root);
    }

    private KnowledgeRootScan startScan(java.util.UUID rootId, Instant startedAt) {
        KnowledgeRootScan scan = new KnowledgeRootScan();
        scan.setRootId(rootId);
        scan.setStatus(WorkStatus.IN_PROGRESS);
        scan.setStartedAt(startedAt);
        return scanRepository.save(scan);
    }

    private void completeScan(KnowledgeRoot root, KnowledgeRootScan scan, ScanResult stats, String errorMessage) {
        Instant finishedAt = Instant.now();
        boolean success = errorMessage == null || errorMessage.isBlank();

        scan.setStatus(WorkStatus.DONE);
        scan.setSuccess(success);
        scan.setMessage(errorMessage);
        scan.setTotalResources(stats.files());
        scan.setTotalSizeBytes(stats.bytes());
        scan.setFinishedAt(finishedAt);
        scanRepository.save(scan);

        root.setTotalResources(stats.files());
        root.setTotalSizeBytes(stats.bytes());
        root.setScanStatus(WorkStatus.DONE);
        root.setScanSuccess(success);
        root.setScanMessage(errorMessage);
        root.setScanFinishedAt(finishedAt);
        rootRepository.save(root);
    }

    private String displayName(Path path) {
        Path fileName = path.getFileName();
        return fileName == null ? path.toString() : fileName.toString();
    }

    private String resourceReference(Path rootPath, Path file) {
        return rootPath.relativize(file).toString().replace('\\', '/');
    }

    private String format(Path file) {
        String fileName = file.getFileName().toString();
        int extensionStart = fileName.lastIndexOf('.');
        if (extensionStart < 0 || extensionStart == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(extensionStart + 1).toLowerCase(Locale.ROOT);
    }

    private void removeDeletedResourceIndexes(java.util.UUID rootId, List<String> currentReferences) {
        Set<String> activeReferences = new HashSet<>(currentReferences);
        resourceRepository.findByRootId(rootId).stream()
                .filter(resource -> !activeReferences.contains(resource.getReference()))
                .forEach(resource -> {
                    log.debug("Removing stale local knowledge resource {}.", resource.getReference());
                    indexingService.deleteResource(resource);
                    chunkingService.deleteChunks(resource);
                });
        resourceRepository.markDeletedResourcesNotInReferences(rootId, currentReferences);
    }

    public record ScanResult(long files, long bytes) {
    }

    public static class KnowledgeScanException extends RuntimeException {

        public KnowledgeScanException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private class ResourceScanningVisitor extends SimpleFileVisitor<Path> {

        private final java.util.UUID rootId;
        private final Path rootPath;
        private final Instant scannedAt;
        private final BooleanSupplier isCancelled;
        private final List<String> references = new ArrayList<>();
        private boolean cancelled;
        private long files;
        private long bytes;

        private ResourceScanningVisitor(
                java.util.UUID rootId,
                Path rootPath,
                Instant scannedAt,
                BooleanSupplier isCancelled
        ) {
            this.rootId = rootId;
            this.rootPath = rootPath;
            this.scannedAt = scannedAt;
            this.isCancelled = isCancelled;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
            if (isCancelled.getAsBoolean()) {
                cancelled = true;
                return FileVisitResult.TERMINATE;
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            if (isCancelled.getAsBoolean()) {
                cancelled = true;
                return FileVisitResult.TERMINATE;
            }
            if (attrs.isRegularFile()) {
                files++;
                bytes += attrs.size();
                saveResource(file, attrs);
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exception) {
            log.warn("Could not visit local knowledge file {}; skipping it.", file, exception);
            return FileVisitResult.CONTINUE;
        }

        private void saveResource(Path file, BasicFileAttributes attrs) {
            KnowledgeResource resource = new KnowledgeResource();
            resource.setRootId(rootId);
            String reference = resourceReference(rootPath, file);
            references.add(reference);
            resource.setReference(reference);
            resource.setDisplayName(file.getFileName().toString());
            resource.setFormat(format(file));
            resource.setSizeBytes(attrs.size());
            resource.setLastModifiedAt(attrs.lastModifiedTime().toInstant());
            resource.setDeleted(false);
            resource.setScannedAt(scannedAt);
            KnowledgeResource savedResource = resourceRepository.save(resource);
            indexingService.deleteResource(savedResource);
            KnowledgeResourceRead read = readService.read(savedResource, file);
            KnowledgeResourceConversion conversion = conversionService.convert(savedResource, read);
            List<KnowledgeResourceChunk> chunks = chunkingService.chunk(savedResource, conversion);
            indexingService.index(savedResource, conversion, chunks);
        }

        private ScanResult stats() {
            return new ScanResult(files, bytes);
        }

        private List<String> references() {
            return references;
        }

        private boolean cancelled() {
            return cancelled;
        }
    }
}
