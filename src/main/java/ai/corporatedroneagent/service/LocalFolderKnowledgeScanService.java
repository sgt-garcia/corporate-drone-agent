package ai.corporatedroneagent.service;

import ai.corporatedroneagent.model.KnowledgeFolder;
import ai.corporatedroneagent.model.knowledge.KnowledgeResource;
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
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class LocalFolderKnowledgeScanService {

    private final KnowledgeRootRepository rootRepository;
    private final KnowledgeRootScanRepository scanRepository;
    private final KnowledgeResourceRepository resourceRepository;
    private final LocalFolderKnowledgeReadService readService;

    public LocalFolderKnowledgeScanService(
            KnowledgeRootRepository rootRepository,
            KnowledgeRootScanRepository scanRepository,
            KnowledgeResourceRepository resourceRepository,
            LocalFolderKnowledgeReadService readService
    ) {
        this.rootRepository = rootRepository;
        this.scanRepository = scanRepository;
        this.resourceRepository = resourceRepository;
        this.readService = readService;
    }

    public ScanResult scan(KnowledgeFolder folder, Path folderPath) {
        Instant startedAt = Instant.now();
        KnowledgeRoot root = startRootScan(knowledgeRoot(folder), startedAt);
        KnowledgeRootScan scan = startScan(root.getId(), startedAt);

        try {
            ResourceScanningVisitor visitor = new ResourceScanningVisitor(root.getId(), folderPath, startedAt);
            Files.walkFileTree(folderPath, visitor);
            resourceRepository.markDeletedResourcesNotScannedSince(root.getId(), startedAt);
            completeScan(root, scan, visitor.stats(), null);
            return visitor.stats();
        } catch (IOException exception) {
            ScanResult emptyResult = new ScanResult(0, 0);
            completeScan(root, scan, emptyResult, "Could not scan folder");
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
        private long files;
        private long bytes;

        private ResourceScanningVisitor(java.util.UUID rootId, Path rootPath, Instant scannedAt) {
            this.rootId = rootId;
            this.rootPath = rootPath;
            this.scannedAt = scannedAt;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            if (attrs.isRegularFile()) {
                files++;
                bytes += attrs.size();
                saveResource(file, attrs);
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exception) {
            return FileVisitResult.CONTINUE;
        }

        private void saveResource(Path file, BasicFileAttributes attrs) {
            KnowledgeResource resource = new KnowledgeResource();
            resource.setRootId(rootId);
            resource.setReference(resourceReference(rootPath, file));
            resource.setDisplayName(file.getFileName().toString());
            resource.setFormat(format(file));
            resource.setSizeBytes(attrs.size());
            resource.setLastModifiedAt(attrs.lastModifiedTime().toInstant());
            resource.setDeleted(false);
            resource.setScannedAt(scannedAt);
            KnowledgeResource savedResource = resourceRepository.save(resource);
            readService.read(savedResource, file);
        }

        private ScanResult stats() {
            return new ScanResult(files, bytes);
        }
    }
}
