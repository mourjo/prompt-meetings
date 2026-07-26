package me.mourjo.prompt.meetings;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import me.mourjo.prompt.meetings.dto.CreateMeetingRequest;
import me.mourjo.prompt.meetings.dto.MeetingResponse;
import me.mourjo.prompt.meetings.model.Meeting;
import me.mourjo.prompt.meetings.repository.CalendarRepository;
import me.mourjo.prompt.meetings.repository.MeetingRepository;
import me.mourjo.prompt.meetings.repository.UserRepository;
import me.mourjo.prompt.meetings.service.CalendarService;
import me.mourjo.prompt.meetings.service.MeetingService;
import me.mourjo.prompt.meetings.service.UserService;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.Disabled;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.lifecycle.BeforeTry;
import net.jqwik.api.state.Action;
import net.jqwik.api.state.ActionChain;
import net.jqwik.api.state.Transformer;
import net.jqwik.spring.JqwikSpringSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@JqwikSpringSupport
@SpringBootTest
public class MeetingSchedulerPropertyTests {

    private static final List<String> USERS = List.of("user1", "user2", "user3", "user4");
    private static final List<String> CALENDARS = List.of("default", "medium", "high");
    private static final List<String> TIMEZONES = List.of("UTC");
    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 9, 10, 10, 0);
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

    private static boolean overlaps(MeetingResponse meeting1, MeetingResponse meeting2) {
        ZonedDateTime start1 = meeting1.startTime().atZone(ZoneId.of(meeting1.timezone()));
        ZonedDateTime end1 = meeting1.endTime().atZone(ZoneId.of(meeting1.timezone()));
        ZonedDateTime start2 = meeting2.startTime().atZone(ZoneId.of(meeting2.timezone()));
        ZonedDateTime end2 = meeting2.endTime().atZone(ZoneId.of(meeting2.timezone()));
        return start1.isBefore(end2) && start2.isBefore(end1);
    }

    @Disabled
    @Property
    void noOverlappingMeetingsForAnyUser(@ForAll("actions") ActionChain<CalendarMeetingsState> chain) {
        chain.withInvariant("no-overlap", sut -> {
            for (String user : USERS) {
                List<MeetingResponse> meetings = meetingService.getMyMeetings(user).stream()
                    .filter(m -> "ACCEPTED".equalsIgnoreCase(m.userStatus()))
                    .toList();
                for (int i = 0; i < meetings.size(); i++) {
                    for (int j = i + 1; j < meetings.size(); j++) {
                        MeetingResponse meeting1 = meetings.get(i);
                        MeetingResponse meeting2 = meetings.get(j);
                        if (overlaps(meeting1, meeting2)) {
                            throw new AssertionError(
                                "User %s is in overlapping meetings: %s [%s - %s] and %s [%s - %s]".formatted(
                                    user,
                                    meeting1.title(), meeting1.startTime().toLocalTime(), meeting1.endTime().toLocalTime(),
                                    meeting2.title(), meeting2.startTime().toLocalTime(), meeting2.endTime().toLocalTime()
                                )
                            );
                        }
                    }
                }
            }
        }).run();
    }

    private double getPriority(MeetingResponse meeting) {
        Double p = calendarRepository.getPriority(meeting.calendarName());
        return p != null ? p : 1.0;
    }

    @Disabled
    @Property
    void autoRejectedMeetingsHaveHigherPriorityAcceptedOverlap(@ForAll("actions") ActionChain<CalendarMeetingsState> chain) {
        chain.withInvariant("auto-rejected-invariant", sut -> {
            for (String user : USERS) {
                List<MeetingResponse> myMeetings = meetingService.getMyMeetings(user);
                
                List<MeetingResponse> autoRejected = myMeetings.stream()
                    .filter(m -> "AUTO_REJECTED".equalsIgnoreCase(m.userStatus()))
                    .toList();
                
                List<MeetingResponse> accepted = myMeetings.stream()
                    .filter(m -> "ACCEPTED".equalsIgnoreCase(m.userStatus()))
                    .toList();

                for (MeetingResponse ar : autoRejected) {
                    double arPriority = getPriority(ar);
                    boolean foundOverlap = false;
                    for (MeetingResponse acc : accepted) {
                        if (overlaps(ar, acc) && getPriority(acc) > arPriority) {
                            foundOverlap = true;
                            break;
                        }
                    }
                    if (!foundOverlap) {
                        throw new AssertionError(
                            "User %s has an AUTO_REJECTED meeting '%s' (priority %f) but no overlapping ACCEPTED meeting with a higher priority"
                            .formatted(user, ar.title(), arPriority)
                        );
                    }
                }
            }
        }).run();
    }

    @Provide
    Arbitrary<ActionChain<CalendarMeetingsState>> actions() {
        return ActionChain.startWith(CalendarMeetingsState::new)
            .withAction(new CreateMeetingAction())
            .withAction(new InviteUserAction())
            .withAction(new AcceptInviteAction())
            .withAction(new RejectMeetingAction());
    }

    public static class CalendarMeetingsState {

        public static AtomicInteger nextId = new AtomicInteger(0);
        public static Map<Integer, Long> meetingLookup = new HashMap<>();

        public static void runSilently(Runnable action) {
            try {
                action.run();
            } catch (Exception ignored) {
                // Exception suppressed
            }
        }
    }

    private class AcceptInviteAction implements Action.Independent<CalendarMeetingsState> {

        @Override
        public Arbitrary<Transformer<CalendarMeetingsState>> transformer() {
            return Combinators.combine(
                Arbitraries.of(USERS),
                Arbitraries.integers().between(0, CalendarMeetingsState.nextId.get())
            ).as((user, randIndex) -> new Transformer<>() {
                @Override
                public CalendarMeetingsState apply(CalendarMeetingsState state) {
                    meetingRepository.findById(CalendarMeetingsState.meetingLookup.getOrDefault(randIndex, -1001L))
                        .ifPresent(meeting -> CalendarMeetingsState.runSilently(() ->
                            meetingService.acceptInvite(user, meeting.getId())));
                    return state;
                }

                @Override
                public String toString() {
                    Optional<Meeting> meeting = meetingRepository.findById(CalendarMeetingsState.meetingLookup.getOrDefault(randIndex, -1001L));
                    if (meeting.isPresent()) {
                        return "%s accepts invitation to meeting %s".formatted(user, meeting.get().getTitle());
                    }
                    return "accept-invitation-no-op";
                }
            });
        }

        @Override
        public String toString() {
            return "accept-invitation";
        }
    }

    private class InviteUserAction implements Action.Independent<CalendarMeetingsState> {

        @Override
        public Arbitrary<Transformer<CalendarMeetingsState>> transformer() {
            return Combinators.combine(
                Arbitraries.of(USERS),
                Arbitraries.of(USERS),
                Arbitraries.integers().between(0, CalendarMeetingsState.nextId.get())
            ).as((inviter, invitee, meetingIdx) -> new Transformer<>() {
                @Override
                public CalendarMeetingsState apply(CalendarMeetingsState state) {
                    if (!invitee.equals(inviter)) {
                        meetingRepository.findById(CalendarMeetingsState.meetingLookup.getOrDefault(meetingIdx, -1001L)).ifPresent(
                            meeting -> CalendarMeetingsState.runSilently(() ->
                                meetingService.inviteUser(inviter, meeting.getId(), invitee))
                        );
                    }
                    return state;
                }

                @Override
                public String toString() {
                    if (!invitee.equals(inviter)) {
                        Optional<Meeting> meeting = meetingRepository.findById(CalendarMeetingsState.meetingLookup.getOrDefault(meetingIdx, -1001L));
                        if (meeting.isPresent()) {
                            return "%s invites %s to %s".formatted(inviter, invitee, meeting.get().getTitle());
                        }
                    }
                    return "invite-user-no-op";
                }
            });
        }

        @Override
        public String toString() {
            return "invite-user";
        }
    }

    private class RejectMeetingAction implements Action.Independent<CalendarMeetingsState> {

        @Override
        public Arbitrary<Transformer<CalendarMeetingsState>> transformer() {
            return Combinators.combine(
                Arbitraries.of(USERS),
                Arbitraries.integers().between(0, CalendarMeetingsState.nextId.get())
            ).as((user, randIndex) -> new Transformer<>() {
                @Override
                public CalendarMeetingsState apply(CalendarMeetingsState state) {
                    meetingRepository.findById(CalendarMeetingsState.meetingLookup.getOrDefault(randIndex, -1001L))
                        .ifPresent(meeting -> CalendarMeetingsState.runSilently(() ->
                            meetingService.rejectInvite(user, meeting.getId()))
                        );
                    return state;
                }

                @Override
                public String toString() {
                    Optional<Meeting> meeting = meetingRepository.findById(CalendarMeetingsState.meetingLookup.getOrDefault(randIndex, -1001L));
                    if (meeting.isPresent()) {
                        return "%s rejects invitation to %s".formatted(user, meeting.get().getTitle());
                    }
                    return "reject-invitation-no-op";
                }
            });
        }

        @Override
        public String toString() {
            return "reject-invitation";
        }
    }

    private class CreateMeetingAction implements Action.Independent<CalendarMeetingsState> {

        @Override
        public Arbitrary<Transformer<CalendarMeetingsState>> transformer() {
            return Combinators.combine(
                Arbitraries.of(USERS),
                Arbitraries.of(CALENDARS),
                Arbitraries.integers().between(0, 360),
                Arbitraries.integers().between(1, 60),
                Arbitraries.of(TIMEZONES),
                Arbitraries.just(CalendarMeetingsState.nextId.get())
            ).as(this::applyMeetingCreationSideEffects);
        }

        private Transformer<CalendarMeetingsState> applyMeetingCreationSideEffects(String organizer, String cal, Integer offset, Integer duration, String tz,
            Integer sequentialId) {
            return new Transformer<>() {

                @Override
                public CalendarMeetingsState apply(CalendarMeetingsState state) {
                    var start = BASE_TIME.plusMinutes(offset);
                    var end = start.plusMinutes(duration);
                    var req = new CreateMeetingRequest("Meeting-" + sequentialId, start, end, tz, cal);

                    CalendarMeetingsState.runSilently(() -> {
                        var resp = meetingService.createMeeting(organizer, req);
                        var meeting = meetingRepository.findById(resp.id()).get();
                        CalendarMeetingsState.meetingLookup.put(sequentialId, meeting.getId());
                        CalendarMeetingsState.nextId.incrementAndGet();
                    });

                    return state;
                }

                @Override
                public String toString() {
                    if (CalendarMeetingsState.meetingLookup.containsKey(sequentialId)) {
                        var start = BASE_TIME.plusMinutes(offset);
                        var end = start.plusMinutes(duration);
                        return "%s creates Meeting-%s in calendar %s (%s) from %s to %s"
                            .formatted(organizer, sequentialId, cal, tz, start.toLocalTime(), end.toLocalTime());
                    }
                    return "create-meeting-no-op";
                }
            };
        }

        @Override
        public String toString() {
            return "create-meeting";
        }
    }

    @BeforeTry
    public void setUp() {
        jdbcTemplate.execute("DELETE FROM invitations");
        jdbcTemplate.execute("DELETE FROM meetings");
        jdbcTemplate.execute("DELETE FROM users");
        jdbcTemplate.execute("DELETE FROM calendars");
        CalendarMeetingsState.meetingLookup = new HashMap<>();
        CalendarMeetingsState.nextId = new AtomicInteger(0);

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
}
