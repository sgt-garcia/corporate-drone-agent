package ai.corporatedroneagent.service;

import ai.corporatedroneagent.model.knowledge.KnowledgeResource;
import ai.corporatedroneagent.model.knowledge.KnowledgeResourceRead;
import ai.corporatedroneagent.model.knowledge.WorkStatus;
import ai.corporatedroneagent.repository.KnowledgeResourcePipelineRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class LocalFolderKnowledgeReadService {

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

    private final KnowledgeResourcePipelineRepository pipelineRepository;

    public LocalFolderKnowledgeReadService(KnowledgeResourcePipelineRepository pipelineRepository) {
        this.pipelineRepository = pipelineRepository;
    }

    public KnowledgeResourceRead read(KnowledgeResource resource, Path file) {
        KnowledgeResourceRead read = startRead(resource);
        if (!isSupported(resource)) {
            return finishRead(read, false, "Unsupported file format", null);
        }
        if (resource.getSizeBytes() > MAX_READ_BYTES) {
            return finishRead(read, false, "File is larger than 1 MB", null);
        }

        try {
            return finishRead(read, true, "", Files.readAllBytes(file));
        } catch (IOException exception) {
            return finishRead(read, false, "Could not read resource", null);
        }
    }

    private KnowledgeResourceRead startRead(KnowledgeResource resource) {
        KnowledgeResourceRead read = pipelineRepository.findReadByResourceId(resource.getId())
                .orElseGet(KnowledgeResourceRead::new);
        read.setResourceId(resource.getId());
        read.setStatus(WorkStatus.IN_PROGRESS);
        read.setSuccess(null);
        read.setMessage("");
        read.setValue(null);
        return pipelineRepository.saveRead(read);
    }

    private KnowledgeResourceRead finishRead(
            KnowledgeResourceRead read,
            boolean success,
            String message,
            byte[] value
    ) {
        read.setStatus(WorkStatus.DONE);
        read.setSuccess(success);
        read.setMessage(message);
        read.setValue(value);
        read.setReadAt(Instant.now());
        return pipelineRepository.saveRead(read);
    }

    private boolean isSupported(KnowledgeResource resource) {
        return SUPPORTED_TEXT_FORMATS.contains(resource.getFormat().toLowerCase(Locale.ROOT));
    }
}
