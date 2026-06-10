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
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class JiraKnowledgeScanService {

    private static final Logger log = LoggerFactory.getLogger(JiraKnowledgeScanService.class);

    private final KnowledgeRootRepository rootRepository;
    private final KnowledgeRootScanRepository scanRepository;
    private final KnowledgeResourceRepository resourceRepository;
    private final KnowledgeResourcePipelineRepository pipelineRepository;
    private final KnowledgeChunkingService chunkingService;
    private final KnowledgeIndexingService indexingService;
    private final JiraIssueFetchService issueFetchService;

    public JiraKnowledgeScanService(
            KnowledgeRootRepository rootRepository,
            KnowledgeRootScanRepository scanRepository,
            KnowledgeResourceRepository resourceRepository,
            KnowledgeResourcePipelineRepository pipelineRepository,
            KnowledgeChunkingService chunkingService,
            KnowledgeIndexingService indexingService,
            JiraIssueFetchService issueFetchService
    ) {
        this.rootRepository = rootRepository;
        this.scanRepository = scanRepository;
        this.resourceRepository = resourceRepository;
        this.pipelineRepository = pipelineRepository;
        this.chunkingService = chunkingService;
        this.indexingService = indexingService;
        this.issueFetchService = issueFetchService;
    }

    public ScanResult scanProject(JiraSettings jira, JiraProjectDto project, String token) {
        Instant startedAt = Instant.now();
        KnowledgeRoot root = startRootScan(knowledgeRoot(jira, project), startedAt);
        KnowledgeRootScan scan = startScan(root.getId(), startedAt);
        ScanStats stats = new ScanStats();
        log.info("Started Jira project scan for {}.", project.getKey());

        try {
            List<JiraIssueFetchService.JiraIssueDocument> issues = issueFetchService.fetchProjectIssues(
                    jira.getInstanceUrl(),
                    jira.getEmail(),
                    token,
                    project
            );
            stats.add(issues);
            processIssues(root.getId(), issues, startedAt);
            removeDeletedResourceIndexes(
                    issues.stream().map(JiraIssueFetchService.JiraIssueDocument::reference).toList(),
                    resourceRepository.findByRootId(root.getId())
            );
            completeScan(root, scan, stats, null);
            log.info(
                    "Completed Jira project scan for {} with {} issues and {} bytes.",
                    project.getKey(),
                    stats.resources(),
                    stats.bytes()
            );
            return new ScanResult(stats.resources(), stats.bytes());
        } catch (RuntimeException exception) {
            completeScan(root, scan, stats, "Could not scan Jira project");
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

    private void processIssues(
            UUID rootId,
            List<JiraIssueFetchService.JiraIssueDocument> issues,
            Instant scannedAt
    ) {
        Map<String, KnowledgeResource> existingResources = resourceRepository.findByRootId(rootId).stream()
                .collect(Collectors.toMap(
                        KnowledgeResource::getReference,
                        resource -> resource,
                        (first, second) -> first
                ));
        Set<UUID> reusablePipelineResourceIds = pipelineRepository.findReusablePipelineResourceIdsByRootId(rootId);
        for (JiraIssueFetchService.JiraIssueDocument issue : issues) {
            saveResource(issue, rootId, existingResources.get(issue.reference()), reusablePipelineResourceIds, scannedAt);
        }
    }

    private void saveResource(
            JiraIssueFetchService.JiraIssueDocument issue,
            UUID rootId,
            KnowledgeResource existingResource,
            Set<UUID> reusablePipelineResourceIds,
            Instant scannedAt
    ) {
        if (existingResource != null && canReusePipeline(existingResource, issue, reusablePipelineResourceIds)) {
            log.debug("Skipping unchanged Jira knowledge resource {}.", issue.reference());
            return;
        }

        KnowledgeResource resource = new KnowledgeResource();
        resource.setRootId(rootId);
        if (existingResource != null) {
            resource.setId(existingResource.getId());
        }
        resource.setReference(issue.reference());
        resource.setDisplayName(issue.displayName());
        resource.setFormat(issue.format());
        resource.setSizeBytes(issue.sizeBytes());
        resource.setLastModifiedAt(issue.lastModifiedAt());
        resource.setDeleted(false);
        resource.setScannedAt(scannedAt);
        KnowledgeResource savedResource = resourceRepository.save(resource);
        indexingService.deleteResource(savedResource);

        KnowledgeResourceRead read = successfulRead(savedResource.getId(), issue.text(), scannedAt);
        KnowledgeResourceConversion conversion = successfulConversion(savedResource.getId(), issue.text(), scannedAt);
        pipelineRepository.saveRead(read);
        KnowledgeResourceConversion savedConversion = pipelineRepository.saveConversion(conversion);
        List<KnowledgeResourceChunk> chunks = chunkingService.chunk(savedResource, savedConversion);
        indexingService.index(savedResource, savedConversion, chunks);
    }

    private boolean canReusePipeline(
            KnowledgeResource resource,
            JiraIssueFetchService.JiraIssueDocument issue,
            Set<UUID> reusablePipelineResourceIds
    ) {
        return !resource.isDeleted()
                && resource.getSizeBytes() == issue.sizeBytes()
                && sameTimestamp(resource.getLastModifiedAt(), issue.lastModifiedAt())
                && reusablePipelineResourceIds.contains(resource.getId());
    }

    private boolean sameTimestamp(Instant first, Instant second) {
        if (first == null || second == null) {
            return first == second;
        }
        return first.toEpochMilli() == second.toEpochMilli();
    }

    private KnowledgeResourceRead successfulRead(UUID resourceId, String text, Instant scannedAt) {
        KnowledgeResourceRead read = new KnowledgeResourceRead();
        read.setResourceId(resourceId);
        read.setStatus(WorkStatus.DONE);
        read.setSuccess(true);
        read.setReason(null);
        read.setMessage("");
        read.setValue(text.getBytes(StandardCharsets.UTF_8));
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

    private void removeDeletedResourceIndexes(
            List<String> currentReferences,
            Collection<KnowledgeResource> existingResources
    ) {
        Set<String> activeReferences = new HashSet<>(currentReferences);
        List<KnowledgeResource> staleResources = existingResources.stream()
                .filter(resource -> !resource.isDeleted())
                .filter(resource -> !activeReferences.contains(resource.getReference()))
                .toList();
        staleResources.forEach(resource -> {
            log.debug("Removing stale Jira knowledge resource {}.", resource.getReference());
            indexingService.deleteResource(resource);
            chunkingService.deleteChunks(resource);
        });
        resourceRepository.markDeletedResourcesByIds(staleResources.stream()
                .map(KnowledgeResource::getId)
                .toList());
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
        root.setPaused("paused".equals(project.getStatus()));
        return root;
    }

    private KnowledgeRoot startRootScan(KnowledgeRoot root, Instant startedAt) {
        root.setScanStatus(WorkStatus.IN_PROGRESS);
        root.setScanSuccess(null);
        root.setScanMessage("");
        root.setScanStartedAt(startedAt);
        root.setScanFinishedAt(null);
        return rootRepository.save(root);
    }

    private KnowledgeRootScan startScan(UUID rootId, Instant startedAt) {
        KnowledgeRootScan scan = new KnowledgeRootScan();
        scan.setRootId(rootId);
        scan.setStatus(WorkStatus.IN_PROGRESS);
        scan.setStartedAt(startedAt);
        return scanRepository.save(scan);
    }

    private void completeScan(KnowledgeRoot root, KnowledgeRootScan scan, ScanStats stats, String errorMessage) {
        Instant finishedAt = Instant.now();
        boolean success = errorMessage == null || errorMessage.isBlank();

        scan.setStatus(WorkStatus.DONE);
        scan.setSuccess(success);
        scan.setMessage(errorMessage);
        scan.setTotalResources(stats.resources());
        scan.setTotalSizeBytes(stats.bytes());
        scan.setFinishedAt(finishedAt);
        scanRepository.save(scan);

        root.setTotalResources(stats.resources());
        root.setTotalSizeBytes(stats.bytes());
        root.setScanStatus(WorkStatus.DONE);
        root.setScanSuccess(success);
        root.setScanMessage(errorMessage);
        root.setScanFinishedAt(finishedAt);
        rootRepository.save(root);
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

        private void add(List<JiraIssueFetchService.JiraIssueDocument> issues) {
            resources += issues.size();
            bytes += issues.stream().mapToLong(JiraIssueFetchService.JiraIssueDocument::sizeBytes).sum();
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

    public static class JiraScanException extends RuntimeException {

        public JiraScanException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
