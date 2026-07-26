package me.mourjo.prompt.meetings.repository;

import me.mourjo.prompt.meetings.dto.CalendarResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class CalendarRepository {
    private final JdbcTemplate jdbcTemplate;

    public CalendarRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(String name, double priority) {
        try {
            jdbcTemplate.update("INSERT INTO calendars (name, priority) VALUES (?, ?)", name, priority);
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException("Calendar already exists with name: " + name);
        }
    }

    public boolean existsByName(String name) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM calendars WHERE name = ?",
            Integer.class,
            name
        );
        return count != null && count > 0;
    }

    public double getPriority(String name) {
        Double priority = jdbcTemplate.queryForObject(
            "SELECT priority FROM calendars WHERE name = ?",
            Double.class,
            name
        );
        if (priority == null) {
            throw new IllegalArgumentException("Calendar not found: " + name);
        }
        return priority;
    }

    public List<CalendarResponse> findAll() {
        return jdbcTemplate.query(
            "SELECT name, priority FROM calendars ORDER BY priority ASC, name ASC",
            (rs, rowNum) -> new CalendarResponse(
                rs.getString("name"),
                rs.getDouble("priority")
            )
        );
    }
}
