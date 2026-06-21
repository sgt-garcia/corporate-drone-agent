package ai.corporatedroneagent.repository;

import static ai.corporatedroneagent.repository.KnowledgeRepositorySupport.instant;
import static ai.corporatedroneagent.repository.KnowledgeRepositorySupport.nullableBoolean;
import static ai.corporatedroneagent.repository.KnowledgeRepositorySupport.queryForOptional;
import static ai.corporatedroneagent.repository.KnowledgeRepositorySupport.timestamp;

import ai.corporatedroneagent.model.knowledge.KnowledgeRootScan;
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
public class KnowledgeRootScanRepository {

    private final JdbcTemplate jdbcTemplate;

    public KnowledgeRootScanRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<KnowledgeRootScan> findById(UUID id) {
        return queryForOptional(jdbcTemplate, "SELECT * FROM knowledge_root_scans WHERE id = ?", this::mapScan, id);
    }

    public Optional<KnowledgeRootScan> findLatestByRootId(UUID rootId) {
        List<KnowledgeRootScan> scans = jdbcTemplate.query("""
                SELECT *
                FROM knowledge_root_scans
                WHERE root_id = ?
                ORDER BY created_at DESC
                LIMIT 1
                """, this::mapScan, rootId);
        return scans.stream().findFirst();
    }

    public KnowledgeRootScan save(KnowledgeRootScan scan) {
        Instant now = Instant.now();
        if (scan.getId() == null || findById(scan.getId()).isEmpty()) {
            return insert(scan, now);
        }
        return update(scan, now);
    }

    private KnowledgeRootScan insert(KnowledgeRootScan scan, Instant now) {
        if (scan.getId() == null) {
            scan.setId(UUID.randomUUID());
        }
        scan.setCreatedAt(now);
        scan.setUpdatedAt(now);
        jdbcTemplate.update("""
                INSERT INTO knowledge_root_scans (
                    id, root_id, status, success, message, total_resources, total_size_bytes,
                    started_at, finished_at, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                scan.getId(),
                scan.getRootId(),
                scan.getStatus().name(),
                scan.getSuccess(),
                scan.getMessage(),
                scan.getTotalResources(),
                scan.getTotalSizeBytes(),
                timestamp(scan.getStartedAt()),
                timestamp(scan.getFinishedAt()),
                timestamp(scan.getCreatedAt()),
                timestamp(scan.getUpdatedAt())
        );
        return scan;
    }

    private KnowledgeRootScan update(KnowledgeRootScan scan, Instant now) {
        scan.setUpdatedAt(now);
        jdbcTemplate.update("""
                UPDATE knowledge_root_scans
                SET status = ?,
                    success = ?,
                    message = ?,
                    total_resources = ?,
                    total_size_bytes = ?,
                    started_at = ?,
                    finished_at = ?,
                    updated_at = ?
                WHERE id = ?
                """,
                scan.getStatus().name(),
                scan.getSuccess(),
                scan.getMessage(),
                scan.getTotalResources(),
                scan.getTotalSizeBytes(),
                timestamp(scan.getStartedAt()),
                timestamp(scan.getFinishedAt()),
                timestamp(scan.getUpdatedAt()),
                scan.getId()
        );
        return scan;
    }

    private KnowledgeRootScan mapScan(ResultSet resultSet, int rowNumber) throws SQLException {
        KnowledgeRootScan scan = new KnowledgeRootScan();
        scan.setId(resultSet.getObject("id", UUID.class));
        scan.setRootId(resultSet.getObject("root_id", UUID.class));
        scan.setStatus(WorkStatus.valueOf(resultSet.getString("status")));
        scan.setSuccess(nullableBoolean(resultSet, "success"));
        scan.setMessage(resultSet.getString("message"));
        scan.setTotalResources(resultSet.getLong("total_resources"));
        scan.setTotalSizeBytes(resultSet.getLong("total_size_bytes"));
        scan.setStartedAt(instant(resultSet, "started_at"));
        scan.setFinishedAt(instant(resultSet, "finished_at"));
        scan.setCreatedAt(instant(resultSet, "created_at"));
        scan.setUpdatedAt(instant(resultSet, "updated_at"));
        return scan;
    }
}
