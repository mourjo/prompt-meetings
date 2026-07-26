package me.mourjo.prompt.meetings;

import me.mourjo.prompt.meetings.dto.*;
import me.mourjo.prompt.meetings.model.Meeting;
import me.mourjo.prompt.meetings.repository.*;
import me.mourjo.prompt.meetings.service.*;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeTry;
import net.jqwik.api.state.*;
import net.jqwik.spring.JqwikSpringSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;

@JqwikSpringSupport
@SpringBootTest
public class MeetingSchedulerPropertyTests {

    @Autowired
    private UserService userService;

    @Autowired
    private CalendarService calendarService;

    @Autowired
    private MeetingService meetingService;

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CalendarRepository calendarRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final List<String> USERS = List.of("user1", "user2", "user3", "user4");
    private static final List<String> CALENDARS = List.of("default", "medium", "high");

    @BeforeTry
    public void setUp() {
        jdbcTemplate.execute("DELETE FROM invitations");
        jdbcTemplate.execute("DELETE FROM meetings");
        jdbcTemplate.execute("DELETE FROM users");
        jdbcTemplate.execute("DELETE FROM calendars");

        for (String user : USERS) {
            userService.createUser(user);
        }
        // Seed default calendar (priority 1.0)
        jdbcTemplate.execute("INSERT INTO calendars (name, priority) VALUES ('default', 1.0)");
        // Seed other calendars
        for (String cal : CALENDARS) {
            if (!cal.equals("default")) {
                double priority = cal.equals("medium") ? 2.0 : 3.0;
                calendarService.createCalendar("user1", cal, priority);
            }
        }
    }

    public static class SUTState {
    }

    @Property
    void noOverlappingMeetingsForAnyUser(@ForAll("actions") ActionChain<SUTState> chain) {
        chain.withInvariant("no-overlap", sut -> {
            for (String user : USERS) {
                List<MeetingResponse> meetings = meetingService.getMyMeetings(user).stream()
                        .filter(m -> "ACCEPTED".equalsIgnoreCase(m.userStatus()))
                        .collect(Collectors.toList());
                for (int i = 0; i < meetings.size(); i++) {
                    for (int j = i + 1; j < meetings.size(); j++) {
                        MeetingResponse m1 = meetings.get(i);
                        MeetingResponse m2 = meetings.get(j);
                        ZonedDateTime start1 = m1.startTime().atZone(ZoneId.of(m1.timezone()));
                        ZonedDateTime end1 = m1.endTime().atZone(ZoneId.of(m1.timezone()));
                        ZonedDateTime start2 = m2.startTime().atZone(ZoneId.of(m2.timezone()));
                        ZonedDateTime end2 = m2.endTime().atZone(ZoneId.of(m2.timezone()));
                        if (start1.isBefore(end2) && start2.isBefore(end1)) {
                            throw new AssertionError("User " + user + " is in overlapping meetings: " +
                                    m1.title() + " [" + start1 + " - " + end1 + "] and " +
                                    m2.title() + " [" + start2 + " - " + end2 + "]");
                        }
                    }
                }
            }
        }).run();
    }

    @Provide
    Arbitrary<ActionChain<SUTState>> actions() {
        return ActionChain.startWith(SUTState::new)
                .withAction(createMeetingAction())
                .withAction(inviteUserAction())
                .withAction(acceptInvitationAction())
                .withAction(rejectInvitationAction());
    }

    private Action.Independent<SUTState> createMeetingAction() {
        return new Action.Independent<>() {
            @Override
            public Arbitrary<Transformer<SUTState>> transformer() {
                return Combinators.combine(
                        Arbitraries.of(USERS),
                        Arbitraries.of(CALENDARS),
                        Arbitraries.of(10, 11, 12),
                        Arbitraries.of("Europe/Paris", "UTC")
                ).as((organizer, cal, hour, tz) -> new Transformer<SUTState>() {
                    @Override
                    public SUTState apply(SUTState state) {
                        LocalDateTime start = LocalDateTime.of(2026, 8, 1, hour, 0);
                        LocalDateTime end = start.plusHours(1);

                        CreateMeetingRequest req = new CreateMeetingRequest("Meeting-" + hour, start, end, tz, cal);
                        try {
                            meetingService.createMeeting(organizer, req);
                        } catch (Exception e) {
                            // Expected validation/conflict failures
                        }
                        return state;
                    }

                    @Override
                    public String toString() {
                        return organizer + " creates meeting 'Meeting-" + hour + "' in calendar '" + cal + "' (" + tz + " timezone)";
                    }
                });
            }

            @Override
            public String toString() {
                return "create-meeting";
            }
        };
    }

