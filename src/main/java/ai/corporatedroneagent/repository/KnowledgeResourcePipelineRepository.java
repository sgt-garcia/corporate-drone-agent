package ai.corporatedroneagent.repository;

import static ai.corporatedroneagent.repository.KnowledgeRepositorySupport.instant;
import static ai.corporatedroneagent.repository.KnowledgeRepositorySupport.nullableBoolean;
import static ai.corporatedroneagent.repository.KnowledgeRepositorySupport.timestamp;

import ai.corporatedroneagent.model.knowledge.KnowledgePipelineReason;
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
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class KnowledgeResourcePipelineRepository {

    private static final PipelineTable<KnowledgeResourceRead> READ_PIPELINE = new PipelineTable<>(
            "knowledge_resource_reads",
            "resource_id",
            "read_value",
            "read_at",
            KnowledgeResourceRead::getId,
            KnowledgeResourceRead::setId,
            KnowledgeResourceRead::getResourceId,
            KnowledgeResourceRead::getStatus,
            KnowledgeResourceRead::getSuccess,
            KnowledgeResourceRead::getReason,
            KnowledgeResourceRead::getMessage,
            KnowledgeResourceRead::getValue,
            KnowledgeResourceRead::getReadAt,
            KnowledgeResourceRead::setCreatedAt,
            KnowledgeResourceRead::setUpdatedAt
    );
    private static final PipelineTable<KnowledgeResourceConversion> CONVERSION_PIPELINE = new PipelineTable<>(
            "knowledge_resource_conversions",
            "resource_id",
            "conversion_value",
            "converted_at",
            KnowledgeResourceConversion::getId,
            KnowledgeResourceConversion::setId,
            KnowledgeResourceConversion::getResourceId,
            KnowledgeResourceConversion::getStatus,
            KnowledgeResourceConversion::getSuccess,
            KnowledgeResourceConversion::getReason,
            KnowledgeResourceConversion::getMessage,
            KnowledgeResourceConversion::getValue,
            KnowledgeResourceConversion::getConvertedAt,
            KnowledgeResourceConversion::setCreatedAt,
            KnowledgeResourceConversion::setUpdatedAt
    );
    private static final PipelineTable<KnowledgeResourceIndex> INDEX_PIPELINE = new PipelineTable<>(
            "knowledge_resource_indexes",
            "chunk_id",
            "index_reference",
            "indexed_at",
            KnowledgeResourceIndex::getId,
            KnowledgeResourceIndex::setId,
            KnowledgeResourceIndex::getChunkId,
            KnowledgeResourceIndex::getStatus,
            KnowledgeResourceIndex::getSuccess,
            KnowledgeResourceIndex::getReason,
            KnowledgeResourceIndex::getMessage,
            KnowledgeResourceIndex::getIndexReference,
            KnowledgeResourceIndex::getIndexedAt,
            KnowledgeResourceIndex::setCreatedAt,
            KnowledgeResourceIndex::setUpdatedAt
    );

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
                  AND read_state.reason IN (?, ?)
                """,
                UUID.class,
                rootId,
                KnowledgePipelineReason.UNSUPPORTED_FILE_FORMAT.name(),
                KnowledgePipelineReason.FILE_TOO_LARGE.name()
        ));

        reusableResourceIds.addAll(jdbcTemplate.queryForList("""
                SELECT conversion.resource_id
                FROM knowledge_resources resource
                JOIN knowledge_resource_conversions conversion
                  ON conversion.resource_id = resource.id
                WHERE resource.root_id = ?
                  AND conversion.status = 'DONE'
                  AND conversion.success = FALSE
                  AND conversion.reason = ?
                """,
                UUID.class,
                rootId,
                KnowledgePipelineReason.UTF8_DECODE_FAILED.name()
        ));

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
        return savePipelineState(read, READ_PIPELINE, this::findReadByResourceId);
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
        return savePipelineState(conversion, CONVERSION_PIPELINE, this::findConversionByResourceId);
    }

    public List<KnowledgeResourceChunk> findChunksByResourceId(UUID resourceId) {
        return jdbcTemplate.query("""
                SELECT *
                FROM knowledge_resource_chunks
                WHERE resource_id = ?
                ORDER BY chunk_index
                """, this::mapChunk, resourceId);
    }

    public KnowledgeResourceChunk insertChunk(KnowledgeResourceChunk chunk) {
        return insertChunk(chunk, Instant.now());
    }

    private KnowledgeResourceChunk insertChunk(KnowledgeResourceChunk chunk, Instant now) {
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
        return savePipelineState(index, INDEX_PIPELINE, this::findIndexByChunkId);
    }

    private <T> T savePipelineState(
            T state,
            PipelineTable<T> table,
            Function<UUID, Optional<T>> finder) {
        Instant now = Instant.now();
        UUID key = table.key().apply(state);
        if (finder.apply(key).isEmpty()) {
            if (table.id().apply(state) == null) {
                table.setId().accept(state, UUID.randomUUID());
            }
            table.setCreatedAt().accept(state, now);
            table.setUpdatedAt().accept(state, now);
            jdbcTemplate.update(
                    table.insertSql(),
                    table.id().apply(state),
                    key,
                    table.status().apply(state).name(),
                    table.success().apply(state),
                    reasonName(table.reason().apply(state)),
                    table.message().apply(state),
                    table.value().apply(state),
                    timestamp(table.processedAt().apply(state)),
                    timestamp(now),
                    timestamp(now)
            );
            return state;
        }
        table.setUpdatedAt().accept(state, now);
        jdbcTemplate.update(
                table.updateSql(),
                table.status().apply(state).name(),
                table.success().apply(state),
                reasonName(table.reason().apply(state)),
                table.message().apply(state),
                table.value().apply(state),
                timestamp(table.processedAt().apply(state)),
                timestamp(now),
                key
        );
        return finder.apply(key).orElseThrow();
    }

    private KnowledgeResourceRead mapRead(ResultSet resultSet, int rowNumber) throws SQLException {
        KnowledgeResourceRead read = new KnowledgeResourceRead();
        read.setId(resultSet.getObject("id", UUID.class));
        read.setResourceId(resultSet.getObject("resource_id", UUID.class));
        read.setStatus(WorkStatus.valueOf(resultSet.getString("status")));
        read.setSuccess(nullableBoolean(resultSet, "success"));
        read.setReason(reason(resultSet, "reason"));
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
        conversion.setReason(reason(resultSet, "reason"));
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
        index.setReason(reason(resultSet, "reason"));
        index.setMessage(resultSet.getString("message"));
        index.setIndexReference(resultSet.getString("index_reference"));
        index.setIndexedAt(instant(resultSet, "indexed_at"));
        index.setCreatedAt(instant(resultSet, "created_at"));
        index.setUpdatedAt(instant(resultSet, "updated_at"));
        return index;
    }

    private record PipelineTable<T>(
            String tableName,
            String keyColumn,
            String valueColumn,
            String processedAtColumn,
            Function<T, UUID> id,
            BiConsumer<T, UUID> setId,
            Function<T, UUID> key,
            Function<T, WorkStatus> status,
            Function<T, Boolean> success,
            Function<T, KnowledgePipelineReason> reason,
            Function<T, String> message,
            Function<T, Object> value,
            Function<T, Instant> processedAt,
            BiConsumer<T, Instant> setCreatedAt,
            BiConsumer<T, Instant> setUpdatedAt
    ) {

        String insertSql() {
            return """
                    INSERT INTO %s (
                        id, %s, status, success, reason, message, %s, %s, created_at, updated_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """.formatted(tableName, keyColumn, valueColumn, processedAtColumn);
        }

        String updateSql() {
            return """
                    UPDATE %s
                    SET status = ?,
                        success = ?,
                        reason = ?,
                        message = ?,
                        %s = ?,
                        %s = ?,
                        updated_at = ?
                    WHERE %s = ?
                    """.formatted(tableName, valueColumn, processedAtColumn, keyColumn);
        }
    }

    private String reasonName(KnowledgePipelineReason reason) {
        return reason == null ? null : reason.name();
    }

    private KnowledgePipelineReason reason(ResultSet resultSet, String columnName) throws SQLException {
        String value = resultSet.getString(columnName);
        if (value == null || value.isBlank()) {
            return null;
        }
        return KnowledgePipelineReason.valueOf(value);
    }

}
