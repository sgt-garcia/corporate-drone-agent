package ai.corporatedroneagent.repository;

import ai.corporatedroneagent.model.ApplicationSettings;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.UncheckedIOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SettingsRepository {

    private static final int SETTINGS_ID = 1;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public SettingsRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public ApplicationSettings get() {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT settings_json FROM app_settings WHERE id = ?",
                    this::mapSettings,
                    SETTINGS_ID
            );
        } catch (EmptyResultDataAccessException exception) {
            return save(new ApplicationSettings());
        }
    }

    public ApplicationSettings save(ApplicationSettings settings) {
        jdbcTemplate.update("""
                MERGE INTO app_settings (id, settings_json, updated_at)
                KEY(id)
                VALUES (?, ?, ?)
                """,
                SETTINGS_ID,
                writeSettings(settings),
                Timestamp.from(Instant.now())
        );
        return settings;
    }

    private ApplicationSettings mapSettings(ResultSet resultSet, int rowNumber) throws SQLException {
        try {
            return objectMapper.readValue(resultSet.getString("settings_json"), ApplicationSettings.class);
        } catch (JsonProcessingException exception) {
            throw new UncheckedIOException("Could not read application settings from database", exception);
        }
    }

    private String writeSettings(ApplicationSettings settings) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(settings);
        } catch (JsonProcessingException exception) {
            throw new UncheckedIOException("Could not write application settings to database", exception);
        }
    }
}
