package ai.corporatedroneagent.service;

import ai.corporatedroneagent.model.knowledge.KnowledgePipelineReason;
import ai.corporatedroneagent.model.knowledge.KnowledgeResource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Reads a local file's bytes for the ingestion engine: enforces the supported-format and
 * size limits and returns a pure {@link ReadResult} (the engine records the read stage).
 * An unsupported/too-large/unreadable file is an item-level skip ({@code success=false}),
 * not a thrown error, so the scan keeps going.
 */
@Service
public class LocalFolderKnowledgeReadService {

    private static final Logger log = LoggerFactory.getLogger(LocalFolderKnowledgeReadService.class);

    static final long MAX_READ_BYTES = 1024L * 1024L;

    private static final Set<String> SUPPORTED_TEXT_FORMATS = Set.of(
            "bat",
            "cmd",
            "css",
            "csv",
            "htm",
            "html",
            "java",
            "js",
            "json",
            "jsx",
            "log",
            "md",
            "markdown",
            "properties",
            "ps1",
            "py",
            "sh",
            "sql",
            "ts",
            "tsx",
            "txt",
            "xml",
            "yaml",
            "yml"
    );

    public ReadResult read(KnowledgeResource resource, Path file) {
        if (!isSupported(resource)) {
            log.debug(
                    "Skipping local knowledge resource {} because format '{}' is unsupported.",
                    resource.getReference(),
                    resource.getFormat()
            );
            return failed(resource, KnowledgePipelineReason.UNSUPPORTED_FILE_FORMAT, "Unsupported file format");
        }
        if (resource.getSizeBytes() > MAX_READ_BYTES) {
            log.debug(
                    "Skipping local knowledge resource {} because it is {} bytes.",
                    resource.getReference(),
                    resource.getSizeBytes()
            );
            return failed(resource, KnowledgePipelineReason.FILE_TOO_LARGE, "File is larger than 1 MB");
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

    private boolean isSupported(KnowledgeResource resource) {
        return SUPPORTED_TEXT_FORMATS.contains(resource.getFormat().toLowerCase(Locale.ROOT));
    }
}
