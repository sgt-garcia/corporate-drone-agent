package ai.corporatedroneagent.service;

import ai.corporatedroneagent.model.knowledge.KnowledgeResource;
import ai.corporatedroneagent.model.knowledge.KnowledgeResourceChunk;
import ai.corporatedroneagent.model.knowledge.KnowledgeResourceConversion;
import ai.corporatedroneagent.repository.KnowledgeResourcePipelineRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeChunkingService {

    static final int CHUNK_SIZE = 3000;
    static final int CHUNK_OVERLAP = 1000;

    private final KnowledgeResourcePipelineRepository pipelineRepository;

    public KnowledgeChunkingService(KnowledgeResourcePipelineRepository pipelineRepository) {
        this.pipelineRepository = pipelineRepository;
    }

    public List<KnowledgeResourceChunk> chunk(KnowledgeResource resource, KnowledgeResourceConversion conversion) {
        deleteChunks(resource);
        if (!Boolean.TRUE.equals(conversion.getSuccess()) || conversion.getValue().isEmpty()) {
            return List.of();
        }

        List<KnowledgeResourceChunk> chunks = new ArrayList<>();
        String text = conversion.getValue();
        int step = CHUNK_SIZE - CHUNK_OVERLAP;
        int chunkIndex = 0;
        for (int startOffset = 0; startOffset < text.length(); startOffset += step) {
            int endOffset = Math.min(startOffset + CHUNK_SIZE, text.length());
            KnowledgeResourceChunk chunk = new KnowledgeResourceChunk();
            chunk.setResourceId(resource.getId());
            chunk.setChunkIndex(chunkIndex);
            chunk.setStartOffset(startOffset);
            chunk.setEndOffset(endOffset);
            chunk.setContentHash(contentHash(text.substring(startOffset, endOffset)));
            chunks.add(pipelineRepository.saveChunk(chunk));
            chunkIndex++;
            if (endOffset == text.length()) {
                break;
            }
        }
        return chunks;
    }

    public void deleteChunks(KnowledgeResource resource) {
        pipelineRepository.deleteChunksByResourceId(resource.getId());
    }

    private String contentHash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is not available", exception);
        }
    }
}
