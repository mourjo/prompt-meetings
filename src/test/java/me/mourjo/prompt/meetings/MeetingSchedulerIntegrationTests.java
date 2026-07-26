package me.mourjo.prompt.meetings;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.mourjo.prompt.meetings.dto.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class MeetingSchedulerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

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
}
