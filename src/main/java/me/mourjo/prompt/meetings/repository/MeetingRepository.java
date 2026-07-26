package me.mourjo.prompt.meetings.repository;

import me.mourjo.prompt.meetings.dto.MeetingConflictCheck;
import me.mourjo.prompt.meetings.model.Meeting;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MeetingRepository extends ListCrudRepository<Meeting, Long> {

    @Query("SELECT organizer_username FROM meetings WHERE id = :id")
    Optional<String> getOrganizerUsername(@Param("id") Long id);

    @Query("""
        SELECT m.id, m.title, m.start_time, m.end_time, m.timezone, m.organizer_username, m.calendar_name,
               i.status AS user_status
        FROM meetings m
        JOIN invitations i ON m.id = i.meeting_id
        WHERE i.invitee_username = :username AND i.status IN ('ACCEPTED', 'REJECTED')
        ORDER BY m.start_time ASC
        """)
    List<UserMeetingInfo> findMeetingsForUser(@Param("username") String username);

    @Query("""
        SELECT m.id, m.title, m.start_time, m.end_time, m.timezone, m.calendar_name, c.priority AS calendar_priority
        FROM meetings m
        JOIN invitations i ON m.id = i.meeting_id
        JOIN calendars c ON m.calendar_name = c.name
        WHERE i.invitee_username = :username AND i.status = 'ACCEPTED'
        """)
    List<MeetingConflictCheck> findAcceptedMeetingsForConflictCheck(@Param("username") String username);
}
