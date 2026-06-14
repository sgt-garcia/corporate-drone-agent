package ai.corporatedroneagent.service;

import ai.corporatedroneagent.dto.JiraProjectDto;
import ai.corporatedroneagent.model.JiraSettings;
import ai.corporatedroneagent.model.knowledge.JiraKnowledgeReferences;
import ai.corporatedroneagent.model.knowledge.KnowledgeResource;
import ai.corporatedroneagent.model.knowledge.KnowledgeResourceChunk;
import ai.corporatedroneagent.model.knowledge.KnowledgeResourceConversion;
import ai.corporatedroneagent.model.knowledge.KnowledgeResourceRead;
import ai.corporatedroneagent.model.knowledge.KnowledgeRoot;
import ai.corporatedroneagent.model.knowledge.KnowledgeRootScan;
import ai.corporatedroneagent.model.knowledge.KnowledgeSource;
import ai.corporatedroneagent.model.knowledge.WorkStatus;
import ai.corporatedroneagent.repository.KnowledgeResourcePipelineRepository;
import ai.corporatedroneagent.repository.KnowledgeResourceRepository;
import ai.corporatedroneagent.repository.KnowledgeRootRepository;
import ai.corporatedroneagent.repository.KnowledgeRootScanRepository;
import ai.corporatedroneagent.util.Strings;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class JiraKnowledgeScanService {

    private static final Logger log = LoggerFactory.getLogger(JiraKnowledgeScanService.class);
    private static final String CONFIG_LEGACY_LAST_FULL_SCAN_FINISHED_AT = "lastFullScanFinishedAt";
    private static final String CONFIG_LAST_SUCCESSFUL_SCAN_STARTED_AT = "lastSuccessfulScanStartedAt";

    private final KnowledgeRootRepository rootRepository;
    private final KnowledgeRootScanRepository scanRepository;
    private final KnowledgeResourceRepository resourceRepository;
    private final KnowledgeResourcePipelineRepository pipelineRepository;
    private final KnowledgeChunkingService chunkingService;
    private final KnowledgeIndexingService indexingService;
    private final JiraIssueFetchService issueFetchService;
    private final ObjectMapper objectMapper;
    private final EventService eventService;

    public JiraKnowledgeScanService(
            KnowledgeRootRepository rootRepository,
            KnowledgeRootScanRepository scanRepository,
            KnowledgeResourceRepository resourceRepository,
            KnowledgeResourcePipelineRepository pipelineRepository,
            KnowledgeChunkingService chunkingService,
            KnowledgeIndexingService indexingService,
            JiraIssueFetchService issueFetchService,
            ObjectMapper objectMapper,
            EventService eventService
    ) {
        this.rootRepository = rootRepository;
        this.scanRepository = scanRepository;
        this.resourceRepository = resourceRepository;
        this.pipelineRepository = pipelineRepository;
        this.chunkingService = chunkingService;
        this.indexingService = indexingService;
        this.issueFetchService = issueFetchService;
        this.objectMapper = objectMapper;
        this.eventService = eventService;
    }

    private static final Consumer<String> NO_PROGRESS = item -> {
    };

    public ScanResult scanProject(JiraSettings jira, JiraProjectDto project, String token) {
        return scanProject(jira, project, token, NO_PROGRESS);
    }

    public ScanResult scanProject(
            JiraSettings jira,
            JiraProjectDto project,
            String token,
            Consumer<String> onProgress
    ) {
        return scanProjectInternal(jira, project, token, onProgress);
    }

    public ScanResult scanScheduledProject(JiraSettings jira, JiraProjectDto project, String token) {
        return scanScheduledProject(jira, project, token, NO_PROGRESS);
    }

    public ScanResult scanScheduledProject(
            JiraSettings jira,
            JiraProjectDto project,
            String token,
            Consumer<String> onProgress
    ) {
        return scanProjectInternal(jira, project, token, onProgress);
    }

    private ScanResult scanProjectInternal(
            JiraSettings jira,
            JiraProjectDto project,
            String token,
            Consumer<String> onProgress
    ) {
        Instant startedAt = Instant.now();
        KnowledgeRoot root = knowledgeRoot(jira, project);
        Instant updatedSince = updatedSince(root);
        boolean incrementalScan = updatedSince != null;
        root = startRootScan(root, startedAt);
        KnowledgeRootScan scan = startScan(root.getId(), startedAt);
        ScanStats stats = new ScanStats();
        if (incrementalScan) {
            log.info("Started incremental Jira project scan for {} since {}.", project.getKey(), updatedSince);
        } else {
            log.info("Started Jira project partial scan for {} without a cursor.", project.getKey());
        }

        try {
            List<JiraIssueFetchService.JiraIssueManifest> issues = issueFetchService.fetchProjectIssueManifests(
                    jira.getInstanceUrl(),
                    jira.getEmail(),
                    token,
                    project,
                    updatedSince,
                    jira.getApiVersion()
            );
            stats.add(issues);
            processIssues(jira, token, root.getId(), issues, startedAt, onProgress);
            ResourceTotals totals = activeResourceTotals(root.getId());
            completeScan(root, scan, totals, null);
            log.info(
                    "Completed {} Jira project scan for {} with {} fetched issue manifests, {} indexed issues, and {} bytes.",
                    incrementalScan ? "incremental" : "partial",
                    project.getKey(),
                    stats.resources(),
                    totals.resources(),
                    totals.bytes()
            );
            return new ScanResult(totals.resources(), totals.bytes());
        } catch (ResponseStatusException exception) {
            ResourceTotals totals = activeResourceTotals(root.getId());
            completeScan(root, scan, totals, scanErrorMessage(exception));
            log.warn(
                    "Jira project scan failed for {} after {} issues and {} bytes.",
                    project.getKey(),
                    stats.resources(),
                    stats.bytes(),
                    exception
            );
            throw exception;
        } catch (RuntimeException exception) {
            ResourceTotals totals = activeResourceTotals(root.getId());
            completeScan(root, scan, totals, "Could not scan Jira project");
            log.warn(
                    "Jira project scan failed for {} after {} issues and {} bytes.",
                    project.getKey(),
                    stats.resources(),
                    stats.bytes(),
                    exception
            );
            throw new JiraScanException("Could not scan Jira project", exception);
        }
    }

    private Instant updatedSince(KnowledgeRoot root) {
        if (root.getId() == null) {
            return null;
        }
        Instant configuredCursor = configInstant(root, CONFIG_LAST_SUCCESSFUL_SCAN_STARTED_AT);
        if (configuredCursor != null) {
            return configuredCursor;
        }
        if (root.getScanStatus() != WorkStatus.DONE
                || !Boolean.TRUE.equals(root.getScanSuccess())
                || (root.getScanStartedAt() == null && root.getScanFinishedAt() == null)) {
            return null;
        }
        // Backward compatibility for roots scanned before config metadata existed.
        return root.getScanStartedAt() == null ? root.getScanFinishedAt() : root.getScanStartedAt();
    }

    private void processIssues(
            JiraSettings jira,
            String token,
            UUID rootId,
            List<JiraIssueFetchService.JiraIssueManifest> issues,
            Instant scannedAt,
            Consumer<String> onProgress
    ) {
        Map<String, KnowledgeResource> existingResources = resourceRepository.findByRootId(rootId).stream()
                .collect(Collectors.toMap(
                        KnowledgeResource::getReference,
                        resource -> resource,
                        (first, second) -> first
                ));
        Set<UUID> reusablePipelineResourceIds = pipelineRepository.findReusablePipelineResourceIdsByRootId(rootId);
        for (JiraIssueFetchService.JiraIssueManifest issue : issues) {
            onProgress.accept(issue.key());
            saveResource(
                    jira,
                    token,
                    issue,
                    rootId,
                    existingResources.get(issue.reference()),
                    reusablePipelineResourceIds,
                    scannedAt
            );
        }
    }

    private void saveResource(
            JiraSettings jira,
            String token,
            JiraIssueFetchService.JiraIssueManifest issue,
            UUID rootId,
            KnowledgeResource existingResource,
            Set<UUID> reusablePipelineResourceIds,
            Instant scannedAt
    ) {
        if (existingResource != null && canReusePipeline(existingResource, issue, reusablePipelineResourceIds)) {
            log.debug("Skipping unchanged Jira knowledge resource {}.", issue.reference());
            return;
        }

        JiraIssueFetchService.JiraIssueDocument document = issueFetchService.fetchIssueDocument(
                jira.getInstanceUrl(),
                jira.getEmail(),
                token,
                jira.getApiVersion(),
                issue
        );
        KnowledgeResource resource = new KnowledgeResource();
        resource.setRootId(rootId);
        if (existingResource != null) {
            resource.setId(existingResource.getId());
        }
        resource.setReference(document.reference());
        resource.setDisplayName(document.displayName());
        resource.setFormat(document.format());
        resource.setSizeBytes(document.sizeBytes());
        resource.setLastModifiedAt(document.lastModifiedAt());
        resource.setDeleted(false);
        resource.setScannedAt(scannedAt);
        KnowledgeResource savedResource = resourceRepository.save(resource);
        indexingService.deleteResource(savedResource);

        KnowledgeResourceRead read = successfulRead(savedResource.getId(), document.readValue(), scannedAt);
        pipelineRepository.saveRead(read);
        KnowledgeResourceConversion conversion = successfulConversion(
                savedResource.getId(),
                issueFetchService.toMarkdown(document.readValue()),
                scannedAt
        );
        KnowledgeResourceConversion savedConversion = pipelineRepository.saveConversion(conversion);
        List<KnowledgeResourceChunk> chunks = chunkingService.chunk(savedResource, savedConversion);
        indexingService.index(savedResource, savedConversion, chunks);
    }

    private boolean canReusePipeline(
            KnowledgeResource resource,
            JiraIssueFetchService.JiraIssueManifest issue,
            Set<UUID> reusablePipelineResourceIds
    ) {
        return !resource.isDeleted()
                && sameTimestamp(resource.getLastModifiedAt(), issue.lastModifiedAt())
                && reusablePipelineResourceIds.contains(resource.getId());
    }

    private boolean sameTimestamp(Instant first, Instant second) {
        if (first == null || second == null) {
            return first == second;
        }
        return first.toEpochMilli() == second.toEpochMilli();
    }

    private KnowledgeResourceRead successfulRead(UUID resourceId, byte[] value, Instant scannedAt) {
        KnowledgeResourceRead read = new KnowledgeResourceRead();
        read.setResourceId(resourceId);
        read.setStatus(WorkStatus.DONE);
        read.setSuccess(true);
        read.setReason(null);
        read.setMessage("");
        read.setValue(value);
        read.setReadAt(scannedAt);
        return read;
    }

    private KnowledgeResourceConversion successfulConversion(UUID resourceId, String text, Instant scannedAt) {
        KnowledgeResourceConversion conversion = new KnowledgeResourceConversion();
        conversion.setResourceId(resourceId);
        conversion.setStatus(WorkStatus.DONE);
        conversion.setSuccess(true);
        conversion.setReason(null);
        conversion.setMessage("");
        conversion.setValue(text);
        conversion.setConvertedAt(scannedAt);
        return conversion;
    }

    private KnowledgeRoot knowledgeRoot(JiraSettings jira, JiraProjectDto project) {
        String reference = JiraKnowledgeReferences.projectRootReference(
                jira.getInstanceUrl(),
                jiraProjectReferenceId(project)
        );
        KnowledgeRoot root = rootRepository.findBySourceAndReference(KnowledgeSource.JIRA, reference)
                .orElseGet(KnowledgeRoot::new);
        root.setSource(KnowledgeSource.JIRA);
        root.setReference(reference);
        root.setDisplayName(jiraProjectDisplayName(project));
        // Do not re-seed paused from the stored project status: root.paused is the
        // live source of truth (a pause may have landed after this snapshot), and
        // re-seeding here would clobber it.
        return root;
    }

    private KnowledgeRoot startRootScan(KnowledgeRoot root, Instant startedAt) {
        root.setScanStatus(WorkStatus.IN_PROGRESS);
        root.setScanSuccess(null);
        root.setScanMessage("");
        root.setScanStartedAt(startedAt);
        root.setScanFinishedAt(null);
        KnowledgeRoot saved = rootRepository.save(root);
        // Announce the flip to IN_PROGRESS so the settings screen derives "scanning"
        // from the root, exactly like a local-folder scan does.
        publishSettingsUpdated();
        return saved;
    }

    private KnowledgeRootScan startScan(UUID rootId, Instant startedAt) {
        KnowledgeRootScan scan = new KnowledgeRootScan();
        scan.setRootId(rootId);
        scan.setStatus(WorkStatus.IN_PROGRESS);
        scan.setStartedAt(startedAt);
        return scanRepository.save(scan);
    }

    private ResourceTotals activeResourceTotals(UUID rootId) {
        List<KnowledgeResource> resources = resourceRepository.findByRootId(rootId).stream()
                .filter(resource -> !resource.isDeleted())
                .toList();
        return new ResourceTotals(
                resources.size(),
                resources.stream().mapToLong(KnowledgeResource::getSizeBytes).sum()
        );
    }

    private void completeScan(
            KnowledgeRoot root,
            KnowledgeRootScan scan,
            ResourceTotals totals,
            String errorMessage
    ) {
        Instant finishedAt = Instant.now();
        boolean success = errorMessage == null || errorMessage.isBlank();

        scan.setStatus(WorkStatus.DONE);
        scan.setSuccess(success);
        scan.setMessage(errorMessage);
        scan.setTotalResources(totals.resources());
        scan.setTotalSizeBytes(totals.bytes());
        scan.setFinishedAt(finishedAt);
        scanRepository.save(scan);

        // Reload before settling so a pause that landed mid-scan is preserved: we
        // only own the scan fields here, not the paused flag (which a concurrent
        // pause may have set on the persisted row).
        KnowledgeRoot current = rootRepository.findById(root.getId()).orElse(root);
        current.setTotalResources(totals.resources());
        current.setTotalSizeBytes(totals.bytes());
        current.setScanStatus(WorkStatus.DONE);
        current.setScanSuccess(success);
        current.setScanMessage(errorMessage);
        current.setScanFinishedAt(finishedAt);
        if (success) {
            current.setConfigJson(configWithScanMetadata(current));
        }
        rootRepository.save(current);
        // Announce the settled status so the settings screen re-derives scanned/error.
        publishSettingsUpdated();
    }

    private void publishSettingsUpdated() {
        eventService.publish("settings-updated");
    }

    private Instant configInstant(KnowledgeRoot root, String fieldName) {
        String configJson = Strings.defaultIfBlank(root.getConfigJson(), "").trim();
        if (configJson.isBlank()) {
            return null;
        }
        try {
            String value = objectMapper.readTree(configJson).path(fieldName).asText("");
            return value.isBlank() ? null : Instant.parse(value);
        } catch (IOException | DateTimeParseException exception) {
            return null;
        }
    }

    private String configWithScanMetadata(KnowledgeRoot root) {
        ObjectNode config = objectMapper.createObjectNode();
        String configJson = Strings.defaultIfBlank(root.getConfigJson(), "").trim();
        if (!configJson.isBlank()) {
            try {
                if (objectMapper.readTree(configJson) instanceof ObjectNode existingConfig) {
                    config = existingConfig;
                }
            } catch (IOException ignored) {
                config = objectMapper.createObjectNode();
            }
        }
        Instant startedAt = root.getScanStartedAt();
        if (startedAt != null) {
            config.put(CONFIG_LAST_SUCCESSFUL_SCAN_STARTED_AT, startedAt.toString());
        }
        config.remove(CONFIG_LEGACY_LAST_FULL_SCAN_FINISHED_AT);
        return config.toString();
    }

    private String jiraProjectReferenceId(JiraProjectDto project) {
        String projectId = Strings.defaultIfBlank(project.getId(), "").trim();
        return projectId.isBlank() ? project.getKey() : projectId;
    }

    private String jiraProjectDisplayName(JiraProjectDto project) {
        String key = Strings.defaultIfBlank(project.getKey(), "").trim();
        String name = Strings.defaultIfBlank(project.getName(), "").trim();
        if (key.isBlank()) {
            return name;
        }
        if (name.isBlank()) {
            return key;
        }
        return key + " - " + name;
    }

    private static class ScanStats {

        private long resources;
        private long bytes;

        private void add(List<JiraIssueFetchService.JiraIssueManifest> issues) {
            resources += issues.size();
            bytes += issues.stream().mapToLong(JiraIssueFetchService.JiraIssueManifest::sizeBytes).sum();
        }

        private long resources() {
            return resources;
        }

        private long bytes() {
            return bytes;
        }
    }

    public record ScanResult(long issues, long bytes) {
    }

    private record ResourceTotals(long resources, long bytes) {
    }

    public static class JiraScanException extends RuntimeException {

        public JiraScanException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private String scanErrorMessage(ResponseStatusException exception) {
        String reason = exception.getReason();
        return reason == null || reason.isBlank() ? "Could not scan Jira project" : reason;
    }
}
