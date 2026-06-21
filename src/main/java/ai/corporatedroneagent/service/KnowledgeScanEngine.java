package ai.corporatedroneagent.service;

import ai.corporatedroneagent.model.knowledge.KnowledgePipelineReason;
import ai.corporatedroneagent.model.knowledge.KnowledgeResource;
import ai.corporatedroneagent.model.knowledge.KnowledgeResourceChunk;
import ai.corporatedroneagent.model.knowledge.KnowledgeResourceConversion;
import ai.corporatedroneagent.model.knowledge.KnowledgeResourceRead;
import ai.corporatedroneagent.model.knowledge.KnowledgeRoot;
import ai.corporatedroneagent.model.knowledge.KnowledgeRootScan;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The generic, source-blind ingestion loop. Given a {@link KnowledgeRoot} and its
 * {@link KnowledgeSourceAdapter}, it drives the scan: mark in-progress, enumerate, skip
 * unchanged items, fetch + record read/conversion + chunk + index changed items,
 * reconcile deletions, advance the incremental cursor, and settle the root. Everything
 * a source differs in lives behind the adapter/session; everything here is shared.
 */
@Service
public class KnowledgeScanEngine {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeScanEngine.class);
    private static final String CONFIG_LAST_SUCCESSFUL_SCAN_STARTED_AT = "lastSuccessfulScanStartedAt";

    private final KnowledgeRootRepository rootRepository;
    private final KnowledgeRootScanRepository scanRepository;
    private final KnowledgeResourceRepository resourceRepository;
    private final KnowledgeResourcePipelineRepository pipelineRepository;
    private final KnowledgeChunkingService chunkingService;
    private final KnowledgeIndexingService indexingService;
    private final EventService eventService;
    private final ObjectMapper objectMapper;

    public KnowledgeScanEngine(
            KnowledgeRootRepository rootRepository,
            KnowledgeRootScanRepository scanRepository,
            KnowledgeResourceRepository resourceRepository,
            KnowledgeResourcePipelineRepository pipelineRepository,
            KnowledgeChunkingService chunkingService,
            KnowledgeIndexingService indexingService,
            EventService eventService,
            ObjectMapper objectMapper
    ) {
        this.rootRepository = rootRepository;
        this.scanRepository = scanRepository;
        this.resourceRepository = resourceRepository;
        this.pipelineRepository = pipelineRepository;
        this.chunkingService = chunkingService;
        this.indexingService = indexingService;
        this.eventService = eventService;
        this.objectMapper = objectMapper;
    }

    public ScanOutcome scan(
            KnowledgeRoot root,
            KnowledgeSourceAdapter adapter,
            BooleanSupplier isCancelled,
            Consumer<String> onProgress
    ) {
        Instant startedAt = Instant.now();
        ScanCursor cursor = readCursor(root);
        UUID rootId = startRootScan(root, startedAt).getId();
        KnowledgeRootScan scan = startScan(rootId, startedAt);
        // Announce the flip to IN_PROGRESS so the settings screen derives "scanning".
        publishSettingsUpdated();

        try (KnowledgeScanSession session = adapter.openSession(root)) {
            List<ResourceManifest> manifests = session.enumerate(cursor);
            Map<String, KnowledgeResource> existing = byReference(resourceRepository.findByRootId(rootId));
            Set<UUID> reusable = pipelineRepository.findReusablePipelineResourceIdsByRootId(rootId);

            List<String> seenReferences = new ArrayList<>();
            boolean cancelled = false;
            for (ResourceManifest manifest : manifests) {
                if (isCancelled.getAsBoolean()) {
                    cancelled = true;
                    break;
                }
                seenReferences.add(manifest.reference());
                onProgress.accept(manifest.displayName());

                KnowledgeResource current = existing.get(manifest.reference());
                if (current != null
                        && !current.isDeleted()
                        && reusable.contains(current.getId())
                        && session.isUnchanged(current, manifest)) {
                    log.debug("Skipping unchanged knowledge resource {}.", manifest.reference());
                    continue;
                }
                ingestOne(rootId, session, manifest, current, startedAt);
            }

            if (!cancelled && session.reconcilesDeletes()) {
                removeDeletedResourceIndexes(seenReferences, existing.values());
            }
            ResourceTotals totals = activeResourceTotals(rootId);
            completeScan(rootId, scan, totals, null, cancelled, startedAt);
            return new ScanOutcome(totals.resources(), totals.bytes(), cancelled);
        } catch (org.springframework.web.server.ResponseStatusException exception) {
            completeScan(rootId, scan, activeResourceTotals(rootId), scanErrorMessage(exception), false, startedAt);
            throw exception;
        } catch (RuntimeException exception) {
            completeScan(rootId, scan, activeResourceTotals(rootId), "Could not scan knowledge source", false, startedAt);
            throw new KnowledgeScanException("Could not scan knowledge source", exception);
        }
    }

    private void ingestOne(
            UUID rootId,
            KnowledgeScanSession session,
            ResourceManifest manifest,
            KnowledgeResource existing,
            Instant scannedAt
    ) {
        // Read the bytes + authoritative metadata first (a root-level failure throws and
        // aborts the scan); build/persist the resource and record the read stage.
        ReadResult read = session.read(manifest);

        KnowledgeResource resource = new KnowledgeResource();
        resource.setRootId(rootId);
        if (existing != null) {
            resource.setId(existing.getId());
        }
        resource.setReference(read.reference());
        resource.setDisplayName(read.displayName());
        resource.setFormat(read.format());
        resource.setSizeBytes(read.sizeBytes());
        resource.setLastModifiedAt(read.lastModifiedAt());
        resource.setDeleted(false);
        resource.setScannedAt(scannedAt);
        KnowledgeResource saved = resourceRepository.save(resource);
        indexingService.deleteResource(saved);
        recordRead(saved.getId(), read, scannedAt);

        // Convert separately so a failed conversion still leaves the read persisted and the
        // scan continues with this resource marked failed, rather than aborting.
        ConversionResult conversion;
        if (!read.success()) {
            conversion = ConversionResult.failed(KnowledgePipelineReason.READ_DID_NOT_SUCCEED, "Read did not succeed");
        } else {
            try {
                conversion = session.convert(read);
            } catch (RuntimeException exception) {
                log.warn("Could not convert knowledge resource {}.", read.reference(), exception);
                conversion = ConversionResult.failed(KnowledgePipelineReason.CONVERSION_FAILED, "Could not convert resource");
            }
        }
        KnowledgeResourceConversion conversionEntity = recordConversion(saved.getId(), conversion, scannedAt);
        List<KnowledgeResourceChunk> chunks = chunkingService.chunk(saved, conversionEntity);
        indexingService.index(saved, conversionEntity, chunks);
    }

    private void recordRead(UUID resourceId, ReadResult read, Instant scannedAt) {
        KnowledgeResourceRead entity = pipelineRepository.findReadByResourceId(resourceId)
                .orElseGet(KnowledgeResourceRead::new);
        entity.setResourceId(resourceId);
        entity.setStatus(WorkStatus.DONE);
        entity.setSuccess(read.success());
        entity.setReason(read.reason());
        entity.setMessage(read.message());
        entity.setValue(read.value());
        entity.setReadAt(scannedAt);
        pipelineRepository.saveRead(entity);
    }

    private KnowledgeResourceConversion recordConversion(UUID resourceId, ConversionResult conversion, Instant scannedAt) {
        KnowledgeResourceConversion entity = pipelineRepository.findConversionByResourceId(resourceId)
                .orElseGet(KnowledgeResourceConversion::new);
        entity.setResourceId(resourceId);
        entity.setStatus(WorkStatus.DONE);
        entity.setSuccess(conversion.success());
        entity.setReason(conversion.reason());
        entity.setMessage(conversion.message());
        entity.setValue(conversion.text());
        entity.setConvertedAt(scannedAt);
        return pipelineRepository.saveConversion(entity);
    }

    private void removeDeletedResourceIndexes(List<String> currentReferences, Collection<KnowledgeResource> existing) {
        Set<String> active = new HashSet<>(currentReferences);
        List<KnowledgeResource> stale = existing.stream()
                .filter(resource -> !resource.isDeleted())
                .filter(resource -> !active.contains(resource.getReference()))
                .toList();
        stale.forEach(resource -> {
            log.debug("Removing stale knowledge resource {}.", resource.getReference());
            indexingService.deleteResource(resource);
            chunkingService.deleteChunks(resource);
        });
        resourceRepository.markDeletedResourcesByIds(stale.stream().map(KnowledgeResource::getId).toList());
    }

    private Map<String, KnowledgeResource> byReference(List<KnowledgeResource> resources) {
        return resources.stream().collect(Collectors.toMap(
                KnowledgeResource::getReference,
                Function.identity(),
                (first, second) -> first
        ));
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

    private void completeScan(
            UUID rootId,
            KnowledgeRootScan scan,
            ResourceTotals totals,
            String errorMessage,
            boolean cancelled,
            Instant startedAt
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

        // Reload so a pause that landed mid-scan is preserved: the engine owns the scan
        // fields, not the paused flag.
        KnowledgeRoot current = rootRepository.findById(rootId).orElse(null);
        if (current == null) {
            return;
        }
        current.setTotalResources(totals.resources());
        current.setTotalSizeBytes(totals.bytes());
        current.setScanStatus(WorkStatus.DONE);
        current.setScanSuccess(success);
        current.setScanMessage(errorMessage);
        current.setScanFinishedAt(finishedAt);
        if (success && !cancelled) {
            // A cancelled scan only covered part of the source, so it must not advance the
            // incremental cursor — the next scan re-covers what it skipped.
            current.setConfigJson(configWithCursor(current, startedAt));
        }
        rootRepository.save(current);
        // Announce the settled status so the settings screen re-derives scanned/error.
        publishSettingsUpdated();
    }

    private void publishSettingsUpdated() {
        eventService.publish("settings-updated");
    }

    private ScanCursor readCursor(KnowledgeRoot root) {
        if (root.getId() == null) {
            return ScanCursor.none();
        }
        Instant configured = configInstant(root, CONFIG_LAST_SUCCESSFUL_SCAN_STARTED_AT);
        if (configured != null) {
            return new ScanCursor(configured);
        }
        // Backward compatibility for roots scanned before the cursor was stored in config.
        if (root.getScanStatus() != WorkStatus.DONE
                || !Boolean.TRUE.equals(root.getScanSuccess())
                || (root.getScanStartedAt() == null && root.getScanFinishedAt() == null)) {
            return ScanCursor.none();
        }
        return new ScanCursor(root.getScanStartedAt() == null ? root.getScanFinishedAt() : root.getScanStartedAt());
    }

    private Instant configInstant(KnowledgeRoot root, String field) {
        String configJson = Strings.defaultIfBlank(root.getConfigJson(), "").trim();
        if (configJson.isBlank()) {
            return null;
        }
        try {
            String value = objectMapper.readTree(configJson).path(field).asText("");
            return value.isBlank() ? null : Instant.parse(value);
        } catch (IOException | DateTimeParseException exception) {
            return null;
        }
    }

    private String configWithCursor(KnowledgeRoot root, Instant startedAt) {
        ObjectNode config = objectMapper.createObjectNode();
        String configJson = Strings.defaultIfBlank(root.getConfigJson(), "").trim();
        if (!configJson.isBlank()) {
            try {
                if (objectMapper.readTree(configJson) instanceof ObjectNode existing) {
                    config = existing;
                }
            } catch (IOException ignored) {
                config = objectMapper.createObjectNode();
            }
        }
        if (startedAt != null) {
            config.put(CONFIG_LAST_SUCCESSFUL_SCAN_STARTED_AT, startedAt.toString());
        }
        return config.toString();
    }

    private String scanErrorMessage(org.springframework.web.server.ResponseStatusException exception) {
        String reason = exception.getReason();
        return reason == null || reason.isBlank() ? "Could not scan knowledge source" : reason;
    }

    public record ScanOutcome(long resources, long bytes, boolean cancelled) {
    }

    private record ResourceTotals(long resources, long bytes) {
    }

    public static class KnowledgeScanException extends RuntimeException {
        public KnowledgeScanException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
