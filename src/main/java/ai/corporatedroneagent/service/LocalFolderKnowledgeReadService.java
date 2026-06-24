package ai.corporatedroneagent.service;

import ai.corporatedroneagent.model.knowledge.KnowledgePipelineReason;
import ai.corporatedroneagent.model.knowledge.KnowledgeResource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Reads a local file's bytes for the ingestion engine: enforces the supported-format and
 * size limits and returns a pure {@link ReadResult} (the engine records the read stage).
 * An unsupported/too-large/unreadable file is an item-level skip ({@code success=false}),
 * not a thrown error, so the scan keeps going. Format classification lives in
 * {@link KnowledgeFileFormats}; rendering bytes to text is the conversion stage's job.
 */
@Service
public class LocalFolderKnowledgeReadService {

    private static final Logger log = LoggerFactory.getLogger(LocalFolderKnowledgeReadService.class);

    // Text decodes roughly 1:1, so a tight cap; a document packs its text into a binary
    // container, so it gets a larger raw-byte budget before extraction shrinks it again.
    static final long MAX_READ_BYTES = 1024L * 1024L;
    static final long MAX_DOCUMENT_READ_BYTES = 25L * 1024L * 1024L;

    public ReadResult read(KnowledgeResource resource, Path file) {
        String format = resource.getFormat();
        if (!KnowledgeFileFormats.isSupported(format)) {
            log.debug(
                    "Skipping local knowledge resource {} because format '{}' is unsupported.",
                    resource.getReference(),
                    format
            );
            return failed(resource, KnowledgePipelineReason.UNSUPPORTED_FILE_FORMAT, "Unsupported file format");
        }
        long maxBytes = KnowledgeFileFormats.isDocument(format) ? MAX_DOCUMENT_READ_BYTES : MAX_READ_BYTES;
        if (resource.getSizeBytes() > maxBytes) {
            log.debug(
                    "Skipping local knowledge resource {} because it is {} bytes.",
                    resource.getReference(),
                    resource.getSizeBytes()
            );
            return failed(
                    resource,
                    KnowledgePipelineReason.FILE_TOO_LARGE,
                    "File is larger than " + (maxBytes / (1024L * 1024L)) + " MB");
        }
        try {
            return ReadResult.of(
                    resource.getReference(),
                    resource.getDisplayName(),
                    resource.getFormat(),
                    resource.getSizeBytes(),
                    resource.getLastModifiedAt(),
                    Files.readAllBytes(file));
        } catch (IOException exception) {
            log.warn("Could not read local knowledge resource {}.", resource.getReference(), exception);
            return failed(resource, KnowledgePipelineReason.READ_FAILED, "Could not read resource");
        }
    }

    private ReadResult failed(KnowledgeResource resource, KnowledgePipelineReason reason, String message) {
        return ReadResult.failed(
                resource.getReference(),
                resource.getDisplayName(),
                resource.getFormat(),
                resource.getSizeBytes(),
                resource.getLastModifiedAt(),
                reason,
                message);
    }
}
