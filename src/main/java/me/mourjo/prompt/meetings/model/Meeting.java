package me.mourjo.prompt.meetings.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("MEETINGS")
public class Meeting {
    @Id
    @Column("ID")
    private Long id;

    @Column("TITLE")
    private String title;

    @Column("START_TIME")
    private LocalDateTime startTime;

    @Column("END_TIME")
    private LocalDateTime endTime;

    @Column("TIMEZONE")
    private String timezone;

    @Column("ORGANIZER_USERNAME")
    private String organizerUsername;

    @Column("CALENDAR_NAME")
    private String calendarName;

    public Meeting() {}

    public Meeting(String title, LocalDateTime startTime, LocalDateTime endTime, String timezone, String organizerUsername, String calendarName) {
        this.title = title;
        this.startTime = startTime;
        this.endTime = endTime;
        this.timezone = timezone;
        this.organizerUsername = organizerUsername;
        this.calendarName = calendarName;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public String getOrganizerUsername() { return organizerUsername; }
    public void setOrganizerUsername(String organizerUsername) { this.organizerUsername = organizerUsername; }
    public String getCalendarName() { return calendarName; }
    public void setCalendarName(String calendarName) { this.calendarName = calendarName; }
}
