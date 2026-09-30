package io.github.ilic02.coderunner.storage;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PostConstruct;

@Repository
public class SnippetRepository {

    private static final String ID_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final int ID_LENGTH = 8;

    private final JdbcTemplate jdbc;
    private final SecureRandom random = new SecureRandom();

    public SnippetRepository(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
        createSchema();
    }

    @PostConstruct
    void createSchema() {
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS snippets (
                id TEXT PRIMARY KEY,
                language TEXT NOT NULL,
                source TEXT NOT NULL,
                stdin TEXT NOT NULL,
                created_at TEXT NOT NULL
            )
            """);
    }

    public String save(String language, String source, String stdin) {
        String id = generateId();
        jdbc.update(
            "INSERT INTO snippets (id, language, source, stdin, created_at) VALUES (?, ?, ?, ?, ?)",
            id, language, source, stdin, Instant.now().toString());
        return id;
    }

    public Optional<Snippet> findById(String id) {
        var results = jdbc.query(
            "SELECT id, language, source, stdin, created_at FROM snippets WHERE id = ?",
            ROW_MAPPER, id);
        return results.stream().findFirst();
    }

    private String generateId() {
        StringBuilder sb = new StringBuilder(ID_LENGTH);
        for (int i = 0; i < ID_LENGTH; i++) {
            sb.append(ID_CHARS.charAt(random.nextInt(ID_CHARS.length())));
        }
        return sb.toString();
    }

    private static final RowMapper<Snippet> ROW_MAPPER = (rs, rowNum) -> new Snippet(
        rs.getString("id"),
        rs.getString("language"),
        rs.getString("source"),
        rs.getString("stdin"),
        rs.getString("created_at"));

    public record Snippet(String id, String language, String source, String stdin, String createdAt) {}
}