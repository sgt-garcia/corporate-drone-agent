package ai.corporatedroneagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.corporatedroneagent.model.knowledge.KnowledgeRoot;
import ai.corporatedroneagent.model.knowledge.KnowledgeSource;
import ai.corporatedroneagent.repository.KnowledgeRootRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class KnowledgeRootServiceTests {

    private final KnowledgeRootRepository repository = mock(KnowledgeRootRepository.class);
    private final KnowledgeRootService service = new KnowledgeRootService(repository);

    @Test
    void createRootRequiresReference() {
        assertThatThrownBy(() -> service.createRoot(KnowledgeSource.LOCAL_FOLDER, " ", "", ""))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Knowledge root reference is required");
    }

    @Test
    void createRootRejectsDuplicateSourceReference() {
        when(repository.findBySourceAndReference(KnowledgeSource.LOCAL_FOLDER, "C:\\Data"))
                .thenReturn(Optional.of(new KnowledgeRoot()));

        assertThatThrownBy(() -> service.createRoot(KnowledgeSource.LOCAL_FOLDER, "C:\\Data", "Data", ""))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Knowledge root is already configured");
    }

    @Test
    void createRootDefaultsDisplayNameToReference() {
        when(repository.save(org.mockito.ArgumentMatchers.any(KnowledgeRoot.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        KnowledgeRoot root = service.createRoot(KnowledgeSource.LOCAL_FOLDER, "C:\\Data", "", "");

        assertThat(root.getDisplayName()).isEqualTo("C:\\Data");
    }
}
