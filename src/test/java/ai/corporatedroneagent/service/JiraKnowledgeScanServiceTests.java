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
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
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
                    JiraProjectDto project,
                    Instant updatedSince
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
                issueFetchService,
                new ObjectMapper()
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
    void scanProjectFetchesIssuesFromJiraHttpAndIndexesSearchableContext() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> issueSearchAuth = new AtomicReference<>();
        AtomicReference<String> commentAuth = new AtomicReference<>();
        server.createContext("/rest/api/3/search/jql", exchange -> {
            issueSearchAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, """
                    {
                      "isLast": true,
                      "issues": [
                        {
                          "id": "10177",
                          "key": "DEV-77",
                          "fields": {
                            "summary": "Checkout telemetry regression",
                            "description": {
                              "type": "doc",
                              "content": [
                                {
                                  "type": "paragraph",
                                  "content": [
                                    { "type": "text", "text": "checkoutunique appears in the Jira description." }
                                  ]
                                }
                              ]
                            },
                            "status": { "name": "Blocked" },
                            "issuetype": { "name": "Bug" },
                            "priority": { "name": "Highest" },
                            "assignee": { "displayName": "Ada Lovelace" },
                            "reporter": { "displayName": "Grace Hopper" },
                            "labels": [ "checkout" ],
                            "components": [ { "name": "Web" } ],
                            "fixVersions": [ { "name": "2026.6" } ],
                            "created": "2026-06-01T10:00:00.000+0000",
                            "updated": "2026-06-09T12:34:56.000+0000"
                          }
                        }
                      ]
                    }
                    """);
        });
        server.createContext("/rest/api/3/issue/DEV-77/comment", exchange -> {
            commentAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, """
                    {
                      "startAt": 0,
                      "maxResults": 50,
                      "total": 1,
                      "comments": [
                        {
                          "author": { "displayName": "Linus Torvalds" },
                          "created": "2026-06-09T13:00:00.000+0000",
                          "body": {
                            "type": "doc",
                            "content": [
                              {
                                "type": "paragraph",
                                "content": [
                                  { "type": "text", "text": "The retry guard is still missing." }
                                ]
                              }
                            ]
                          }
                        }
                      ]
                    }
                    """);
        });
        server.start();
        try {
            JiraSettings jira = jira();
            jira.setInstanceUrl("http://127.0.0.1:" + server.getAddress().getPort());
            JiraKnowledgeScanService httpScanService = new JiraKnowledgeScanService(
                    rootRepository,
                    scanRepository,
                    resourceRepository,
                    pipelineRepository,
                    new KnowledgeChunkingService(pipelineRepository),
                    indexingService,
                    new JiraIssueFetchService(HttpClient.newHttpClient(), new ObjectMapper()),
                    new ObjectMapper()
            );

            JiraKnowledgeScanService.ScanResult result = httpScanService.scanProject(jira, project(), "token-1234");

            assertThat(result.issues()).isEqualTo(1);
            String expectedAuth = "Basic " + Base64.getEncoder()
                    .encodeToString("me@example.com:token-1234".getBytes(StandardCharsets.UTF_8));
            assertThat(issueSearchAuth.get()).isEqualTo(expectedAuth);
            assertThat(commentAuth.get()).isEqualTo(expectedAuth);

            KnowledgeRoot root = rootRepository.findBySourceAndReference(
                    KnowledgeSource.JIRA,
                    JiraKnowledgeReferences.projectRootReference(jira.getInstanceUrl(), "10001")
            ).orElseThrow();
            KnowledgeResource resource = resourceRepository.findByRootId(root.getId())
                    .getFirst();
            assertThat(resource.getDisplayName()).isEqualTo("DEV-77 - Checkout telemetry regression");

            KnowledgeSearchService searchService = new KnowledgeSearchService(
                    indexingService,
                    pipelineRepository,
                    resourceRepository,
                    rootRepository
            );
            assertThat(searchService.search("checkoutunique", 5))
                    .singleElement()
                    .satisfies(snippet -> {
                        assertThat(snippet.source()).isEqualTo("JIRA");
                        assertThat(snippet.rootName()).isEqualTo("DEV - Software Development");
                        assertThat(snippet.resourceName()).isEqualTo("DEV-77 - Checkout telemetry regression");
                        assertThat(snippet.content()).contains("checkoutunique", "Status: Blocked");
                    });
        } finally {
            server.stop(0);
        }
    }

    @Test
    void scanProjectMarksMissingIssuesDeletedAndRemovesTheirIndex() {
        JiraSettings jira = jira();
        JiraProjectDto project = project();
        JiraIssueFetchService.JiraIssueDocument stale = issue("10100", "DEV-7", "staletoken");
        JiraIssueFetchService.JiraIssueDocument kept = issue("10101", "DEV-8", "kepttoken");
        issues.set(List.of(stale, kept));
        scanService.scanProject(jira, project, "token-1234");
        KnowledgeRoot firstScanRoot = rootRepository.findBySourceAndReference(
                KnowledgeSource.JIRA,
                JiraKnowledgeReferences.projectRootReference(jira.getInstanceUrl(), project.getId())
        ).orElseThrow();
        firstScanRoot.setScanSuccess(null);
        firstScanRoot.setScanFinishedAt(null);
        firstScanRoot.setConfigJson("");
        rootRepository.save(firstScanRoot);

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

    @Test
    void deltaScanFetchesOnlyUpdatedIssuesAndDoesNotDeleteUnreturnedExistingIssues() {
        JiraSettings jira = jira();
        JiraProjectDto project = project();
        AtomicReference<Instant> updatedSince = new AtomicReference<>();
        JiraIssueFetchService issueFetchService = new JiraIssueFetchService(new ObjectMapper()) {
            @Override
            public List<JiraIssueDocument> fetchProjectIssues(
                    String instanceUrl,
                    String email,
                    String token,
                    JiraProjectDto project,
                    Instant since
            ) {
                updatedSince.set(since);
                return issues.get();
            }
        };
        JiraKnowledgeScanService service = new JiraKnowledgeScanService(
                rootRepository,
                scanRepository,
                resourceRepository,
                pipelineRepository,
                new KnowledgeChunkingService(pipelineRepository),
                indexingService,
                issueFetchService,
                new ObjectMapper()
        );
        JiraIssueFetchService.JiraIssueDocument staleButUnchanged = issue("10100", "DEV-7", "staletoken");
        JiraIssueFetchService.JiraIssueDocument changed = issue("10101", "DEV-8", "changedtoken");
        issues.set(List.of(staleButUnchanged, changed));
        service.scanProject(jira, project, "token-1234");

        KnowledgeRoot root = rootRepository.findBySourceAndReference(
                KnowledgeSource.JIRA,
                JiraKnowledgeReferences.projectRootReference(jira.getInstanceUrl(), project.getId())
        ).orElseThrow();
        Instant firstScanStartedAt = root.getScanStartedAt();
        issues.set(List.of(issue("10101", "DEV-8", "changedtoken2")));

        JiraKnowledgeScanService.ScanResult result = service.scanProject(jira, project, "token-1234");

        assertThat(updatedSince.get().toEpochMilli()).isEqualTo(firstScanStartedAt.toEpochMilli());
        assertThat(result.issues()).isEqualTo(2);
        assertThat(resourceRepository.findByRootIdAndReference(root.getId(), staleButUnchanged.reference()))
                .hasValueSatisfying(resource -> assertThat(resource.isDeleted()).isFalse());
        assertThat(indexingService.searchTerm("staletoken", 10)).hasSize(1);
        assertThat(indexingService.searchTerm("changedtoken2", 10)).hasSize(1);
    }

    @Test
    void manualDeltaScanKeepsUsingLastSuccessfulCursorAfterFailedScan() {
        JiraSettings jira = jira();
        JiraProjectDto project = project();
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<Instant> thirdScanUpdatedSince = new AtomicReference<>();
        JiraIssueFetchService issueFetchService = new JiraIssueFetchService(new ObjectMapper()) {
            @Override
            public List<JiraIssueDocument> fetchProjectIssues(
                    String instanceUrl,
                    String email,
                    String token,
                    JiraProjectDto project,
                    Instant since
            ) {
                int call = calls.incrementAndGet();
                if (call == 1) {
                    return List.of(
                            issue("10100", "DEV-7", "staletoken"),
                            issue("10101", "DEV-8", "changedtoken")
                    );
                }
                if (call == 2) {
                    throw new RuntimeException("temporary jira outage");
                }
                thirdScanUpdatedSince.set(since);
                return List.of(issue("10101", "DEV-8", "changedtoken3"));
            }
        };
        JiraKnowledgeScanService service = new JiraKnowledgeScanService(
                rootRepository,
                scanRepository,
                resourceRepository,
                pipelineRepository,
                new KnowledgeChunkingService(pipelineRepository),
                indexingService,
                issueFetchService,
                new ObjectMapper()
        );

        service.scanProject(jira, project, "token-1234");
        KnowledgeRoot root = rootRepository.findBySourceAndReference(
                KnowledgeSource.JIRA,
                JiraKnowledgeReferences.projectRootReference(jira.getInstanceUrl(), project.getId())
        ).orElseThrow();
        Instant firstSuccessfulScanStartedAt = root.getScanStartedAt();

        org.junit.jupiter.api.Assertions.assertThrows(
                JiraKnowledgeScanService.JiraScanException.class,
                () -> service.scanProject(jira, project, "token-1234")
        );
        assertThat(rootRepository.findById(root.getId()))
                .hasValueSatisfying(failedRoot -> assertThat(failedRoot.getScanSuccess()).isFalse());

        JiraKnowledgeScanService.ScanResult result = service.scanProject(jira, project, "token-1234");

        assertThat(thirdScanUpdatedSince.get().toEpochMilli()).isEqualTo(firstSuccessfulScanStartedAt.toEpochMilli());
        assertThat(result.issues()).isEqualTo(2);
        assertThat(indexingService.searchTerm("staletoken", 10)).hasSize(1);
        assertThat(indexingService.searchTerm("changedtoken3", 10)).hasSize(1);
    }

    @Test
    void scheduledScanRunsFullReconciliationWhenLastFullScanIsStale() {
        JiraSettings jira = jira();
        JiraProjectDto project = project();
        JiraIssueFetchService.JiraIssueDocument stale = issue("10100", "DEV-7", "staletoken");
        JiraIssueFetchService.JiraIssueDocument kept = issue("10101", "DEV-8", "kepttoken");
        issues.set(List.of(stale, kept));
        scanService.scanProject(jira, project, "token-1234");
        KnowledgeRoot firstScanRoot = rootRepository.findBySourceAndReference(
                KnowledgeSource.JIRA,
                JiraKnowledgeReferences.projectRootReference(jira.getInstanceUrl(), project.getId())
        ).orElseThrow();
        assertThat(firstScanRoot.getConfigJson()).contains("lastFullScanFinishedAt");
        firstScanRoot.setConfigJson("{\"lastFullScanFinishedAt\":\""
                + Instant.now().minusSeconds(25 * 60 * 60) + "\"}");
        rootRepository.save(firstScanRoot);

        issues.set(List.of(kept));
        scanService.scanScheduledProject(jira, project, "token-1234");

        KnowledgeRoot root = rootRepository.findBySourceAndReference(
                KnowledgeSource.JIRA,
                JiraKnowledgeReferences.projectRootReference(jira.getInstanceUrl(), project.getId())
        ).orElseThrow();
        assertThat(resourceRepository.findByRootIdAndReference(root.getId(), stale.reference()))
                .hasValueSatisfying(resource -> assertThat(resource.isDeleted()).isTrue());
        assertThat(resourceRepository.findByRootIdAndReference(root.getId(), kept.reference()))
                .hasValueSatisfying(resource -> assertThat(resource.isDeleted()).isFalse());
        assertThat(indexingService.searchTerm("staletoken", 10)).isEmpty();
        assertThat(indexingService.searchTerm("kepttoken", 10)).hasSize(1);
    }

    @Test
    void removingJiraProjectRootDeletesStoredResourcesAndIndexEntries() {
        JiraSettings jira = jira();
        JiraProjectDto project = project();
        JiraIssueFetchService.JiraIssueDocument issue = issue("10100", "DEV-7", "removedtoken");
        issues.set(List.of(issue));
        scanService.scanProject(jira, project, "token-1234");

        KnowledgeRoot root = rootRepository.findBySourceAndReference(
                KnowledgeSource.JIRA,
                JiraKnowledgeReferences.projectRootReference(jira.getInstanceUrl(), project.getId())
        ).orElseThrow();
        KnowledgeResource resource = resourceRepository
                .findByRootIdAndReference(root.getId(), issue.reference())
                .orElseThrow();
        assertThat(indexingService.searchTerm("removedtoken", 10))
                .containsExactly(resource.getId() + ":0");

        new KnowledgeRootCleanupService(rootRepository, resourceRepository, indexingService)
                .removeJiraRoot(root.getReference());

        assertThat(rootRepository.findById(root.getId())).isEmpty();
        assertThat(resourceRepository.findByRootId(root.getId())).isEmpty();
        assertThat(indexingService.searchTerm("removedtoken", 10)).isEmpty();
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

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
