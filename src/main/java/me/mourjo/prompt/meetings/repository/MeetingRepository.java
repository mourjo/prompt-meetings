package me.mourjo.prompt.meetings.repository;

import me.mourjo.prompt.meetings.dto.MeetingResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class MeetingRepository {
    private final JdbcTemplate jdbcTemplate;

    public MeetingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Long save(String title, LocalDateTime startTime, LocalDateTime endTime, String timezone, String organizerUsername, String calendarName) {
        String sql = "INSERT INTO meetings (title, start_time, end_time, timezone, organizer_username, calendar_name) VALUES (?, ?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, title);
            ps.setTimestamp(2, Timestamp.valueOf(startTime));
            ps.setTimestamp(3, Timestamp.valueOf(endTime));
            ps.setString(4, timezone);
            ps.setString(5, organizerUsername);
            ps.setString(6, calendarName);
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to retrieve generated key for meeting insertion.");
        }
        return key.longValue();
    }

    public boolean existsById(Long id) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM meetings WHERE id = ?",
            Integer.class,
            id
        );
        return count != null && count > 0;
    }

    public Optional<String> getOrganizerUsername(Long id) {
        List<String> results = jdbcTemplate.query(
            "SELECT organizer_username FROM meetings WHERE id = ?",
            (rs, rowNum) -> rs.getString("organizer_username"),
            id
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public List<MeetingResponse> findMeetingsForUser(String username) {
        String sql = """
            SELECT m.id, m.title, m.start_time, m.end_time, m.timezone, m.organizer_username, m.calendar_name,
                   i.status AS user_status
            FROM meetings m
            JOIN invitations i ON m.id = i.meeting_id
            WHERE i.invitee_username = ? AND i.status IN ('ACCEPTED', 'REJECTED')
            ORDER BY m.start_time ASC
            """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new MeetingResponse(
            rs.getLong("id"),
            rs.getString("title"),
            rs.getTimestamp("start_time").toLocalDateTime(),
            rs.getTimestamp("end_time").toLocalDateTime(),
            rs.getString("timezone"),
            rs.getString("organizer_username"),
            rs.getString("user_status"),
            rs.getString("calendar_name")
        ), username);
    }
}
