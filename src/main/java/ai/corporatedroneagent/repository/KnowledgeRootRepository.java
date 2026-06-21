package ai.corporatedroneagent.repository;

import static ai.corporatedroneagent.repository.KnowledgeRepositorySupport.instant;
import static ai.corporatedroneagent.repository.KnowledgeRepositorySupport.nullableBoolean;
import static ai.corporatedroneagent.repository.KnowledgeRepositorySupport.queryForOptional;
import static ai.corporatedroneagent.repository.KnowledgeRepositorySupport.timestamp;

import ai.corporatedroneagent.model.knowledge.KnowledgeRoot;
import ai.corporatedroneagent.model.knowledge.KnowledgeSource;
import ai.corporatedroneagent.model.knowledge.WorkStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class KnowledgeRootRepository {

    private final JdbcTemplate jdbcTemplate;

    public KnowledgeRootRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<KnowledgeRoot> findAll() {
        return jdbcTemplate.query("""
                SELECT *
                FROM knowledge_roots
                ORDER BY created_at, display_name
                """, this::mapRoot);
    }

    public Optional<KnowledgeRoot> findById(UUID id) {
        return queryForOptional(jdbcTemplate, "SELECT * FROM knowledge_roots WHERE id = ?", this::mapRoot, id);
    }

    public Optional<KnowledgeRoot> findByIdAndSource(UUID id, KnowledgeSource source) {
        return queryForOptional(
                jdbcTemplate,
                "SELECT * FROM knowledge_roots WHERE id = ? AND source_type = ?",
                this::mapRoot,
                id,
                source.name()
        );
    }

    public List<KnowledgeRoot> findBySource(KnowledgeSource source) {
        return jdbcTemplate.query("""
                SELECT *
                FROM knowledge_roots
                WHERE source_type = ?
                ORDER BY created_at, display_name
                """,
                this::mapRoot,
                source.name()
        );
    }

    public Optional<KnowledgeRoot> findBySourceAndReference(KnowledgeSource source, String reference) {
        return queryForOptional(
                jdbcTemplate,
                "SELECT * FROM knowledge_roots WHERE source_type = ? AND root_reference = ?",
                this::mapRoot,
                source.name(),
                reference
        );
    }

    public KnowledgeRoot save(KnowledgeRoot root) {
        Instant now = Instant.now();
        if (root.getId() == null || findById(root.getId()).isEmpty()) {
            return insert(root, now);
        }
        return update(root, now);
    }

    public void delete(UUID id) {
        jdbcTemplate.update("DELETE FROM knowledge_roots WHERE id = ?", id);
    }

    private KnowledgeRoot insert(KnowledgeRoot root, Instant now) {
        if (root.getId() == null) {
            root.setId(UUID.randomUUID());
        }
        root.setCreatedAt(now);
        root.setUpdatedAt(now);
        jdbcTemplate.update("""
                INSERT INTO knowledge_roots (
                    id, source_type, root_reference, display_name, paused, config_json,
                    total_resources, total_size_bytes, scan_status, scan_success, scan_message,
                    scan_started_at, scan_finished_at, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                root.getId(),
                root.getSource().name(),
                root.getReference(),
                root.getDisplayName(),
                root.isPaused(),
                root.getConfigJson(),
                root.getTotalResources(),
                root.getTotalSizeBytes(),
                root.getScanStatus().name(),
                root.getScanSuccess(),
                root.getScanMessage(),
                timestamp(root.getScanStartedAt()),
                timestamp(root.getScanFinishedAt()),
                timestamp(root.getCreatedAt()),
                timestamp(root.getUpdatedAt())
        );
        return root;
    }

    private KnowledgeRoot update(KnowledgeRoot root, Instant now) {
        root.setUpdatedAt(now);
        jdbcTemplate.update("""
                UPDATE knowledge_roots
                SET source_type = ?,
                    root_reference = ?,
                    display_name = ?,
                    paused = ?,
                    config_json = ?,
                    total_resources = ?,
                    total_size_bytes = ?,
                    scan_status = ?,
                    scan_success = ?,
                    scan_message = ?,
                    scan_started_at = ?,
                    scan_finished_at = ?,
                    updated_at = ?
                WHERE id = ?
                """,
                root.getSource().name(),
                root.getReference(),
                root.getDisplayName(),
                root.isPaused(),
                root.getConfigJson(),
                root.getTotalResources(),
                root.getTotalSizeBytes(),
                root.getScanStatus().name(),
                root.getScanSuccess(),
                root.getScanMessage(),
                timestamp(root.getScanStartedAt()),
                timestamp(root.getScanFinishedAt()),
                timestamp(root.getUpdatedAt()),
                root.getId()
        );
        return root;
    }

    private KnowledgeRoot mapRoot(ResultSet resultSet, int rowNumber) throws SQLException {
        KnowledgeRoot root = new KnowledgeRoot();
        root.setId(resultSet.getObject("id", UUID.class));
        root.setSource(KnowledgeSource.valueOf(resultSet.getString("source_type")));
        root.setReference(resultSet.getString("root_reference"));
        root.setDisplayName(resultSet.getString("display_name"));
        root.setPaused(resultSet.getBoolean("paused"));
        root.setConfigJson(resultSet.getString("config_json"));
        root.setTotalResources(resultSet.getLong("total_resources"));
        root.setTotalSizeBytes(resultSet.getLong("total_size_bytes"));
        root.setScanStatus(WorkStatus.valueOf(resultSet.getString("scan_status")));
        root.setScanSuccess(nullableBoolean(resultSet, "scan_success"));
        root.setScanMessage(resultSet.getString("scan_message"));
        root.setScanStartedAt(instant(resultSet, "scan_started_at"));
        root.setScanFinishedAt(instant(resultSet, "scan_finished_at"));
        root.setCreatedAt(instant(resultSet, "created_at"));
        root.setUpdatedAt(instant(resultSet, "updated_at"));
        return root;
    }

}
