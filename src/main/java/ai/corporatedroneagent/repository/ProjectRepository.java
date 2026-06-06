package ai.corporatedroneagent.repository;

import ai.corporatedroneagent.model.Project;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ProjectRepository {

    private final JdbcTemplate jdbcTemplate;

    public ProjectRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Project> findAll() {
        return jdbcTemplate.query("SELECT * FROM projects", this::mapProject);
    }

    public Optional<Project> findById(UUID id) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT * FROM projects WHERE id = ?",
                    this::mapProject,
                    id
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Transactional
    public Project save(Project project) {
        Instant now = Instant.now();
        if (project.getId() == null) {
            project.setId(UUID.randomUUID());
        }

        if (findById(project.getId()).isEmpty()) {
            jdbcTemplate.update("""
                    INSERT INTO projects (
                        id, name, working_folder, custom_instructions, created_at, updated_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    project.getId(),
                    project.getName(),
                    project.getWorkingFolder(),
                    project.getCustomInstructions(),
                    timestamp(project.getCreatedAt()),
                    Timestamp.from(now)
            );
        } else {
            jdbcTemplate.update("""
                    UPDATE projects
                    SET name = ?,
                        working_folder = ?,
                        custom_instructions = ?,
                        created_at = ?,
                        updated_at = ?
                    WHERE id = ?
                    """,
                    project.getName(),
                    project.getWorkingFolder(),
                    project.getCustomInstructions(),
                    timestamp(project.getCreatedAt()),
                    Timestamp.from(now),
                    project.getId()
            );
        }

        syncConversationOrder(project);
        return findById(project.getId()).orElse(project);
    }

    @Transactional
    public synchronized boolean delete(UUID id) {
        return jdbcTemplate.update("DELETE FROM projects WHERE id = ?", id) > 0;
    }

    private Project mapProject(ResultSet resultSet, int rowNumber) throws SQLException {
        Project project = new Project();
        UUID projectId = resultSet.getObject("id", UUID.class);
        project.setId(projectId);
        project.setName(resultSet.getString("name"));
        project.setWorkingFolder(resultSet.getString("working_folder"));
        project.setCustomInstructions(resultSet.getString("custom_instructions"));
        project.setCreatedAt(instant(resultSet, "created_at"));
        project.setConversationIds(conversationIds(projectId));
        return project;
    }

    private List<UUID> conversationIds(UUID projectId) {
        return jdbcTemplate.queryForList("""
                SELECT id
                FROM conversations
                WHERE project_id = ?
                ORDER BY sort_order, created_at, name
                """, UUID.class, projectId);
    }

    private void syncConversationOrder(Project project) {
        List<UUID> conversationIds = project.getConversationIds();
        for (int index = 0; index < conversationIds.size(); index++) {
            jdbcTemplate.update("""
                    UPDATE conversations
                    SET sort_order = ?,
                        updated_at = ?
                    WHERE id = ?
                      AND project_id = ?
                    """,
                    index,
                    Timestamp.from(Instant.now()),
                    conversationIds.get(index),
                    project.getId()
            );
        }
    }

    private Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private Instant instant(ResultSet resultSet, String columnName) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(columnName);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