    private Action.Independent<SUTState> inviteUserAction() {
        return new Action.Independent<>() {
            @Override
            public Arbitrary<Transformer<SUTState>> transformer() {
                return Combinators.combine(
                        Arbitraries.of(USERS),
                        Arbitraries.of(USERS),
                        Arbitraries.integers().greaterOrEqual(0)
                ).as((inviter, invitee, randIndex) -> new Transformer<SUTState>() {
                    @Override
                    public SUTState apply(SUTState state) {
                        List<Meeting> meetings = meetingRepository.findAll();
                        if (!meetings.isEmpty()) {
                            Meeting meeting = meetings.get(randIndex % meetings.size());
                            try {
                                meetingService.inviteUser(inviter, meeting.getId(), invitee);
                            } catch (Exception e) {
                                // Expected failures
                            }
                        }
                        return state;
                    }

                    @Override
                    public String toString() {
                        List<Meeting> meetings = meetingRepository.findAll();
                        if (!meetings.isEmpty()) {
                            Meeting meeting = meetings.get(randIndex % meetings.size());
                            return inviter + " invites " + invitee + " to meeting '" + meeting.getTitle() + "' (ID: " + meeting.getId() + ")";
                        }
                        return inviter + " invites " + invitee + " (no meetings exist)";
                    }
                });
            }

            @Override
            public String toString() {
                return "invite-user";
            }
        };
    }

    private Action.Independent<SUTState> acceptInvitationAction() {
        return new Action.Independent<>() {
            @Override
            public Arbitrary<Transformer<SUTState>> transformer() {
                return Combinators.combine(
                        Arbitraries.of(USERS),
                        Arbitraries.integers().greaterOrEqual(0)
                ).as((user, randIndex) -> new Transformer<SUTState>() {
                    @Override
                    public SUTState apply(SUTState state) {
                        List<Meeting> meetings = meetingRepository.findAll();
                        if (!meetings.isEmpty()) {
                            Meeting meeting = meetings.get(randIndex % meetings.size());
                            try {
                                meetingService.acceptInvite(user, meeting.getId());
                            } catch (Exception e) {
                                // Expected failures
                            }
                        }
                        return state;
                    }

                    @Override
                    public String toString() {
                        List<Meeting> meetings = meetingRepository.findAll();
                        if (!meetings.isEmpty()) {
                            Meeting meeting = meetings.get(randIndex % meetings.size());
                            return user + " accepts invitation to meeting '" + meeting.getTitle() + "' (ID: " + meeting.getId() + ")";
                        }
                        return user + " accepts invitation (no meetings exist)";
                    }
                });
            }

            @Override
            public String toString() {
                return "accept-invitation";
            }
        };
    }

    private Action.Independent<SUTState> rejectInvitationAction() {
        return new Action.Independent<>() {
            @Override
            public Arbitrary<Transformer<SUTState>> transformer() {
                return Combinators.combine(
                        Arbitraries.of(USERS),
                        Arbitraries.integers().greaterOrEqual(0)
                ).as((user, randIndex) -> new Transformer<SUTState>() {
                    @Override
                    public SUTState apply(SUTState state) {
                        List<Meeting> meetings = meetingRepository.findAll();
                        if (!meetings.isEmpty()) {
                            Meeting meeting = meetings.get(randIndex % meetings.size());
                            try {
                                meetingService.rejectInvite(user, meeting.getId());
                            } catch (Exception e) {
                                // Expected failures
                            }
                        }
                        return state;
                    }

                    @Override
                    public String toString() {
                        List<Meeting> meetings = meetingRepository.findAll();
                        if (!meetings.isEmpty()) {
                            Meeting meeting = meetings.get(randIndex % meetings.size());
                            return user + " rejects invitation to meeting '" + meeting.getTitle() + "' (ID: " + meeting.getId() + ")";
                        }
                        return user + " rejects invitation (no meetings exist)";
                    }
                });
            }

            @Override
            public String toString() {
                return "reject-invitation";
            }
        };
    }
}
