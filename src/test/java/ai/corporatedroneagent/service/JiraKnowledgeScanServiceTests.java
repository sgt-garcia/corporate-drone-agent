package ai.corporatedroneagent.service;

import static org.assertj.core.api.Assertions.assertThat;

import ai.corporatedroneagent.TestDatabaseSupport;
import ai.corporatedroneagent.config.StorageProperties;
import ai.corporatedroneagent.dto.JiraProjectDto;
import ai.corporatedroneagent.model.JiraSettings;
import ai.corporatedroneagent.model.knowledge.JiraKnowledgeReferences;
import ai.corporatedroneagent.model.knowledge.KnowledgeResource;
import ai.corporatedroneagent.model.knowledge.KnowledgeResourceChunk;
import ai.corporatedroneagent.model.knowledge.KnowledgeResourceRead;
import ai.corporatedroneagent.model.knowledge.KnowledgeRoot;
import ai.corporatedroneagent.model.knowledge.KnowledgeSource;
import ai.corporatedroneagent.model.knowledge.WorkStatus;
import ai.corporatedroneagent.repository.KnowledgeResourcePipelineRepository;
import ai.corporatedroneagent.repository.KnowledgeResourceRepository;
import ai.corporatedroneagent.repository.KnowledgeRootRepository;
import ai.corporatedroneagent.repository.KnowledgeRootScanRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

class JiraKnowledgeScanServiceTests {

    @TempDir
    private Path root;
    private KnowledgeRootRepository rootRepository;
    private KnowledgeRootScanRepository scanRepository;
    private KnowledgeResourceRepository resourceRepository;
    private KnowledgeResourcePipelineRepository pipelineRepository;
    private KnowledgeIndexingService indexingService;
    private JiraKnowledgeScanService scanService;
    private AtomicReference<List<JiraIssueFetchService.JiraIssueDocument>> issues;

    @BeforeEach
    void setUp() {
        JdbcTemplate jdbcTemplate = TestDatabaseSupport.migratedJdbcTemplate();
        rootRepository = new KnowledgeRootRepository(jdbcTemplate);
        scanRepository = new KnowledgeRootScanRepository(jdbcTemplate);
        resourceRepository = new KnowledgeResourceRepository(jdbcTemplate);
        pipelineRepository = new KnowledgeResourcePipelineRepository(jdbcTemplate);
        KnowledgeChunkingService chunkingService = new KnowledgeChunkingService(pipelineRepository);
        StorageProperties storageProperties = new StorageProperties();
        storageProperties.setRoot(root);
        indexingService = new KnowledgeIndexingService(pipelineRepository, storageProperties);
        issues = new AtomicReference<>(List.of());
        JiraIssueFetchService issueFetchService = new JiraIssueFetchService(new ObjectMapper()) {
            @Override
            public List<JiraIssueDocument> fetchProjectIssues(
                    String instanceUrl,
                    String email,
                    String token,
                    JiraProjectDto project
            ) {
                assertThat(instanceUrl).isEqualTo("https://example.atlassian.net");
                assertThat(email).isEqualTo("me@example.com");
                assertThat(token).isEqualTo("token-1234");
                assertThat(project.getKey()).isEqualTo("DEV");
                return issues.get();
            }
        };
        scanService = new JiraKnowledgeScanService(
                rootRepository,
                scanRepository,
                resourceRepository,
                pipelineRepository,
                chunkingService,
                indexingService,
                issueFetchService
        );
    }

