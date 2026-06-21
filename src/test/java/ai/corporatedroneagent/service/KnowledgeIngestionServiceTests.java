package ai.corporatedroneagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.corporatedroneagent.model.knowledge.KnowledgeRoot;
import ai.corporatedroneagent.model.knowledge.KnowledgeSource;
import ai.corporatedroneagent.repository.KnowledgeRootRepository;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class KnowledgeIngestionServiceTests {

    // Adding a project / "Scan now" must not block the request thread for the whole index, or the
    // settings UI freezes until indexing finishes. scanInBackground hands the scan to a worker and
    // returns; this proves the scan actually runs (engine driven, coordinator released) off-thread.
    @Test
    void scanInBackgroundRunsTheScanOffTheCallingThread() throws InterruptedException {
        KnowledgeScanEngine engine = mock(KnowledgeScanEngine.class);
        KnowledgeSourceRegistry registry = mock(KnowledgeSourceRegistry.class);
        KnowledgeSourceAdapter adapter = mock(KnowledgeSourceAdapter.class);
        KnowledgeScanCoordinator coordinator = mock(KnowledgeScanCoordinator.class);
        KnowledgeRootRepository rootRepository = mock(KnowledgeRootRepository.class);

        KnowledgeRoot root = new KnowledgeRoot();
        UUID rootId = UUID.randomUUID();
        root.setId(rootId);
        root.setSource(KnowledgeSource.JIRA);

        when(registry.adapterFor(KnowledgeSource.JIRA)).thenReturn(adapter);
        when(adapter.scanProgressId(root)).thenReturn("project-1");
        when(coordinator.tryStartScan(rootId)).thenReturn(true);
        when(rootRepository.findById(rootId)).thenReturn(Optional.of(root));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        KnowledgeIngestionService service = new KnowledgeIngestionService(
                engine, registry, coordinator, rootRepository, mock(EventService.class), executor);

        service.scanInBackground(root);

        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        verify(engine).scan(eq(root), eq(adapter), any(), any());
        verify(coordinator).finishScan(rootId);
    }
}
