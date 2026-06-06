package ai.corporatedroneagent.repository;

import static ai.corporatedroneagent.repository.KnowledgeRepositorySupport.instant;
import static ai.corporatedroneagent.repository.KnowledgeRepositorySupport.nullableBoolean;
import static ai.corporatedroneagent.repository.KnowledgeRepositorySupport.timestamp;

import ai.corporatedroneagent.model.knowledge.KnowledgeResourceChunk;
import ai.corporatedroneagent.model.knowledge.KnowledgeResourceConversion;
import ai.corporatedroneagent.model.knowledge.KnowledgeResourceIndex;
import ai.corporatedroneagent.model.knowledge.KnowledgeResourceRead;
import ai.corporatedroneagent.model.knowledge.WorkStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class KnowledgeResourcePipelineRepository {

    private final JdbcTemplate jdbcTemplate;

    public KnowledgeResourcePipelineRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<KnowledgeResourceRead> findReadByResourceId(UUID resourceId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT * FROM knowledge_resource_reads WHERE resource_id = ?",
                    this::mapRead,
                    resourceId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    public Set<UUID> findReusablePipelineResourceIdsByRootId(UUID rootId) {
        if (rootId == null) {
            return Set.of();
        }

        Set<UUID> reusableResourceIds = new HashSet<>();

        reusableResourceIds.addAll(jdbcTemplate.queryForList("""
                SELECT read_state.resource_id
                FROM knowledge_resources resource
                JOIN knowledge_resource_reads read_state
                  ON read_state.resource_id = resource.id
                WHERE resource.root_id = ?
                  AND read_state.status = 'DONE'
                  AND read_state.success = FALSE
                  AND read_state.message IN ('Unsupported file format', 'File is larger than 1 MB')
                """, UUID.class, rootId));

        reusableResourceIds.addAll(jdbcTemplate.queryForList("""
                SELECT conversion.resource_id
                FROM knowledge_resources resource
                JOIN knowledge_resource_conversions conversion
                  ON conversion.resource_id = resource.id
                WHERE resource.root_id = ?
                  AND conversion.status = 'DONE'
                  AND conversion.success = FALSE
                  AND conversion.message = 'Could not decode resource as UTF-8'
                """, UUID.class, rootId));

        reusableResourceIds.addAll(jdbcTemplate.queryForList("""
                SELECT conversion.resource_id
                FROM knowledge_resources resource
                JOIN knowledge_resource_conversions conversion
                  ON conversion.resource_id = resource.id
                WHERE resource.root_id = ?
                  AND conversion.status = 'DONE'
                  AND conversion.success = TRUE
                  AND (conversion.conversion_value IS NULL OR CHAR_LENGTH(conversion.conversion_value) = 0)
                """, UUID.class, rootId));

        reusableResourceIds.addAll(jdbcTemplate.queryForList("""
                SELECT conversion.resource_id
                FROM knowledge_resources resource
                JOIN knowledge_resource_conversions conversion
                  ON conversion.resource_id = resource.id
                WHERE resource.root_id = ?
                  AND conversion.status = 'DONE'
                  AND conversion.success = TRUE
                  AND CHAR_LENGTH(conversion.conversion_value) > 0
                  AND EXISTS (
                      SELECT 1
                      FROM knowledge_resource_chunks chunk
                      WHERE chunk.resource_id = conversion.resource_id
                  )
                  AND NOT EXISTS (
                      SELECT 1
                      FROM knowledge_resource_chunks chunk
                      LEFT JOIN knowledge_resource_indexes index_state
                        ON index_state.chunk_id = chunk.id
                       AND index_state.status = 'DONE'
                       AND index_state.success = TRUE
                      WHERE chunk.resource_id = conversion.resource_id
                        AND index_state.id IS NULL
                  )
                """, UUID.class, rootId));

        return reusableResourceIds;
    }

    public KnowledgeResourceRead saveRead(KnowledgeResourceRead read) {
        Instant now = Instant.now();
        if (findReadByResourceId(read.getResourceId()).isEmpty()) {
            if (read.getId() == null) {
                read.setId(UUID.randomUUID());
            }
            read.setCreatedAt(now);
            read.setUpdatedAt(now);
            jdbcTemplate.update("""
                    INSERT INTO knowledge_resource_reads (
                        id, resource_id, status, success, message, read_value, read_at, created_at, updated_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    read.getId(),
                    read.getResourceId(),
                    read.getStatus().name(),
                    read.getSuccess(),
                    read.getMessage(),
                    read.getValue(),
                    timestamp(read.getReadAt()),
                    timestamp(read.getCreatedAt()),
                    timestamp(read.getUpdatedAt())
            );
            return read;
        }
        read.setUpdatedAt(now);
        jdbcTemplate.update("""
                UPDATE knowledge_resource_reads
                SET status = ?,
                    success = ?,
                    message = ?,
                    read_value = ?,
                    read_at = ?,
                    updated_at = ?
                WHERE resource_id = ?
                """,
                read.getStatus().name(),
                read.getSuccess(),
                read.getMessage(),
                read.getValue(),
                timestamp(read.getReadAt()),
                timestamp(read.getUpdatedAt()),
                read.getResourceId()
        );
        return findReadByResourceId(read.getResourceId()).orElseThrow();
    }

    public Optional<KnowledgeResourceConversion> findConversionByResourceId(UUID resourceId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT * FROM knowledge_resource_conversions WHERE resource_id = ?",
                    this::mapConversion,
                    resourceId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    public KnowledgeResourceConversion saveConversion(KnowledgeResourceConversion conversion) {
        Instant now = Instant.now();
        if (findConversionByResourceId(conversion.getResourceId()).isEmpty()) {
            if (conversion.getId() == null) {
                conversion.setId(UUID.randomUUID());
            }
            conversion.setCreatedAt(now);
            conversion.setUpdatedAt(now);
            jdbcTemplate.update("""
                    INSERT INTO knowledge_resource_conversions (
                        id, resource_id, status, success, message, conversion_value, converted_at, created_at, updated_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    conversion.getId(),
                    conversion.getResourceId(),
                    conversion.getStatus().name(),
                    conversion.getSuccess(),
                    conversion.getMessage(),
                    conversion.getValue(),
                    timestamp(conversion.getConvertedAt()),
                    timestamp(conversion.getCreatedAt()),
                    timestamp(conversion.getUpdatedAt())
            );
            return conversion;
        }
        conversion.setUpdatedAt(now);
        jdbcTemplate.update("""
                UPDATE knowledge_resource_conversions
                SET status = ?,
                    success = ?,
                    message = ?,
                    conversion_value = ?,
                    converted_at = ?,
                    updated_at = ?
                WHERE resource_id = ?
                """,
                conversion.getStatus().name(),
                conversion.getSuccess(),
                conversion.getMessage(),
                conversion.getValue(),
                timestamp(conversion.getConvertedAt()),
                timestamp(conversion.getUpdatedAt()),
                conversion.getResourceId()
        );
        return findConversionByResourceId(conversion.getResourceId()).orElseThrow();
    }

    public List<KnowledgeResourceChunk> findChunksByResourceId(UUID resourceId) {
        return jdbcTemplate.query("""
                SELECT *
                FROM knowledge_resource_chunks
                WHERE resource_id = ?
                ORDER BY chunk_index
                """, this::mapChunk, resourceId);
    }

    public Optional<KnowledgeResourceChunk> findChunkById(UUID chunkId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT * FROM knowledge_resource_chunks WHERE id = ?",
                    this::mapChunk,
                    chunkId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    public KnowledgeResourceChunk saveChunk(KnowledgeResourceChunk chunk) {
        Instant now = Instant.now();
        Optional<KnowledgeResourceChunk> existing = findChunksByResourceId(chunk.getResourceId()).stream()
                .filter(candidate -> candidate.getChunkIndex() == chunk.getChunkIndex())
                .findFirst();
        if (existing.isEmpty()) {
            if (chunk.getId() == null) {
                chunk.setId(UUID.randomUUID());
            }
            chunk.setCreatedAt(now);
            chunk.setUpdatedAt(now);
            jdbcTemplate.update("""
                    INSERT INTO knowledge_resource_chunks (
                        id, resource_id, chunk_index, start_offset, end_offset,
                        content_hash, created_at, updated_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    chunk.getId(),
                    chunk.getResourceId(),
                    chunk.getChunkIndex(),
                    chunk.getStartOffset(),
                    chunk.getEndOffset(),
                    chunk.getContentHash(),
                    timestamp(chunk.getCreatedAt()),
                    timestamp(chunk.getUpdatedAt())
            );
            return chunk;
        }
        chunk.setId(existing.get().getId());
        chunk.setCreatedAt(existing.get().getCreatedAt());
        chunk.setUpdatedAt(now);
        jdbcTemplate.update("""
                UPDATE knowledge_resource_chunks
                SET start_offset = ?,
                    end_offset = ?,
                    content_hash = ?,
                    updated_at = ?
                WHERE id = ?
                """,
                chunk.getStartOffset(),
                chunk.getEndOffset(),
                chunk.getContentHash(),
                timestamp(chunk.getUpdatedAt()),
                chunk.getId()
        );
        return chunk;
    }

    public void deleteChunksByResourceId(UUID resourceId) {
        jdbcTemplate.update("DELETE FROM knowledge_resource_chunks WHERE resource_id = ?", resourceId);
    }

    public Optional<KnowledgeResourceIndex> findIndexByChunkId(UUID chunkId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT * FROM knowledge_resource_indexes WHERE chunk_id = ?",
                    this::mapIndex,
                    chunkId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    public KnowledgeResourceIndex saveIndex(KnowledgeResourceIndex index) {
        Instant now = Instant.now();
        if (findIndexByChunkId(index.getChunkId()).isEmpty()) {
            if (index.getId() == null) {
                index.setId(UUID.randomUUID());
            }
            index.setCreatedAt(now);
            index.setUpdatedAt(now);
            jdbcTemplate.update("""
                    INSERT INTO knowledge_resource_indexes (
                        id, chunk_id, status, success, message, index_reference,
                        indexed_at, created_at, updated_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    index.getId(),
                    index.getChunkId(),
                    index.getStatus().name(),
                    index.getSuccess(),
                    index.getMessage(),
                    index.getIndexReference(),
                    timestamp(index.getIndexedAt()),
                    timestamp(index.getCreatedAt()),
                    timestamp(index.getUpdatedAt())
            );
            return index;
        }
        index.setUpdatedAt(now);
        jdbcTemplate.update("""
                UPDATE knowledge_resource_indexes
                SET status = ?,
                    success = ?,
                    message = ?,
                    index_reference = ?,
                    indexed_at = ?,
                    updated_at = ?
                WHERE chunk_id = ?
                """,
                index.getStatus().name(),
                index.getSuccess(),
                index.getMessage(),
                index.getIndexReference(),
                timestamp(index.getIndexedAt()),
                timestamp(index.getUpdatedAt()),
                index.getChunkId()
        );
        return findIndexByChunkId(index.getChunkId()).orElseThrow();
    }

    private KnowledgeResourceRead mapRead(ResultSet resultSet, int rowNumber) throws SQLException {
        KnowledgeResourceRead read = new KnowledgeResourceRead();
        read.setId(resultSet.getObject("id", UUID.class));
        read.setResourceId(resultSet.getObject("resource_id", UUID.class));
        read.setStatus(WorkStatus.valueOf(resultSet.getString("status")));
        read.setSuccess(nullableBoolean(resultSet, "success"));
        read.setMessage(resultSet.getString("message"));
        read.setValue(resultSet.getBytes("read_value"));
        read.setReadAt(instant(resultSet, "read_at"));
        read.setCreatedAt(instant(resultSet, "created_at"));
        read.setUpdatedAt(instant(resultSet, "updated_at"));
        return read;
    }

    private KnowledgeResourceConversion mapConversion(ResultSet resultSet, int rowNumber) throws SQLException {
        KnowledgeResourceConversion conversion = new KnowledgeResourceConversion();
        conversion.setId(resultSet.getObject("id", UUID.class));
        conversion.setResourceId(resultSet.getObject("resource_id", UUID.class));
        conversion.setStatus(WorkStatus.valueOf(resultSet.getString("status")));
        conversion.setSuccess(nullableBoolean(resultSet, "success"));
        conversion.setMessage(resultSet.getString("message"));
        conversion.setValue(resultSet.getString("conversion_value"));
        conversion.setConvertedAt(instant(resultSet, "converted_at"));
        conversion.setCreatedAt(instant(resultSet, "created_at"));
        conversion.setUpdatedAt(instant(resultSet, "updated_at"));
        return conversion;
    }

    private KnowledgeResourceChunk mapChunk(ResultSet resultSet, int rowNumber) throws SQLException {
        KnowledgeResourceChunk chunk = new KnowledgeResourceChunk();
        chunk.setId(resultSet.getObject("id", UUID.class));
        chunk.setResourceId(resultSet.getObject("resource_id", UUID.class));
        chunk.setChunkIndex(resultSet.getInt("chunk_index"));
        chunk.setStartOffset(resultSet.getInt("start_offset"));
        chunk.setEndOffset(resultSet.getInt("end_offset"));
        chunk.setContentHash(resultSet.getString("content_hash"));
        chunk.setCreatedAt(instant(resultSet, "created_at"));
        chunk.setUpdatedAt(instant(resultSet, "updated_at"));
        return chunk;
    }

    private KnowledgeResourceIndex mapIndex(ResultSet resultSet, int rowNumber) throws SQLException {
        KnowledgeResourceIndex index = new KnowledgeResourceIndex();
        index.setId(resultSet.getObject("id", UUID.class));
        index.setChunkId(resultSet.getObject("chunk_id", UUID.class));
        index.setStatus(WorkStatus.valueOf(resultSet.getString("status")));
        index.setSuccess(nullableBoolean(resultSet, "success"));
        index.setMessage(resultSet.getString("message"));
        index.setIndexReference(resultSet.getString("index_reference"));
        index.setIndexedAt(instant(resultSet, "indexed_at"));
        index.setCreatedAt(instant(resultSet, "created_at"));
        index.setUpdatedAt(instant(resultSet, "updated_at"));
        return index;
    }

}
