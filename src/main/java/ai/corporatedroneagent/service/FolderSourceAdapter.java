package ai.corporatedroneagent.service;

import ai.corporatedroneagent.model.knowledge.KnowledgeResource;
import ai.corporatedroneagent.model.knowledge.KnowledgeRoot;
import ai.corporatedroneagent.model.knowledge.KnowledgeSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The local-folder half of ingestion: enumerate the files under a folder, read one file's
 * bytes, decode it to text. Constructed per scan with the folder path; the
 * {@link KnowledgeScanEngine} drives it. A folder does a full enumeration, so it reconciles
 * deletions and detects change by size + last-modified.
 */
public class FolderSourceAdapter implements KnowledgeSourceAdapter {

    private static final Logger log = LoggerFactory.getLogger(FolderSourceAdapter.class);

    private final LocalFolderKnowledgeReadService readService;
    private final LocalFolderKnowledgeConversionService conversionService;
    private final Path folderPath;

    public FolderSourceAdapter(
            LocalFolderKnowledgeReadService readService,
            LocalFolderKnowledgeConversionService conversionService,
            Path folderPath
    ) {
        this.readService = readService;
        this.conversionService = conversionService;
        this.folderPath = folderPath;
    }

    @Override
    public KnowledgeSource source() {
        return KnowledgeSource.LOCAL_FOLDER;
    }

    @Override
    public KnowledgeScanSession openSession(KnowledgeRoot root) {
        return new Session();
    }

    private final class Session implements KnowledgeScanSession {

        @Override
        public List<ResourceManifest> enumerate(ScanCursor cursor) {
            CollectingVisitor visitor = new CollectingVisitor();
            try {
                Files.walkFileTree(folderPath, visitor);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
            return visitor.manifests;
        }

        @Override
        public ReadResult read(ResourceManifest manifest) {
            Path file = (Path) manifest.handle();
            // A transient carrier so the read service can apply its format/size gates and
            // echo the metadata back onto the ReadResult.
            KnowledgeResource carrier = new KnowledgeResource();
            carrier.setReference(manifest.reference());
            carrier.setDisplayName(manifest.displayName());
            carrier.setFormat(manifest.format());
            carrier.setSizeBytes(manifest.sizeBytes());
            carrier.setLastModifiedAt(manifest.lastModifiedAt());
            return readService.read(carrier, file);
        }

        @Override
        public ConversionResult convert(ReadResult read) {
            return conversionService.convert(read);
        }

        @Override
        public boolean isUnchanged(KnowledgeResource existing, ResourceManifest manifest) {
            return existing.getSizeBytes() == manifest.sizeBytes()
                    && sameTimestamp(existing.getLastModifiedAt(), manifest.lastModifiedAt());
        }

        @Override
        public boolean reconcilesDeletes() {
            return true;
        }
    }

    private final class CollectingVisitor extends SimpleFileVisitor<Path> {

        private final List<ResourceManifest> manifests = new ArrayList<>();

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            if (attrs.isRegularFile()) {
                manifests.add(new ResourceManifest(
                        reference(file),
                        file.getFileName().toString(),
                        format(file),
                        attrs.size(),
                        attrs.lastModifiedTime().toInstant(),
                        file));
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exception) {
            log.warn("Could not visit local knowledge file {}; skipping it.", file, exception);
            return FileVisitResult.CONTINUE;
        }
    }

    private String reference(Path file) {
        return folderPath.relativize(file).toString().replace('\\', '/');
    }

    private String format(Path file) {
        String fileName = file.getFileName().toString();
        int extensionStart = fileName.lastIndexOf('.');
        if (extensionStart < 0 || extensionStart == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(extensionStart + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean sameTimestamp(Instant first, Instant second) {
        if (first == null || second == null) {
            return first == second;
        }
        return first.toEpochMilli() == second.toEpochMilli();
    }
}
