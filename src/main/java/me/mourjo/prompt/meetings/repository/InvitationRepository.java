package me.mourjo.prompt.meetings.repository;

import me.mourjo.prompt.meetings.dto.PendingInvitationResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class InvitationRepository {
    private final JdbcTemplate jdbcTemplate;

    public InvitationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void saveInvitation(Long meetingId, String inviteeUsername) {
        try {
            jdbcTemplate.update(
                "INSERT INTO invitations (meeting_id, invitee_username, status) VALUES (?, ?, 'PENDING')",
                meetingId,
                inviteeUsername
            );
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException("User is already invited to this meeting");
        }
    }

    public void updateStatus(Long meetingId, String inviteeUsername, String status) {
        int updated = jdbcTemplate.update(
            "UPDATE invitations SET status = ? WHERE meeting_id = ? AND invitee_username = ?",
            status,
            meetingId,
            inviteeUsername
        );
        if (updated == 0) {
            throw new IllegalArgumentException("No invitation found for user to this meeting");
        }
    }

    public Optional<String> getInvitationStatus(Long meetingId, String inviteeUsername) {
        List<String> results = jdbcTemplate.query(
            "SELECT status FROM invitations WHERE meeting_id = ? AND invitee_username = ?",
            (rs, rowNum) -> rs.getString("status"),
            meetingId,
            inviteeUsername
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public List<PendingInvitationResponse> findPendingInvitationsForUser(String username) {
        String sql = """
            SELECT m.id AS meeting_id, m.title AS meeting_name, m.organizer_username AS invited_by,
                   m.start_time, m.end_time, m.timezone
            FROM invitations i
            JOIN meetings m ON i.meeting_id = m.id
            WHERE i.invitee_username = ? AND i.status = 'PENDING'
            ORDER BY m.start_time ASC
            """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            LocalDateTime start = rs.getTimestamp("start_time").toLocalDateTime();
            LocalDateTime end = rs.getTimestamp("end_time").toLocalDateTime();
            long durationMinutes = Duration.between(start, end).toMinutes();
            String durationStr = durationMinutes + " minutes";
            return new PendingInvitationResponse(
                rs.getLong("meeting_id"),
                rs.getString("meeting_name"),
                rs.getString("invited_by"),
                start,
                end,
                rs.getString("timezone"),
                durationMinutes,
                durationStr
            );
        }, username);
    }
}
