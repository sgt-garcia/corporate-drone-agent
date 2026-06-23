package ai.corporatedroneagent.repository;

import static ai.corporatedroneagent.repository.KnowledgeRepositorySupport.instant;
import static ai.corporatedroneagent.repository.KnowledgeRepositorySupport.queryForOptional;
import static ai.corporatedroneagent.repository.KnowledgeRepositorySupport.timestamp;

import ai.corporatedroneagent.model.knowledge.KnowledgeResource;
import ai.corporatedroneagent.model.knowledge.KnowledgeSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class KnowledgeResourceRepository {

    private final JdbcTemplate jdbcTemplate;

    public KnowledgeResourceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<KnowledgeResource> findById(UUID id) {
        return queryForOptional(jdbcTemplate, "SELECT * FROM knowledge_resources WHERE id = ?", this::mapResource, id);
    }

    public Optional<KnowledgeResource> findByRootIdAndReference(UUID rootId, String reference) {
        return queryForOptional(
                jdbcTemplate,
                "SELECT * FROM knowledge_resources WHERE root_id = ? AND resource_reference = ?",
                this::mapResource,
                rootId,
                reference
        );
    }

    public List<KnowledgeResource> findByRootId(UUID rootId) {
        return jdbcTemplate.query("""
                SELECT *
                FROM knowledge_resources
                WHERE root_id = ?
                ORDER BY display_name, resource_reference
                """, this::mapResource, rootId);
    }

    public List<KnowledgeResource> findActiveBySourceAndDisplayNamePrefix(
            KnowledgeSource source,
            String displayNamePrefix,
            int limit
    ) {
        if (source == null || displayNamePrefix == null || displayNamePrefix.isBlank() || limit <= 0) {
            return List.of();
        }
        String normalizedPrefix = displayNamePrefix.trim().toUpperCase(java.util.Locale.ROOT);
        return jdbcTemplate.query("""
                SELECT resource.*
                FROM knowledge_resources resource
                JOIN knowledge_roots root
                  ON root.id = resource.root_id
                WHERE root.source_type = ?
                  AND resource.deleted = FALSE
                  AND (
                    UPPER(resource.display_name) = ?
                    OR UPPER(resource.display_name) LIKE ? ESCAPE '\\'
                  )
                ORDER BY resource.display_name, resource.resource_reference
                LIMIT ?
                """,
                this::mapResource,
                source.name(),
                normalizedPrefix,
                escapeLike(normalizedPrefix) + " - %",
                limit
        );
    }

    public List<KnowledgeResource> findActiveByReferenceOrName(String identifier, int limit) {
        if (identifier == null || identifier.isBlank() || limit <= 0) {
            return List.of();
        }
        // The model passes back whatever a search result showed: a file path or Jira key (the
        // reference) for local folders and Jira, or a title (the display name) for Confluence. Match
        // either, plus the "KEY - title" form so a bare Jira key still resolves.
        String normalized = identifier.trim().toUpperCase(java.util.Locale.ROOT);
        return jdbcTemplate.query("""
                SELECT resource.*
                FROM knowledge_resources resource
                WHERE resource.deleted = FALSE
                  AND (
                    UPPER(resource.resource_reference) = ?
                    OR UPPER(resource.display_name) = ?
                    OR UPPER(resource.display_name) LIKE ? ESCAPE '\\'
                  )
                ORDER BY resource.display_name, resource.resource_reference
                LIMIT ?
                """,
                this::mapResource,
                normalized,
                normalized,
                escapeLike(normalized) + " - %",
                limit
        );
    }

    public KnowledgeResource save(KnowledgeResource resource) {
        Instant now = Instant.now();
        Optional<KnowledgeResource> existing = resource.getId() == null
                ? findByRootIdAndReference(resource.getRootId(), resource.getReference())
                : findById(resource.getId());
        if (existing.isEmpty()) {
            return insert(resource, now);
        }
        resource.setId(existing.get().getId());
        resource.setCreatedAt(existing.get().getCreatedAt());
        return update(resource, now);
    }

    public int markDeletedResourcesByIds(Collection<UUID> resourceIds) {
        if (resourceIds == null || resourceIds.isEmpty()) {
            return 0;
        }

        String placeholders = String.join(",", java.util.Collections.nCopies(resourceIds.size(), "?"));
        List<Object> parameters = new ArrayList<>();
        parameters.add(timestamp(Instant.now()));
        parameters.addAll(resourceIds);
        return jdbcTemplate.update("""
                UPDATE knowledge_resources
                SET deleted = TRUE,
                    updated_at = ?
                WHERE id IN (
                """ + placeholders + ")",
                parameters.toArray()
        );
    }

    private KnowledgeResource insert(KnowledgeResource resource, Instant now) {
        if (resource.getId() == null) {
            resource.setId(UUID.randomUUID());
        }
        resource.setCreatedAt(now);
        resource.setUpdatedAt(now);
        jdbcTemplate.update("""
                INSERT INTO knowledge_resources (
                    id, root_id, resource_reference, display_name, format, size_bytes,
                    last_modified_at, deleted, scanned_at, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                resource.getId(),
                resource.getRootId(),
                resource.getReference(),
                resource.getDisplayName(),
                resource.getFormat(),
                resource.getSizeBytes(),
                timestamp(resource.getLastModifiedAt()),
                resource.isDeleted(),
                timestamp(resource.getScannedAt()),
                timestamp(resource.getCreatedAt()),
                timestamp(resource.getUpdatedAt())
        );
        return resource;
    }

    private KnowledgeResource update(KnowledgeResource resource, Instant now) {
        resource.setUpdatedAt(now);
        jdbcTemplate.update("""
                UPDATE knowledge_resources
                SET resource_reference = ?,
                    display_name = ?,
                    format = ?,
                    size_bytes = ?,
                    last_modified_at = ?,
                    deleted = ?,
                    scanned_at = ?,
                    updated_at = ?
                WHERE id = ?
                """,
                resource.getReference(),
                resource.getDisplayName(),
                resource.getFormat(),
                resource.getSizeBytes(),
                timestamp(resource.getLastModifiedAt()),
                resource.isDeleted(),
                timestamp(resource.getScannedAt()),
                timestamp(resource.getUpdatedAt()),
                resource.getId()
        );
        return resource;
    }

    private KnowledgeResource mapResource(ResultSet resultSet, int rowNumber) throws SQLException {
        KnowledgeResource resource = new KnowledgeResource();
        resource.setId(resultSet.getObject("id", UUID.class));
        resource.setRootId(resultSet.getObject("root_id", UUID.class));
        resource.setReference(resultSet.getString("resource_reference"));
        resource.setDisplayName(resultSet.getString("display_name"));
        resource.setFormat(resultSet.getString("format"));
        resource.setSizeBytes(resultSet.getLong("size_bytes"));
        resource.setLastModifiedAt(instant(resultSet, "last_modified_at"));
        resource.setDeleted(resultSet.getBoolean("deleted"));
        resource.setScannedAt(instant(resultSet, "scanned_at"));
        resource.setCreatedAt(instant(resultSet, "created_at"));
        resource.setUpdatedAt(instant(resultSet, "updated_at"));
        return resource;
    }

    private String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
