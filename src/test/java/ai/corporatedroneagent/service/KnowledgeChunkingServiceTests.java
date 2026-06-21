package ai.corporatedroneagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.corporatedroneagent.model.knowledge.KnowledgeResource;
import ai.corporatedroneagent.model.knowledge.KnowledgeResourceChunk;
import ai.corporatedroneagent.model.knowledge.KnowledgeResourceConversion;
import ai.corporatedroneagent.repository.KnowledgeResourcePipelineRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class KnowledgeChunkingServiceTests {

    @Test
    void chunkDeletesExistingChunksThenInsertsNewChunksDirectly() {
        KnowledgeResourcePipelineRepository pipelineRepository = mock(KnowledgeResourcePipelineRepository.class);
        when(pipelineRepository.insertChunk(any(KnowledgeResourceChunk.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        KnowledgeChunkingService chunkingService = new KnowledgeChunkingService(pipelineRepository);
        KnowledgeResource resource = new KnowledgeResource();
        resource.setId(UUID.randomUUID());
        KnowledgeResourceConversion conversion = new KnowledgeResourceConversion();
        conversion.setSuccess(true);
        conversion.setValue("a".repeat(3500));

        java.util.List<KnowledgeResourceChunk> chunks = chunkingService.chunk(resource, conversion);

        assertThat(chunks)
                .hasSize(2)
                .extracting(KnowledgeResourceChunk::getChunkIndex)
                .containsExactly(0, 1);
        InOrder order = inOrder(pipelineRepository);
        order.verify(pipelineRepository).deleteChunksByResourceId(resource.getId());
        order.verify(pipelineRepository, times(2)).insertChunk(any(KnowledgeResourceChunk.class));
        verify(pipelineRepository, times(2)).insertChunk(any(KnowledgeResourceChunk.class));
    }
}
