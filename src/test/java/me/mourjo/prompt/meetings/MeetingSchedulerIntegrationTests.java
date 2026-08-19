package me.mourjo.prompt.meetings;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import me.mourjo.prompt.meetings.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class MeetingSchedulerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @BeforeEach
    public void setUp() {
        jdbcTemplate.execute("DELETE FROM invitations");
        jdbcTemplate.execute("DELETE FROM meetings");
        jdbcTemplate.execute("DELETE FROM users");
        jdbcTemplate.execute("DELETE FROM calendars WHERE name <> 'default'");
    }

    private void createUser(String username) throws Exception {
        CreateUserRequest request = new CreateUserRequest(username);
        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    private void createCalendar(String username, String name, double priority) throws Exception {
        CreateCalendarRequest request = new CreateCalendarRequest(name, priority);
        mockMvc.perform(post("/calendars")
                .header("X-USERNAME", username)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    private Long createMeeting(String username, String title, LocalDateTime start, LocalDateTime end, String tz, String cal) throws Exception {
        CreateMeetingRequest request = new CreateMeetingRequest(title, start, end, tz, cal);
        String responseJson = mockMvc.perform(post("/meetings")
                .header("X-USERNAME", username)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(responseJson).get("id").asLong();
    }

    private void inviteUser(String username, Long meetingId, String invitee) throws Exception {
        InviteUserRequest request = new InviteUserRequest(invitee);
        mockMvc.perform(post("/meetings/" + meetingId + "/invites")
                .header("X-USERNAME", username)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    public void testCompleteWorkflow() throws Exception {
        // 1. Create User 'alice'
        CreateUserRequest createAlice = new CreateUserRequest("alice");
        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createAlice)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username", is("alice")));

        // 2. Create User 'bob'
        CreateUserRequest createBob = new CreateUserRequest("bob");
        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createBob)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username", is("bob")));

        // 3. Fail to create duplicate user 'alice'
        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createAlice)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Username already exists")));

        // 4. View all users
        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].username", is("alice")))
                .andExpect(jsonPath("$[1].username", is("bob")));

        // 5. Fail to create meeting without authentication header
        LocalDateTime start = LocalDateTime.of(2026, 8, 1, 10, 0);
        LocalDateTime end = LocalDateTime.of(2026, 8, 1, 11, 0);
        CreateMeetingRequest meetingRequest = new CreateMeetingRequest("Project Sync", start, end, "Europe/Paris", "default");

        mockMvc.perform(post("/meetings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(meetingRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", containsString("X-USERNAME")));

        // 6. Fail to create meeting with non-existent user
        mockMvc.perform(post("/meetings")
                .header("X-USERNAME", "charlie")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(meetingRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", containsString("not registered")));

        // 7. Create meeting successfully (alice is organizer, status defaults to ACCEPTED)
        String meetingResponseJson = mockMvc.perform(post("/meetings")
                .header("X-USERNAME", "alice")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(meetingRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.title", is("Project Sync")))
                .andExpect(jsonPath("$.organizerUsername", is("alice")))
                .andExpect(jsonPath("$.userStatus", is("ACCEPTED")))
                .andExpect(jsonPath("$.calendarName", is("default")))
                .andReturn().getResponse().getContentAsString();

        Long meetingId = objectMapper.readTree(meetingResponseJson).get("id").asLong();

        // 8. Fail to create meeting with invalid timezone
        CreateMeetingRequest badTzRequest = new CreateMeetingRequest("Project Sync", start, end, "Invalid/Timezone", "default");
        mockMvc.perform(post("/meetings")
                .header("X-USERNAME", "alice")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(badTzRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Invalid timezone")));

        // 9. Fail to create meeting with start time after end time
        CreateMeetingRequest badTimeRequest = new CreateMeetingRequest("Project Sync", end, start, "Europe/Paris", "default");
        mockMvc.perform(post("/meetings")
                .header("X-USERNAME", "alice")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(badTimeRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Start time must be before end time")));

        // 10. Invite Bob (by Alice)
        InviteUserRequest inviteBob = new InviteUserRequest("bob");
        mockMvc.perform(post("/meetings/" + meetingId + "/invites")
                .header("X-USERNAME", "alice")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(inviteBob)))
                .andExpect(status().isCreated());

        // 11. Fail to invite non-existent user 'charlie'
        InviteUserRequest inviteCharlie = new InviteUserRequest("charlie");
        mockMvc.perform(post("/meetings/" + meetingId + "/invites")
                .header("X-USERNAME", "alice")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(inviteCharlie)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("does not exist")));

        // 12. Fail to invite if not organizer (bob tries to invite alice)
        mockMvc.perform(post("/meetings/" + meetingId + "/invites")
                .header("X-USERNAME", "bob")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(inviteBob)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("Only the meeting organizer can invite")));

        // 14. View pending invitations for bob (received by current user)
        mockMvc.perform(get("/meetings/invitations/pending")
                .header("X-USERNAME", "bob"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].meetingName", is("Project Sync")));

        // 16. Accept invite as Bob
        mockMvc.perform(post("/meetings/" + meetingId + "/invites/accept")
                .header("X-USERNAME", "bob"))
                .andExpect(status().isOk());

        // 16.5 Accept invite as Bob again (should fail because it's already ACCEPTED)
        mockMvc.perform(post("/meetings/" + meetingId + "/invites/accept")
                .header("X-USERNAME", "bob"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Invitation is not in a pending state")));

        // 17. Verify Bob no longer has pending invitations
        mockMvc.perform(get("/meetings/invitations/pending")
                .header("X-USERNAME", "bob"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // 18. View Bob's meetings (should show ACCEPTED)
        mockMvc.perform(get("/meetings")
                .header("X-USERNAME", "bob"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(meetingId.intValue())))
                .andExpect(jsonPath("$[0].userStatus", is("ACCEPTED")))
                .andExpect(jsonPath("$[0].calendarName", is("default")));

        // 19. View Alice's meetings (should show ACCEPTED since organizer defaults to accepted)
        mockMvc.perform(get("/meetings")
                .header("X-USERNAME", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(meetingId.intValue())))
                .andExpect(jsonPath("$[0].userStatus", is("ACCEPTED")))
                .andExpect(jsonPath("$[0].calendarName", is("default")));

        // 19.5 Alice tries to reject her own meeting (fails because it's already ACCEPTED)
        mockMvc.perform(post("/meetings/" + meetingId + "/invites/reject")
                .header("X-USERNAME", "alice"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Invitation is not in a pending state")));

        // Verify Alice's meetings view still shows ACCEPTED for this meeting
        mockMvc.perform(get("/meetings")
                .header("X-USERNAME", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(meetingId.intValue())))
                .andExpect(jsonPath("$[0].userStatus", is("ACCEPTED")));

        // Register new user 'eve'
        CreateUserRequest createEve = new CreateUserRequest("eve");
        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createEve)))
                .andExpect(status().isCreated());

        // Alice invites Eve to the meeting
        InviteUserRequest inviteEve = new InviteUserRequest("eve");
        mockMvc.perform(post("/meetings/" + meetingId + "/invites")
                .header("X-USERNAME", "alice")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(inviteEve)))
                .andExpect(status().isCreated());

        // Eve rejects her pending invitation (succeeds)
        mockMvc.perform(post("/meetings/" + meetingId + "/invites/reject")
                .header("X-USERNAME", "eve"))
                .andExpect(status().isOk());

        // Verify Eve's meetings view shows REJECTED for this meeting
        mockMvc.perform(get("/meetings")
                .header("X-USERNAME", "eve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(meetingId.intValue())))
                .andExpect(jsonPath("$[0].userStatus", is("REJECTED")));

        // Eve tries to reject her already rejected invitation (fails)
        mockMvc.perform(post("/meetings/" + meetingId + "/invites/reject")
                .header("X-USERNAME", "eve"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Invitation is not in a pending state")));

        // Eve tries to accept her rejected invitation (fails)
        mockMvc.perform(post("/meetings/" + meetingId + "/invites/accept")
                .header("X-USERNAME", "eve"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Invitation is not in a pending state")));

        // Verify Bob's meetings view still shows ACCEPTED for this meeting (others can still attend)
        mockMvc.perform(get("/meetings")
                .header("X-USERNAME", "bob"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(meetingId.intValue())))
                .andExpect(jsonPath("$[0].userStatus", is("ACCEPTED")));

        // 20. Calendar workflow:
        // Get calendars initially - should only have the seeded default calendar
        mockMvc.perform(get("/calendars")
                .header("X-USERNAME", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("default")))
                .andExpect(jsonPath("$[0].priority", is(1.0)));

        // Create new calendar 'work' with priority 2.0
        CreateCalendarRequest createWork = new CreateCalendarRequest("work", 2.0);
        mockMvc.perform(post("/calendars")
                .header("X-USERNAME", "alice")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createWork)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("work")))
                .andExpect(jsonPath("$.priority", is(2.0)));

        // Fail to create a calendar named 'default'
        CreateCalendarRequest createDefaultDup = new CreateCalendarRequest("default", 3.0);
        mockMvc.perform(post("/calendars")
                .header("X-USERNAME", "alice")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createDefaultDup)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Cannot create or override the default calendar")));

        // Fetch calendars again - should have both default and work
        mockMvc.perform(get("/calendars")
                .header("X-USERNAME", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name", is("default")))
                .andExpect(jsonPath("$[1].name", is("work")));

        // Create a meeting in the new 'work' calendar
        CreateMeetingRequest workMeeting = new CreateMeetingRequest("Design Workshop", start.plusDays(1), end.plusDays(1), "Europe/Paris", "work");
        mockMvc.perform(post("/meetings")
                .header("X-USERNAME", "alice")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(workMeeting)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title", is("Design Workshop")))
                .andExpect(jsonPath("$.calendarName", is("work")));

        // Fail to create a meeting in a non-existent calendar
        CreateMeetingRequest invalidCalMeeting = new CreateMeetingRequest("Invalid Cal Meeting", start, end, "Europe/Paris", "non-existent-cal");
        mockMvc.perform(post("/meetings")
                .header("X-USERNAME", "alice")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidCalMeeting)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("does not exist")));

        // 21. Priority-Based Conflict Resolution workflow:
        // Create calendars: 'high-priority' (5.0) and 'low-priority' (2.0)
        CreateCalendarRequest createHigh = new CreateCalendarRequest("high-priority", 5.0);
        mockMvc.perform(post("/calendars")
                .header("X-USERNAME", "alice")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createHigh)))
                .andExpect(status().isCreated());

        CreateCalendarRequest createLow = new CreateCalendarRequest("low-priority", 2.0);
        mockMvc.perform(post("/calendars")
                .header("X-USERNAME", "alice")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createLow)))
                .andExpect(status().isCreated());

        // Create initial meeting in 'low-priority' calendar
        LocalDateTime timeStart1 = LocalDateTime.of(2026, 9, 1, 14, 0);
        LocalDateTime timeEnd1 = LocalDateTime.of(2026, 9, 1, 15, 0);
        CreateMeetingRequest lowMeeting = new CreateMeetingRequest("Low Priority Meeting", timeStart1, timeEnd1, "Europe/Paris", "low-priority");
        String lowMeetingRes = mockMvc.perform(post("/meetings")
                .header("X-USERNAME", "alice")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(lowMeeting)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long lowMeetingId = objectMapper.readTree(lowMeetingRes).get("id").asLong();

        // Fail to create overlapping meeting of SAME priority (should return 400)
        CreateMeetingRequest overlappingSameMeeting = new CreateMeetingRequest("Overlapping Same Priority", timeStart1.plusMinutes(30), timeEnd1.plusMinutes(30), "Europe/Paris", "low-priority");
        mockMvc.perform(post("/meetings")
                .header("X-USERNAME", "alice")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(overlappingSameMeeting)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Conflict with meeting")));

        // Succeed creating overlapping meeting of HIGHER priority
        CreateMeetingRequest overlappingHighMeeting = new CreateMeetingRequest("Overlapping High Priority", timeStart1.plusMinutes(30), timeEnd1.plusMinutes(30), "Europe/Paris", "high-priority");
        String highMeetingRes = mockMvc.perform(post("/meetings")
                .header("X-USERNAME", "alice")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(overlappingHighMeeting)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long highMeetingId = objectMapper.readTree(highMeetingRes).get("id").asLong();

        // Verify that the low priority meeting was automatically rejected
        mockMvc.perform(get("/meetings")
                .header("X-USERNAME", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + lowMeetingId + ")].userStatus", contains("AUTO_REJECTED")))
                .andExpect(jsonPath("$[?(@.id == " + highMeetingId + ")].userStatus", contains("ACCEPTED")));

        // Verify Timezone-aware overlap check:
        // Schedule a meeting in 'high-priority' (5.0) at 10:00 Europe/Paris to 11:00 Europe/Paris on Sept 2nd.
        LocalDateTime parisStart = LocalDateTime.of(2026, 9, 2, 10, 0);
        LocalDateTime parisEnd = LocalDateTime.of(2026, 9, 2, 11, 0);
        CreateMeetingRequest parisMeeting = new CreateMeetingRequest("Paris Meeting", parisStart, parisEnd, "Europe/Paris", "high-priority");
        mockMvc.perform(post("/meetings")
                .header("X-USERNAME", "alice")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(parisMeeting)))
                .andExpect(status().isCreated());

        // Try to schedule overlapping meeting in 'high-priority' (5.0) at 08:30 UTC to 09:30 UTC.
        // In summer (Sept 2), Europe/Paris is UTC+2, so 10:00-11:00 Europe/Paris = 08:00-09:00 UTC.
        // Therefore, 08:30-09:30 UTC overlaps with the Paris Meeting.
        // Since priority is the same (5.0 >= 5.0), it should be blocked and return 400.
        LocalDateTime utcStart = LocalDateTime.of(2026, 9, 2, 8, 30);
        LocalDateTime utcEnd = LocalDateTime.of(2026, 9, 2, 9, 30);
        CreateMeetingRequest utcMeeting = new CreateMeetingRequest("UTC Meeting", utcStart, utcEnd, "UTC", "high-priority");
        mockMvc.perform(post("/meetings")
                .header("X-USERNAME", "alice")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(utcMeeting)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Conflict with meeting")));

        // 22. Conflict Resolution on Accept Invite:
        // Create an initial meeting by Alice in 'low-priority' calendar (priority 2.0)
        LocalDateTime acceptTestStart = LocalDateTime.of(2026, 10, 1, 10, 0);
        LocalDateTime acceptTestEnd = LocalDateTime.of(2026, 10, 1, 11, 0);
        CreateMeetingRequest bobLowMeeting = new CreateMeetingRequest("Bob Low Priority Meeting", acceptTestStart, acceptTestEnd, "Europe/Paris", "low-priority");
        
        String bobLowMeetingRes = mockMvc.perform(post("/meetings")
                .header("X-USERNAME", "alice")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(bobLowMeeting)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long bobLowMeetingId = objectMapper.readTree(bobLowMeetingRes).get("id").asLong();

        // Invite Bob to the low-priority meeting
        mockMvc.perform(post("/meetings/" + bobLowMeetingId + "/invites")
                .header("X-USERNAME", "alice")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new InviteUserRequest("bob"))))
                .andExpect(status().isCreated());

        // Bob accepts the low-priority meeting (no conflict initially)
        mockMvc.perform(post("/meetings/" + bobLowMeetingId + "/invites/accept")
                .header("X-USERNAME", "bob"))
                .andExpect(status().isOk());

        // Verify Bob's meeting status is ACCEPTED
        mockMvc.perform(get("/meetings")
                .header("X-USERNAME", "bob"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + bobLowMeetingId + ")].userStatus", contains("ACCEPTED")));

        // Create a conflicting meeting in 'high-priority' calendar (priority 5.0)
        CreateMeetingRequest bobHighMeeting = new CreateMeetingRequest("Bob High Priority Meeting", acceptTestStart, acceptTestEnd, "Europe/Paris", "high-priority");
        String bobHighMeetingRes = mockMvc.perform(post("/meetings")
                .header("X-USERNAME", "alice")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(bobHighMeeting)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long bobHighMeetingId = objectMapper.readTree(bobHighMeetingRes).get("id").asLong();

        // Invite Bob to the high-priority meeting
        mockMvc.perform(post("/meetings/" + bobHighMeetingId + "/invites")
                .header("X-USERNAME", "alice")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new InviteUserRequest("bob"))))
                .andExpect(status().isCreated());

        // Bob accepts the high-priority meeting -> this should succeed and reject his low-priority meeting
        mockMvc.perform(post("/meetings/" + bobHighMeetingId + "/invites/accept")
                .header("X-USERNAME", "bob"))
                .andExpect(status().isOk());

        // Verify Bob's low priority meeting is now AUTO_REJECTED, and high priority meeting is ACCEPTED
        mockMvc.perform(get("/meetings")
                .header("X-USERNAME", "bob"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + bobLowMeetingId + ")].userStatus", contains("AUTO_REJECTED")))
                .andExpect(jsonPath("$[?(@.id == " + bobHighMeetingId + ")].userStatus", contains("ACCEPTED")));

        // Create User 'charlie'
        CreateUserRequest createCharlie = new CreateUserRequest("charlie");
        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createCharlie)))
                .andExpect(status().isCreated());

        // Create a conflicting meeting by Charlie in 'low-priority' calendar (priority 2.0)
        CreateMeetingRequest bobAnotherLowMeeting = new CreateMeetingRequest("Bob Another Low Priority Meeting", acceptTestStart, acceptTestEnd, "Europe/Paris", "low-priority");
        String bobAnotherLowMeetingRes = mockMvc.perform(post("/meetings")
                .header("X-USERNAME", "charlie")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(bobAnotherLowMeeting)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long bobAnotherLowMeetingId = objectMapper.readTree(bobAnotherLowMeetingRes).get("id").asLong();

        // Invite Bob to the new low-priority meeting
        mockMvc.perform(post("/meetings/" + bobAnotherLowMeetingId + "/invites")
                .header("X-USERNAME", "charlie")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new InviteUserRequest("bob"))))
                .andExpect(status().isCreated());

        // Bob attempts to accept the low-priority meeting -> should be blocked (400 Bad Request) because it conflicts with the high-priority meeting
        mockMvc.perform(post("/meetings/" + bobAnotherLowMeetingId + "/invites/accept")
                .header("X-USERNAME", "bob"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Conflict with meeting")));

        // Create User 'david'
        CreateUserRequest createDavid = new CreateUserRequest("david");
        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createDavid)))
                .andExpect(status().isCreated());

        // Create a conflicting meeting by David in 'high-priority' calendar (priority 5.0)
        CreateMeetingRequest bobAnotherHighMeeting = new CreateMeetingRequest("Bob Another High Priority Meeting", acceptTestStart, acceptTestEnd, "Europe/Paris", "high-priority");
        String bobAnotherHighMeetingRes = mockMvc.perform(post("/meetings")
                .header("X-USERNAME", "david")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(bobAnotherHighMeeting)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long bobAnotherHighMeetingId = objectMapper.readTree(bobAnotherHighMeetingRes).get("id").asLong();

        // Invite Bob to the new high-priority meeting
        mockMvc.perform(post("/meetings/" + bobAnotherHighMeetingId + "/invites")
                .header("X-USERNAME", "david")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new InviteUserRequest("bob"))))
                .andExpect(status().isCreated());

        // Bob attempts to accept the high-priority meeting -> should be blocked (400 Bad Request) because it conflicts with another high-priority meeting
        mockMvc.perform(post("/meetings/" + bobAnotherHighMeetingId + "/invites/accept")
                .header("X-USERNAME", "bob"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Conflict with meeting")));
    }

    @Test
    public void testUserValidationAndBoundaryConditions() throws Exception {
        // Blank username
        CreateUserRequest blankUser = new CreateUserRequest("");
        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(blankUser)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Validation failed")));

        // Username shorter than 3 characters
        CreateUserRequest shortUser = new CreateUserRequest("al");
        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(shortUser)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("between 3 and 50")));

        // Username longer than 50 characters
        String longUsernameStr = "a".repeat(51);
        CreateUserRequest longUser = new CreateUserRequest(longUsernameStr);
        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(longUser)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("between 3 and 50")));

        // Valid boundary usernames (3 chars and 50 chars)
        createUser("abc");
        createUser("a".repeat(50));

        // Get all users sorted alphabetically
        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].username", is("a".repeat(50))))
                .andExpect(jsonPath("$[1].username", is("abc")));
    }

    @Test
    public void testAuthenticationAndAuthorizationHeaderValidation() throws Exception {
        createUser("alice");
        LocalDateTime start = LocalDateTime.of(2026, 8, 10, 10, 0);
        LocalDateTime end = LocalDateTime.of(2026, 8, 10, 11, 0);
        CreateMeetingRequest meetingRequest = new CreateMeetingRequest("Team Sync", start, end, "UTC", "default");

        // 1. Missing header on /meetings
        mockMvc.perform(get("/meetings"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", containsString("X-USERNAME")));

        // 2. Blank header on /meetings
        mockMvc.perform(get("/meetings")
                .header("X-USERNAME", "   "))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", containsString("missing or empty")));

        // 3. Missing header on /calendars
        mockMvc.perform(get("/calendars"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", containsString("X-USERNAME")));

        // 4. Missing header on /meetings/invitations/pending
        mockMvc.perform(get("/meetings/invitations/pending"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", containsString("X-USERNAME")));

        // 5. Unregistered user header
        mockMvc.perform(get("/meetings")
                .header("X-USERNAME", "unknown_user"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", containsString("not registered")));
    }

    @Test
    public void testCalendarManagementValidationAndDuplicates() throws Exception {
        createUser("alice");

        // 1. Fetch initial calendars - default exists with priority 1.0
        mockMvc.perform(get("/calendars")
                .header("X-USERNAME", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("default")))
                .andExpect(jsonPath("$[0].priority", is(1.0)));

        // 2. Validation error: blank calendar name
        CreateCalendarRequest blankNameCal = new CreateCalendarRequest("", 2.0);
        mockMvc.perform(post("/calendars")
                .header("X-USERNAME", "alice")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(blankNameCal)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Validation failed")));

        // 3. Validation error: null priority
        CreateCalendarRequest nullPriorityCal = new CreateCalendarRequest("personal", null);
        mockMvc.perform(post("/calendars")
                .header("X-USERNAME", "alice")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(nullPriorityCal)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Validation failed")));

        // 4. Create custom calendars
        createCalendar("alice", "work", 2.0);
        createCalendar("alice", "urgent", 5.0);
        createCalendar("alice", "personal", 0.5);

        // 5. Fail to create duplicate custom calendar (work already exists)
        CreateCalendarRequest dupWork = new CreateCalendarRequest("work", 3.0);
        mockMvc.perform(post("/calendars")
                .header("X-USERNAME", "alice")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dupWork)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Calendar already exists with name: work")));

        // 6. Fail to override default calendar (case-insensitive "DEFAULT")
        CreateCalendarRequest dupDefault = new CreateCalendarRequest("DEFAULT", 10.0);
        mockMvc.perform(post("/calendars")
                .header("X-USERNAME", "alice")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dupDefault)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Cannot create or override the default calendar")));

        // 7. Verify calendars are sorted by priority ASC, name ASC
        mockMvc.perform(get("/calendars")
                .header("X-USERNAME", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].name", is("personal")))
                .andExpect(jsonPath("$[0].priority", is(0.5)))
                .andExpect(jsonPath("$[1].name", is("default")))
                .andExpect(jsonPath("$[1].priority", is(1.0)))
                .andExpect(jsonPath("$[2].name", is("work")))
                .andExpect(jsonPath("$[2].priority", is(2.0)))
                .andExpect(jsonPath("$[3].name", is("urgent")))
                .andExpect(jsonPath("$[3].priority", is(5.0)));
    }

    @Test
    public void testMeetingRequestValidationAndNotFoundExceptions() throws Exception {
        createUser("alice");
        createUser("bob");
        LocalDateTime start = LocalDateTime.of(2026, 8, 15, 10, 0);
        LocalDateTime end = LocalDateTime.of(2026, 8, 15, 11, 0);

        // 1. Validation error: blank title
        CreateMeetingRequest blankTitle = new CreateMeetingRequest("", start, end, "UTC", "default");
        mockMvc.perform(post("/meetings")
                .header("X-USERNAME", "alice")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(blankTitle)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Validation failed")));

        // 2. Validation error: null start time
        CreateMeetingRequest nullStart = new CreateMeetingRequest("Title", null, end, "UTC", "default");
        mockMvc.perform(post("/meetings")
                .header("X-USERNAME", "alice")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(nullStart)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Validation failed")));

        // 3. Validation error: null end time
        CreateMeetingRequest nullEnd = new CreateMeetingRequest("Title", start, null, "UTC", "default");
        mockMvc.perform(post("/meetings")
                .header("X-USERNAME", "alice")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(nullEnd)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Validation failed")));

        // 4. Equal start and end time (duration 0)
        CreateMeetingRequest zeroDuration = new CreateMeetingRequest("Title", start, start, "UTC", "default");
        mockMvc.perform(post("/meetings")
                .header("X-USERNAME", "alice")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(zeroDuration)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Start time must be before end time")));

        // 5. Non-existent meeting ID operations (NotFoundException -> 404 NOT_FOUND)
        Long nonExistentMeetingId = 999999L;

        // Invite to non-existent meeting
        mockMvc.perform(post("/meetings/" + nonExistentMeetingId + "/invites")
                .header("X-USERNAME", "alice")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new InviteUserRequest("bob"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("Meeting not found with ID: " + nonExistentMeetingId)));

        // Accept non-existent meeting
        mockMvc.perform(post("/meetings/" + nonExistentMeetingId + "/invites/accept")
                .header("X-USERNAME", "alice"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("Meeting not found with ID: " + nonExistentMeetingId)));

        // Reject non-existent meeting
        mockMvc.perform(post("/meetings/" + nonExistentMeetingId + "/invites/reject")
                .header("X-USERNAME", "alice"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("Meeting not found with ID: " + nonExistentMeetingId)));
    }

    @Test
    public void testInvitationValidationAndForbiddenCases() throws Exception {
        createUser("alice");
        createUser("bob");
        createUser("charlie");
        LocalDateTime start = LocalDateTime.of(2026, 8, 20, 10, 0);
        LocalDateTime end = LocalDateTime.of(2026, 8, 20, 11, 0);

        Long meetingId = createMeeting("alice", "Architecture Review", start, end, "UTC", "default");

        // 1. Validation error: blank invitee username
        mockMvc.perform(post("/meetings/" + meetingId + "/invites")
                .header("X-USERNAME", "alice")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new InviteUserRequest(""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Validation failed")));

        // 2. Organizer invites themselves
        mockMvc.perform(post("/meetings/" + meetingId + "/invites")
                .header("X-USERNAME", "alice")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new InviteUserRequest("alice"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Organizer cannot invite themselves")));

        // 3. Organizer invites Bob successfully
        inviteUser("alice", meetingId, "bob");

        // 4. Duplicate invitation (invite Bob again)
        mockMvc.perform(post("/meetings/" + meetingId + "/invites")
                .header("X-USERNAME", "alice")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new InviteUserRequest("bob"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("already invited")));

        // 5. Charlie is not invited -> Charlie attempts to accept (403 Forbidden)
        mockMvc.perform(post("/meetings/" + meetingId + "/invites/accept")
                .header("X-USERNAME", "charlie"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("Only an invited member can respond")));

        // 6. Charlie is not invited -> Charlie attempts to reject (403 Forbidden)
        mockMvc.perform(post("/meetings/" + meetingId + "/invites/reject")
                .header("X-USERNAME", "charlie"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("Only an invited member can respond")));
    }

    @Test
    public void testPendingInvitationsCalculationAndSorting() throws Exception {
        createUser("alice");
        createUser("bob");

        LocalDateTime time1 = LocalDateTime.of(2026, 9, 10, 14, 0);
        LocalDateTime time2 = LocalDateTime.of(2026, 9, 10, 9, 0);
        LocalDateTime time3 = LocalDateTime.of(2026, 9, 10, 11, 0);

        Long m1 = createMeeting("alice", "Late Meeting", time1, time1.plusMinutes(90), "UTC", "default");
        Long m2 = createMeeting("alice", "Early Meeting", time2, time2.plusMinutes(45), "UTC", "default");
        Long m3 = createMeeting("alice", "Mid Meeting", time3, time3.plusMinutes(60), "UTC", "default");

        inviteUser("alice", m1, "bob");
        inviteUser("alice", m2, "bob");
        inviteUser("alice", m3, "bob");

        // View pending invitations - should be sorted chronologically (m2 at 9:00, m3 at 11:00, m1 at 14:00)
        mockMvc.perform(get("/meetings/invitations/pending")
                .header("X-USERNAME", "bob"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].meetingId", is(m2.intValue())))
                .andExpect(jsonPath("$[0].meetingName", is("Early Meeting")))
                .andExpect(jsonPath("$[0].invitedBy", is("alice")))
                .andExpect(jsonPath("$[0].durationMinutes", is(45)))
                .andExpect(jsonPath("$[0].duration", is("45 minutes")))
                .andExpect(jsonPath("$[1].meetingId", is(m3.intValue())))
                .andExpect(jsonPath("$[1].durationMinutes", is(60)))
                .andExpect(jsonPath("$[1].duration", is("60 minutes")))
                .andExpect(jsonPath("$[2].meetingId", is(m1.intValue())))
                .andExpect(jsonPath("$[2].durationMinutes", is(90)))
                .andExpect(jsonPath("$[2].duration", is("90 minutes")));

        // Bob accepts m2
        mockMvc.perform(post("/meetings/" + m2 + "/invites/accept")
                .header("X-USERNAME", "bob"))
                .andExpect(status().isOk());

        // Bob rejects m3
        mockMvc.perform(post("/meetings/" + m3 + "/invites/reject")
                .header("X-USERNAME", "bob"))
                .andExpect(status().isOk());

        // Pending list now only contains m1
        mockMvc.perform(get("/meetings/invitations/pending")
                .header("X-USERNAME", "bob"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].meetingId", is(m1.intValue())));
    }

    @Test
    public void testConflictResolutionOnMeetingCreationAndAutoRejection() throws Exception {
        createUser("alice");

        createCalendar("alice", "low", 1.0);
        createCalendar("alice", "medium", 2.0);
        createCalendar("alice", "high", 3.0);

        LocalDateTime start = LocalDateTime.of(2026, 11, 1, 10, 0);
        LocalDateTime end = LocalDateTime.of(2026, 11, 1, 11, 0);

        // 1. Create meeting M_med in medium calendar (priority 2.0)
        Long mMedId = createMeeting("alice", "Medium Meeting", start, end, "UTC", "medium");

        // 2. Attempt to create conflicting meeting in medium calendar (priority 2.0 >= 2.0) -> fails
        CreateMeetingRequest mSame = new CreateMeetingRequest("Same Priority", start.plusMinutes(15), end.plusMinutes(15), "UTC", "medium");
        mockMvc.perform(post("/meetings")
                .header("X-USERNAME", "alice")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mSame)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Conflict with meeting 'Medium Meeting' in calendar 'medium' (priority: 2.0 >= 2.0)")));

        // 3. Attempt to create conflicting meeting in low calendar (priority 1.0 < 2.0) -> fails
        CreateMeetingRequest mLow = new CreateMeetingRequest("Low Priority", start.plusMinutes(15), end.plusMinutes(15), "UTC", "low");
        mockMvc.perform(post("/meetings")
                .header("X-USERNAME", "alice")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mLow)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Conflict with meeting 'Medium Meeting' in calendar 'medium' (priority: 2.0 >= 1.0)")));

        // 4. Create conflicting meeting in high calendar (priority 3.0 > 2.0) -> succeeds and auto-rejects M_med
        Long mHighId = createMeeting("alice", "High Priority", start.plusMinutes(15), end.plusMinutes(15), "UTC", "high");

        // 5. Verify M_med is AUTO_REJECTED and M_high is ACCEPTED
        mockMvc.perform(get("/meetings")
                .header("X-USERNAME", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + mMedId + ")].userStatus", contains("AUTO_REJECTED")))
                .andExpect(jsonPath("$[?(@.id == " + mHighId + ")].userStatus", contains("ACCEPTED")));

        // 6. Create non-conflicting meeting in low calendar (adjacent: 11:15 - 12:15) -> succeeds
        Long mAdjacentId = createMeeting("alice", "Adjacent Low Priority", end.plusMinutes(15), end.plusMinutes(75), "UTC", "low");
        mockMvc.perform(get("/meetings")
                .header("X-USERNAME", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + mAdjacentId + ")].userStatus", contains("ACCEPTED")));
    }

    @Test
    public void testConflictResolutionOnAcceptingInvitations() throws Exception {
        createUser("alice");
        createUser("bob");
        createUser("charlie");

        createCalendar("alice", "low", 1.0);
        createCalendar("charlie", "high", 3.0);
        createCalendar("alice", "med", 2.0);

        LocalDateTime start = LocalDateTime.of(2026, 12, 1, 14, 0);
        LocalDateTime end = LocalDateTime.of(2026, 12, 1, 15, 0);

        // Alice creates low priority meeting and invites Bob
        Long mLow = createMeeting("alice", "Alice Low", start, end, "UTC", "low");
        inviteUser("alice", mLow, "bob");

        // Bob accepts M_low -> Bob's status is ACCEPTED
        mockMvc.perform(post("/meetings/" + mLow + "/invites/accept")
                .header("X-USERNAME", "bob"))
                .andExpect(status().isOk());

        // Charlie creates high priority meeting at overlapping time and invites Bob
        Long mHigh = createMeeting("charlie", "Charlie High", start.plusMinutes(15), end.plusMinutes(15), "UTC", "high");
        inviteUser("charlie", mHigh, "bob");

        // Bob accepts M_high -> succeeds, Bob's M_low becomes AUTO_REJECTED, M_high is ACCEPTED
        mockMvc.perform(post("/meetings/" + mHigh + "/invites/accept")
                .header("X-USERNAME", "bob"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/meetings")
                .header("X-USERNAME", "bob"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + mLow + ")].userStatus", contains("AUTO_REJECTED")))
                .andExpect(jsonPath("$[?(@.id == " + mHigh + ")].userStatus", contains("ACCEPTED")));

        // Alice creates med priority meeting at overlapping time and invites Bob
        Long mMed = createMeeting("alice", "Alice Med", start.plusMinutes(20), end.plusMinutes(20), "UTC", "med");
        inviteUser("alice", mMed, "bob");

        // Bob tries to accept M_med -> blocked due to conflict with M_high (3.0 >= 2.0)
        mockMvc.perform(post("/meetings/" + mMed + "/invites/accept")
                .header("X-USERNAME", "bob"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Conflict with meeting 'Charlie High' in calendar 'high' (priority: 3.0 >= 2.0)")));
    }

    @Test
    public void testTimezoneAwareOverlaps() throws Exception {
        createUser("alice");
        createCalendar("alice", "high", 5.0);

        // Meeting 1: 15:00 - 16:00 in Tokyo (Asia/Tokyo is UTC+9, so 06:00 - 07:00 UTC)
        LocalDateTime tokyoStart = LocalDateTime.of(2026, 9, 15, 15, 0);
        LocalDateTime tokyoEnd = LocalDateTime.of(2026, 9, 15, 16, 0);
        Long tokyoMeeting = createMeeting("alice", "Tokyo Sync", tokyoStart, tokyoEnd, "Asia/Tokyo", "high");

        // Meeting 2: 06:30 - 07:30 in London (Europe/London in Sept is BST = UTC+1, so 05:30 - 06:30 UTC)
        // At 06:30 BST, it is 05:30 UTC -> ends at 06:30 UTC. Overlaps with 06:00 - 07:00 UTC (Tokyo Sync)!
        LocalDateTime londonStart = LocalDateTime.of(2026, 9, 15, 6, 30);
        LocalDateTime londonEnd = LocalDateTime.of(2026, 9, 15, 7, 30);
        CreateMeetingRequest londonRequest = new CreateMeetingRequest("London Sync", londonStart, londonEnd, "Europe/London", "high");

        mockMvc.perform(post("/meetings")
                .header("X-USERNAME", "alice")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(londonRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Conflict with meeting 'Tokyo Sync'")));
    }

    @Test
    public void testEntityModelGettersAndSetters() {
        me.mourjo.prompt.meetings.model.Calendar cal1 = new me.mourjo.prompt.meetings.model.Calendar();
        cal1.setName("test-cal");
        cal1.setPriority(4.5);
        assertEquals("test-cal", cal1.getName());
        assertEquals(4.5, cal1.getPriority());

        me.mourjo.prompt.meetings.model.Calendar cal2 = new me.mourjo.prompt.meetings.model.Calendar("work", 2.0);
        assertEquals("work", cal2.getName());
        assertEquals(2.0, cal2.getPriority());

        LocalDateTime now = LocalDateTime.now();
        me.mourjo.prompt.meetings.model.Meeting meeting = new me.mourjo.prompt.meetings.model.Meeting();
        meeting.setId(100L);
        meeting.setTitle("Test Title");
        meeting.setStartTime(now);
        meeting.setEndTime(now.plusHours(1));
        meeting.setTimezone("UTC");
        meeting.setOrganizerUsername("alice");
        meeting.setCalendarName("default");

        assertEquals(100L, meeting.getId());
        assertEquals("Test Title", meeting.getTitle());
        assertEquals(now, meeting.getStartTime());
        assertEquals(now.plusHours(1), meeting.getEndTime());
        assertEquals("UTC", meeting.getTimezone());
        assertEquals("alice", meeting.getOrganizerUsername());
        assertEquals("default", meeting.getCalendarName());
    }

    @Test
    public void testGlobalExceptionHandlerDirectly() {
        me.mourjo.prompt.meetings.exception.GlobalExceptionHandler handler = new me.mourjo.prompt.meetings.exception.GlobalExceptionHandler();

        var genericRes = handler.handleGeneric(new RuntimeException("Unexpected error"));
        assertEquals(500, genericRes.getStatusCode().value());
        assertEquals("Unexpected error", genericRes.getBody().get("message"));

        var notFoundRes = handler.handleNotFound(new me.mourjo.prompt.meetings.exception.NotFoundException("Not found"));
        assertEquals(404, notFoundRes.getStatusCode().value());

        var badReqRes = handler.handleBadRequest(new me.mourjo.prompt.meetings.exception.BadRequestException("Bad req"));
        assertEquals(400, badReqRes.getStatusCode().value());

        var unauthRes = handler.handleUnauthorized(new me.mourjo.prompt.meetings.exception.UnauthorizedException("Unauth"));
        assertEquals(401, unauthRes.getStatusCode().value());

        var forbiddenRes = handler.handleForbidden(new me.mourjo.prompt.meetings.exception.ForbiddenException("Forbidden"));
        assertEquals(403, forbiddenRes.getStatusCode().value());
    }
}
