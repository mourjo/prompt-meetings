package me.mourjo.prompt.meetings.repository;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public class UserRepository {
    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(String username) {
        try {
            jdbcTemplate.update("INSERT INTO users (username) VALUES (?)", username);
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException("Username already exists: " + username);
        }
    }

    public boolean existsByUsername(String username) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM users WHERE username = ?",
            Integer.class,
            username
        );
        return count != null && count > 0;
    }

    public List<String> findAllUsernames() {
        return jdbcTemplate.query(
            "SELECT username FROM users ORDER BY username ASC",
            (rs, rowNum) -> rs.getString("username")
        );
    }
}
