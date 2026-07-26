package me.mourjo.prompt.meetings;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.mourjo.prompt.meetings.dto.CreateMeetingRequest;
import me.mourjo.prompt.meetings.dto.CreateUserRequest;
import me.mourjo.prompt.meetings.dto.InviteUserRequest;
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
        CreateMeetingRequest meetingRequest = new CreateMeetingRequest("Project Sync", start, end, "Europe/Paris");

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

        // 7. Create meeting successfully (alice is organizer)
        String meetingResponseJson = mockMvc.perform(post("/meetings")
                .header("X-USERNAME", "alice")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(meetingRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.title", is("Project Sync")))
                .andExpect(jsonPath("$.organizerUsername", is("alice")))
                .andExpect(jsonPath("$.userStatus", is("ORGANIZER")))
                .andReturn().getResponse().getContentAsString();

        Long meetingId = objectMapper.readTree(meetingResponseJson).get("id").asLong();

        // 8. Fail to create meeting with invalid timezone
        CreateMeetingRequest badTzRequest = new CreateMeetingRequest("Project Sync", start, end, "Invalid/Timezone");
        mockMvc.perform(post("/meetings")
                .header("X-USERNAME", "alice")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(badTzRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Invalid timezone")));

        // 9. Fail to create meeting with start time after end time
        CreateMeetingRequest badTimeRequest = new CreateMeetingRequest("Project Sync", end, start, "Europe/Paris");
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
                .andExpect(jsonPath("$[0].userStatus", is("ACCEPTED")));

        // 19. View Alice's meetings (should show ORGANIZER)
        mockMvc.perform(get("/meetings")
                .header("X-USERNAME", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(meetingId.intValue())))
                .andExpect(jsonPath("$[0].userStatus", is("ORGANIZER")));
    }
}