    @Test
    void scanProjectStoresIssuesAsKnowledgeResourcesAndIndexesText() {
        JiraSettings jira = jira();
        JiraProjectDto project = project();
        JiraIssueFetchService.JiraIssueDocument issue = issue("10100", "DEV-7", "firsttoken");
        issues.set(List.of(issue));

        JiraKnowledgeScanService.ScanResult result = scanService.scanProject(jira, project, "token-1234");

        assertThat(result.issues()).isEqualTo(1);
        assertThat(result.bytes()).isEqualTo(issue.sizeBytes());
        KnowledgeRoot root = rootRepository.findBySourceAndReference(
                KnowledgeSource.JIRA,
                JiraKnowledgeReferences.projectRootReference(jira.getInstanceUrl(), project.getId())
        ).orElseThrow();
        assertThat(root.getScanStatus()).isEqualTo(WorkStatus.DONE);
        assertThat(root.getScanSuccess()).isTrue();
        assertThat(root.getTotalResources()).isEqualTo(1);
        assertThat(root.getTotalSizeBytes()).isEqualTo(issue.sizeBytes());
        assertThat(scanRepository.findLatestByRootId(root.getId()))
                .hasValueSatisfying(scan -> {
                    assertThat(scan.getSuccess()).isTrue();
                    assertThat(scan.getTotalResources()).isEqualTo(1);
                });

        KnowledgeResource resource = resourceRepository
                .findByRootIdAndReference(root.getId(), issue.reference())
                .orElseThrow();
        assertThat(resource.getDisplayName()).isEqualTo("DEV-7 - Issue 10100");
        assertThat(resource.getFormat()).isEqualTo("jira-issue");
        assertThat(resource.isDeleted()).isFalse();
        assertThat(pipelineRepository.findReadByResourceId(resource.getId()))
                .hasValueSatisfying(read -> assertThat(readText(read)).contains("firsttoken"));
        assertThat(pipelineRepository.findConversionByResourceId(resource.getId()))
                .hasValueSatisfying(conversion -> assertThat(conversion.getValue()).contains("firsttoken"));
        List<KnowledgeResourceChunk> chunks = pipelineRepository.findChunksByResourceId(resource.getId());
        assertThat(chunks).hasSize(1);
        assertThat(pipelineRepository.findIndexByChunkId(chunks.getFirst().getId()))
                .hasValueSatisfying(index -> assertThat(index.getSuccess()).isTrue());
        assertThat(indexingService.searchTerm("firsttoken", 10))
                .containsExactly(resource.getId() + ":0");
    }

    @Test
    void scanProjectMarksMissingIssuesDeletedAndRemovesTheirIndex() {
        JiraSettings jira = jira();
        JiraProjectDto project = project();
        JiraIssueFetchService.JiraIssueDocument stale = issue("10100", "DEV-7", "staletoken");
        JiraIssueFetchService.JiraIssueDocument kept = issue("10101", "DEV-8", "kepttoken");
        issues.set(List.of(stale, kept));
        scanService.scanProject(jira, project, "token-1234");

        issues.set(List.of(kept));
        scanService.scanProject(jira, project, "token-1234");

        KnowledgeRoot root = rootRepository.findBySourceAndReference(
                KnowledgeSource.JIRA,
                JiraKnowledgeReferences.projectRootReference(jira.getInstanceUrl(), project.getId())
        ).orElseThrow();
        assertThat(resourceRepository.findByRootIdAndReference(root.getId(), stale.reference()))
                .hasValueSatisfying(resource -> {
                    assertThat(resource.isDeleted()).isTrue();
                    assertThat(pipelineRepository.findChunksByResourceId(resource.getId())).isEmpty();
                });
        assertThat(resourceRepository.findByRootIdAndReference(root.getId(), kept.reference()))
                .hasValueSatisfying(resource -> assertThat(resource.isDeleted()).isFalse());
        assertThat(indexingService.searchTerm("staletoken", 10)).isEmpty();
        assertThat(indexingService.searchTerm("kepttoken", 10)).hasSize(1);
    }

    private JiraIssueFetchService.JiraIssueDocument issue(String id, String key, String token) {
        String text = """
                Jira issue: %s
                Summary: Issue %s
                Description:
                %s
                """.formatted(key, id, token).strip();
        return new JiraIssueFetchService.JiraIssueDocument(
                id,
                key,
                JiraKnowledgeReferences.issueResourceReference("https://example.atlassian.net", id),
                key + " - Issue " + id,
                "jira-issue",
                text.getBytes(StandardCharsets.UTF_8).length,
                Instant.parse("2026-06-09T12:34:56Z"),
                text
        );
    }

    private JiraSettings jira() {
        JiraSettings jira = new JiraSettings();
        jira.setConnected(true);
        jira.setInstanceUrl("https://example.atlassian.net");
        jira.setEmail("me@example.com");
        jira.setTokenConfigured(true);
        return jira;
    }

    private JiraProjectDto project() {
        JiraProjectDto project = new JiraProjectDto();
        project.setId("10001");
        project.setKey("DEV");
        project.setName("Software Development");
        return project;
    }

    private String readText(KnowledgeResourceRead read) {
        return new String(read.getValue(), StandardCharsets.UTF_8);
    }
}
