package ai.corporatedroneagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import ai.corporatedroneagent.TestDatabaseSupport;
import ai.corporatedroneagent.config.StorageProperties;
import ai.corporatedroneagent.dto.JiraProjectDto;
import ai.corporatedroneagent.model.JiraSettings;
import ai.corporatedroneagent.model.knowledge.JiraKnowledgeReferences;
import ai.corporatedroneagent.model.knowledge.KnowledgePipelineReason;
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
import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
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
    private FakeJiraIssueFetchService issueFetchService;
    private ObjectMapper objectMapper;

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
        objectMapper = new ObjectMapper();
        issueFetchService = new FakeJiraIssueFetchService(objectMapper);
        KnowledgeScanEngine engine = new KnowledgeScanEngine(
                rootRepository,
                scanRepository,
                resourceRepository,
                pipelineRepository,
                chunkingService,
                indexingService,
                mock(EventService.class),
                objectMapper
        );
        scanService = new JiraKnowledgeScanService(engine, rootRepository, issueFetchService);
    }

    @Test
    void scanProjectStoresNativeJiraReadPayloadAndIndexesMarkdownConversion() throws IOException {
        JiraSettings jira = jira();
        JiraProjectDto project = project();
        JiraIssueFetchService.JiraIssueManifest manifest = manifest(jira, "10100", "DEV-7", "Issue 10100");
        JiraIssueFetchService.JiraIssueDocument document = document(jira, "10100", "DEV-7", "Issue 10100", "firsttoken");
        issueFetchService.setManifests(List.of(manifest));
        issueFetchService.putDocument(document);

        JiraKnowledgeScanService.ScanResult result = scanService.scanProject(jira, project, "token-1234");

        assertThat(result.issues()).isEqualTo(1);
        assertThat(result.bytes()).isEqualTo(document.sizeBytes());
        KnowledgeRoot root = rootRepository.findBySourceAndReference(
                KnowledgeSource.JIRA,
                JiraKnowledgeReferences.projectRootReference(jira.getInstanceUrl(), project.getId())
        ).orElseThrow();
        assertThat(root.getScanStatus()).isEqualTo(WorkStatus.DONE);
        assertThat(root.getScanSuccess()).isTrue();
        assertThat(root.getTotalResources()).isEqualTo(1);

        KnowledgeResource resource = resourceRepository
                .findByRootIdAndReference(root.getId(), manifest.reference())
                .orElseThrow();
        assertThat(resource.getDisplayName()).isEqualTo("DEV-7 - Issue 10100");
        assertThat(resource.getFormat()).isEqualTo("jira-issue");
        assertThat(resource.isDeleted()).isFalse();
        assertThat(pipelineRepository.findReadByResourceId(resource.getId()))
                .hasValueSatisfying(read -> {
                    JsonNode payload = readJson(read);
                    assertThat(payload.path("issue").path("key").asText()).isEqualTo("DEV-7");
                    assertThat(payload.path("comments").get(0).path("body").toString()).contains("firsttoken");
                });
        assertThat(pipelineRepository.findConversionByResourceId(resource.getId()))
                .hasValueSatisfying(conversion -> assertThat(conversion.getValue())
                        .contains("# DEV-7 - Issue 10100")
                        .contains("Issue key: DEV-7")
                        .contains("firsttoken"));
        List<KnowledgeResourceChunk> chunks = pipelineRepository.findChunksByResourceId(resource.getId());
        assertThat(chunks).hasSize(1);
        assertThat(pipelineRepository.findIndexByChunkId(chunks.getFirst().getId()))
                .hasValueSatisfying(index -> assertThat(index.getSuccess()).isTrue());
        assertThat(indexingService.searchTerm("firsttoken", 10))
                .containsExactly(resource.getId() + ":0");
    }

    @Test
    void cancelledScanStopsBetweenIssuesAndDoesNotAdvanceCursor() {
        JiraSettings jira = jira();
        JiraProjectDto project = project();
        issueFetchService.setManifests(List.of(
                manifest(jira, "10100", "DEV-7", "Issue 10100"),
                manifest(jira, "10101", "DEV-8", "Issue 10101")
        ));
        issueFetchService.putDocument(document(jira, "10100", "DEV-7", "Issue 10100", "firsttoken"));
        issueFetchService.putDocument(document(jira, "10101", "DEV-8", "Issue 10101", "secondtoken"));

        // Let the first issue through, then cancel before the second.
        AtomicInteger checks = new AtomicInteger();
        BooleanSupplier isCancelled = () -> checks.getAndIncrement() >= 1;

        JiraKnowledgeScanService.ScanResult result =
                scanService.scanProject(jira, project, "token-1234", item -> {}, isCancelled);

        // Only the first issue was indexed before cancellation.
        assertThat(result.issues()).isEqualTo(1);
        KnowledgeRoot root = rootRepository.findBySourceAndReference(
                KnowledgeSource.JIRA,
                JiraKnowledgeReferences.projectRootReference(jira.getInstanceUrl(), project.getId())
        ).orElseThrow();
        assertThat(root.getScanStatus()).isEqualTo(WorkStatus.DONE);
        // The cursor must not advance, so the next scan re-covers the skipped issue.
        assertThat(root.getConfigJson()).doesNotContain("lastSuccessfulScanStartedAt");
    }

    @Test
    void conversionFailurePersistsNativeReadAndRecordsFailedConversionWithoutAbortingScan() {
        JiraSettings jira = jira();
        JiraProjectDto project = project();
        JiraIssueFetchService.JiraIssueManifest manifest = manifest(jira, "10100", "DEV-7", "Issue 10100");
        JiraIssueFetchService.JiraIssueDocument document = document(jira, "10100", "DEV-7", "Issue 10100", "firsttoken");
        issueFetchService.setManifests(List.of(manifest));
        issueFetchService.putDocument(document);
        issueFetchService.throwOnMarkdown = true;

        // A single item's conversion failure no longer aborts the scan; it records a failed
        // conversion and the scan completes, keeping the native read so nothing is lost.
        JiraKnowledgeScanService.ScanResult result = scanService.scanProject(jira, project, "token-1234");
        assertThat(result.issues()).isEqualTo(1);

        KnowledgeRoot root = rootRepository.findBySourceAndReference(
                KnowledgeSource.JIRA,
                JiraKnowledgeReferences.projectRootReference(jira.getInstanceUrl(), project.getId())
        ).orElseThrow();
        assertThat(root.getScanStatus()).isEqualTo(WorkStatus.DONE);
        assertThat(root.getScanSuccess()).isTrue();
        KnowledgeResource resource = resourceRepository
                .findByRootIdAndReference(root.getId(), manifest.reference())
                .orElseThrow();
        assertThat(pipelineRepository.findReadByResourceId(resource.getId()))
                .hasValueSatisfying(read -> {
                    JsonNode payload = readJson(read);
                    assertThat(payload.path("issue").path("key").asText()).isEqualTo("DEV-7");
                    assertThat(payload.path("comments").get(0).path("body").toString()).contains("firsttoken");
                });
        assertThat(pipelineRepository.findConversionByResourceId(resource.getId()))
                .hasValueSatisfying(conversion -> {
                    assertThat(conversion.getSuccess()).isFalse();
                    assertThat(conversion.getReason()).isEqualTo(KnowledgePipelineReason.CONVERSION_FAILED);
                });
    }

    @Test
    void incrementalScanUsesLastSuccessfulCursorAndDoesNotDeleteUnreturnedIssues() {
        JiraSettings jira = jira();
        JiraProjectDto project = project();
        JiraIssueFetchService.JiraIssueManifest stale = manifest(jira, "10100", "DEV-7", "Issue 10100");
        JiraIssueFetchService.JiraIssueManifest changed = manifest(jira, "10101", "DEV-8", "Issue 10101");
        issueFetchService.setManifests(List.of(stale, changed));
        issueFetchService.putDocument(document(jira, "10100", "DEV-7", "Issue 10100", "staletoken"));
        issueFetchService.putDocument(document(jira, "10101", "DEV-8", "Issue 10101", "changedtoken"));
        scanService.scanProject(jira, project, "token-1234");

        KnowledgeRoot root = rootRepository.findBySourceAndReference(
                KnowledgeSource.JIRA,
                JiraKnowledgeReferences.projectRootReference(jira.getInstanceUrl(), project.getId())
        ).orElseThrow();
        Instant firstScanStartedAt = root.getScanStartedAt();
        JiraIssueFetchService.JiraIssueManifest changedAgain = manifest(
                jira,
                "10101",
                "DEV-8",
                "Issue 10101",
                Instant.parse("2026-06-10T12:34:56Z")
        );
        issueFetchService.setManifests(List.of(changedAgain));
        issueFetchService.putDocument(document(
                jira,
                "10101",
                "DEV-8",
                "Issue 10101",
                "changedtoken2",
                Instant.parse("2026-06-10T12:34:56Z")
        ));

        JiraKnowledgeScanService.ScanResult result = scanService.scanProject(jira, project, "token-1234");

        assertThat(issueFetchService.updatedSince.get().toEpochMilli()).isEqualTo(firstScanStartedAt.toEpochMilli());
        assertThat(result.issues()).isEqualTo(2);
        assertThat(resourceRepository.findByRootIdAndReference(root.getId(), stale.reference()))
                .hasValueSatisfying(resource -> assertThat(resource.isDeleted()).isFalse());
        assertThat(indexingService.searchTerm("staletoken", 10)).hasSize(1);
        assertThat(indexingService.searchTerm("changedtoken2", 10)).hasSize(1);
    }

    @Test
    void scheduledScanUsesLastSuccessfulCursorAndDoesNotReconcileDeletions() {
        JiraSettings jira = jira();
        JiraProjectDto project = project();
        JiraIssueFetchService.JiraIssueManifest stale = manifest(jira, "10100", "DEV-7", "Issue 10100");
        JiraIssueFetchService.JiraIssueManifest changed = manifest(jira, "10101", "DEV-8", "Issue 10101");
        issueFetchService.setManifests(List.of(stale, changed));
        issueFetchService.putDocument(document(jira, "10100", "DEV-7", "Issue 10100", "staletoken"));
        issueFetchService.putDocument(document(jira, "10101", "DEV-8", "Issue 10101", "changedtoken"));
        scanService.scanProject(jira, project, "token-1234");

        KnowledgeRoot root = rootRepository.findBySourceAndReference(
                KnowledgeSource.JIRA,
                JiraKnowledgeReferences.projectRootReference(jira.getInstanceUrl(), project.getId())
        ).orElseThrow();
        Instant firstScanStartedAt = root.getScanStartedAt();
        JiraIssueFetchService.JiraIssueManifest changedAgain = manifest(
                jira,
                "10101",
                "DEV-8",
                "Issue 10101",
                Instant.parse("2026-06-10T12:34:56Z")
        );
        issueFetchService.setManifests(List.of(changedAgain));
        issueFetchService.putDocument(document(
                jira,
                "10101",
                "DEV-8",
                "Issue 10101",
                "changedtoken2",
                Instant.parse("2026-06-10T12:34:56Z")
        ));

        JiraKnowledgeScanService.ScanResult result = scanService.scanScheduledProject(jira, project, "token-1234");

        assertThat(issueFetchService.updatedSince.get().toEpochMilli()).isEqualTo(firstScanStartedAt.toEpochMilli());
        assertThat(result.issues()).isEqualTo(2);
        assertThat(resourceRepository.findByRootIdAndReference(root.getId(), stale.reference()))
                .hasValueSatisfying(resource -> assertThat(resource.isDeleted()).isFalse());
        assertThat(indexingService.searchTerm("staletoken", 10)).hasSize(1);
        assertThat(indexingService.searchTerm("changedtoken2", 10)).hasSize(1);
    }

    @Test
    void scanMetadataDropsLegacyFullReconciliationCursor() throws IOException {
        JiraSettings jira = jira();
        JiraProjectDto project = project();
        JiraIssueFetchService.JiraIssueManifest issue = manifest(jira, "10100", "DEV-7", "Issue 10100");
        issueFetchService.setManifests(List.of(issue));
        issueFetchService.putDocument(document(jira, "10100", "DEV-7", "Issue 10100", "firsttoken"));
        scanService.scanProject(jira, project, "token-1234");

        KnowledgeRoot root = rootRepository.findBySourceAndReference(
                KnowledgeSource.JIRA,
                JiraKnowledgeReferences.projectRootReference(jira.getInstanceUrl(), project.getId())
        ).orElseThrow();
        root.setConfigJson("""
                {
                  "lastFullScanFinishedAt": "2026-06-12T00:00:00Z",
                  "kept": "value"
                }
                """);
        rootRepository.save(root);
        issueFetchService.setManifests(List.of());

        scanService.scanProject(jira, project, "token-1234");

        KnowledgeRoot updatedRoot = rootRepository.findById(root.getId()).orElseThrow();
        JsonNode config = objectMapper.readTree(updatedRoot.getConfigJson());
        assertThat(config.has("lastFullScanFinishedAt")).isFalse();
        assertThat(config.path("lastSuccessfulScanStartedAt").asText()).isNotBlank();
        assertThat(config.path("kept").asText()).isEqualTo("value");
    }

    @Test
    void failedScanDoesNotAdvanceLastSuccessfulCursor() {
        JiraSettings jira = jira();
        JiraProjectDto project = project();
        JiraIssueFetchService.JiraIssueManifest issue = manifest(jira, "10100", "DEV-7", "Issue 10100");
        issueFetchService.setManifests(List.of(issue));
        issueFetchService.putDocument(document(jira, "10100", "DEV-7", "Issue 10100", "firsttoken"));
        scanService.scanProject(jira, project, "token-1234");
        KnowledgeRoot root = rootRepository.findBySourceAndReference(
                KnowledgeSource.JIRA,
                JiraKnowledgeReferences.projectRootReference(jira.getInstanceUrl(), project.getId())
        ).orElseThrow();
        Instant firstSuccessfulScanStartedAt = root.getScanStartedAt();

        issueFetchService.throwOnManifest = true;
        org.junit.jupiter.api.Assertions.assertThrows(
                JiraKnowledgeScanService.JiraScanException.class,
                () -> scanService.scanProject(jira, project, "token-1234")
        );
        issueFetchService.throwOnManifest = false;
        issueFetchService.setManifests(List.of());

        scanService.scanProject(jira, project, "token-1234");

        assertThat(issueFetchService.updatedSince.get().toEpochMilli())
                .isEqualTo(firstSuccessfulScanStartedAt.toEpochMilli());
    }

    @Test
    void scanProjectFetchesJiraHttpPayloadAndSearchFindsExactTicketKey() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> issueSearchAuth = new AtomicReference<>();
        AtomicReference<String> issueReadAuth = new AtomicReference<>();
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
                            "updated": "2026-06-09T12:34:56.000+0000"
                          }
                        }
                      ]
                    }
                    """);
        });
        server.createContext("/rest/api/3/issue/DEV-77", exchange -> {
            issueReadAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, """
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
                        "updated": "2026-06-09T12:34:56.000+0000"
                      }
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
            JiraIssueFetchService httpFetchService = new JiraIssueFetchService(HttpClient.newHttpClient(), objectMapper);
            KnowledgeScanEngine httpEngine = new KnowledgeScanEngine(
                    rootRepository,
                    scanRepository,
                    resourceRepository,
                    pipelineRepository,
                    new KnowledgeChunkingService(pipelineRepository),
                    indexingService,
                    mock(EventService.class),
                    objectMapper
            );
            JiraKnowledgeScanService httpScanService =
                    new JiraKnowledgeScanService(httpEngine, rootRepository, httpFetchService);

            JiraKnowledgeScanService.ScanResult result = httpScanService.scanProject(jira, project(), "token-1234");

            assertThat(result.issues()).isEqualTo(1);
            String expectedAuth = "Basic " + Base64.getEncoder()
                    .encodeToString("me@example.com:token-1234".getBytes(StandardCharsets.UTF_8));
            assertThat(issueSearchAuth.get()).isEqualTo(expectedAuth);
            assertThat(issueReadAuth.get()).isEqualTo(expectedAuth);
            assertThat(commentAuth.get()).isEqualTo(expectedAuth);

            KnowledgeSearchService searchService = new KnowledgeSearchService(
                    indexingService,
                    pipelineRepository,
                    resourceRepository,
                    rootRepository
            );
            assertThat(searchService.search("What is happening in DEV-77?", 5))
                    .first()
                    .satisfies(snippet -> {
                        assertThat(snippet.source()).isEqualTo("JIRA");
                        assertThat(snippet.rootName()).isEqualTo("DEV - Software Development");
                        assertThat(snippet.resourceName()).isEqualTo("DEV-77 - Checkout telemetry regression");
                        assertThat(snippet.content()).contains("Issue key: DEV-77", "checkoutunique");
                    });
        } finally {
            server.stop(0);
        }
    }

    @Test
    void removingJiraProjectRootDeletesStoredResourcesAndIndexEntries() {
        JiraSettings jira = jira();
        JiraProjectDto project = project();
        JiraIssueFetchService.JiraIssueManifest issue = manifest(jira, "10100", "DEV-7", "Issue 10100");
        issueFetchService.setManifests(List.of(issue));
        issueFetchService.putDocument(document(jira, "10100", "DEV-7", "Issue 10100", "removedtoken"));
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

    private JiraIssueFetchService.JiraIssueManifest manifest(
            JiraSettings jira,
            String id,
            String key,
            String summary
    ) {
        return manifest(jira, id, key, summary, Instant.parse("2026-06-09T12:34:56Z"));
    }

    private JiraIssueFetchService.JiraIssueManifest manifest(
            JiraSettings jira,
            String id,
            String key,
            String summary,
            Instant lastModifiedAt
    ) {
        return new JiraIssueFetchService.JiraIssueManifest(
                id,
                key,
                JiraKnowledgeReferences.issueResourceReference(jira.getInstanceUrl(), id),
                key + " - " + summary,
                "jira-issue",
                (key + "\n" + summary).getBytes(StandardCharsets.UTF_8).length,
                lastModifiedAt
        );
    }

    private JiraIssueFetchService.JiraIssueDocument document(
            JiraSettings jira,
            String id,
            String key,
            String summary,
            String token
    ) {
        return document(jira, id, key, summary, token, Instant.parse("2026-06-09T12:34:56Z"));
    }

    private JiraIssueFetchService.JiraIssueDocument document(
            JiraSettings jira,
            String id,
            String key,
            String summary,
            String token,
            Instant lastModifiedAt
    ) {
        byte[] value = readPayload(id, key, summary, token);
        return new JiraIssueFetchService.JiraIssueDocument(
                id,
                key,
                JiraKnowledgeReferences.issueResourceReference(jira.getInstanceUrl(), id),
                key + " - " + summary,
                "jira-issue",
                value.length,
                lastModifiedAt,
                value
        );
    }

    private byte[] readPayload(String id, String key, String summary, String token) {
        return """
                {
                  "source": "jira",
                  "apiVersion": "3",
                  "fetchedAt": "2026-06-12T19:00:00Z",
                  "issue": {
                    "id": "%s",
                    "key": "%s",
                    "fields": {
                      "summary": "%s",
                      "description": {
                        "type": "doc",
                        "content": [
                          {
                            "type": "paragraph",
                            "content": [
                              { "type": "text", "text": "Description text." }
                            ]
                          }
                        ]
                      },
                      "status": { "name": "In Progress" },
                      "issuetype": { "name": "Task" },
                      "updated": "2026-06-09T12:34:56.000+0000"
                    }
                  },
                  "comments": [
                    {
                      "author": { "displayName": "Reviewer" },
                      "created": "2026-06-09T13:00:00.000+0000",
                      "body": {
                        "type": "doc",
                        "content": [
                          {
                            "type": "paragraph",
                            "content": [
                              { "type": "text", "text": "%s" }
                            ]
                          }
                        ]
                      }
                    }
                  ]
                }
                """.formatted(id, key, summary, token).getBytes(StandardCharsets.UTF_8);
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

    private JsonNode readJson(KnowledgeResourceRead read) {
        try {
            return objectMapper.readTree(read.getValue());
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static final class FakeJiraIssueFetchService extends JiraIssueFetchService {

        private final AtomicReference<List<JiraIssueManifest>> manifests = new AtomicReference<>(List.of());
        private final Map<String, JiraIssueDocument> documents = new HashMap<>();
        private final AtomicReference<Instant> updatedSince = new AtomicReference<>();
        private boolean throwOnManifest;
        private boolean throwOnMarkdown;

        private FakeJiraIssueFetchService(ObjectMapper objectMapper) {
            super(objectMapper);
        }

        private void setManifests(List<JiraIssueManifest> manifests) {
            this.manifests.set(manifests);
        }

        private void putDocument(JiraIssueDocument document) {
            documents.put(document.key(), document);
        }

        @Override
        public List<JiraIssueManifest> fetchProjectIssueManifests(
                String instanceUrl,
                String email,
                String token,
                JiraProjectDto project,
                Instant updatedSince,
                String apiVersion
        ) {
            assertThat(instanceUrl).isEqualTo("https://example.atlassian.net");
            assertThat(email).isEqualTo("me@example.com");
            assertThat(token).isEqualTo("token-1234");
            assertThat(project.getKey()).isEqualTo("DEV");
            assertThat(apiVersion).isEqualTo("3");
            this.updatedSince.set(updatedSince);
            if (throwOnManifest) {
                throw new RuntimeException("temporary jira outage");
            }
            return manifests.get();
        }

        @Override
        public JiraIssueDocument fetchIssueDocument(
                String instanceUrl,
                String email,
                String token,
                String apiVersion,
                JiraIssueManifest manifest
        ) {
            return documents.get(manifest.key());
        }

        @Override
        public String toMarkdown(byte[] readValue) {
            if (throwOnMarkdown) {
                throw new RuntimeException("markdown conversion failed");
            }
            return super.toMarkdown(readValue);
        }
    }
}
