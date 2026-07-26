package me.mourjo.prompt.meetings.service;

import me.mourjo.prompt.meetings.dto.CalendarResponse;
import me.mourjo.prompt.meetings.exception.BadRequestException;
import me.mourjo.prompt.meetings.repository.CalendarRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CalendarService {
    private final CalendarRepository calendarRepository;

    public CalendarService(CalendarRepository calendarRepository) {
        this.calendarRepository = calendarRepository;
    }

    public CalendarResponse createCalendar(String xUsername, String name, double priority) {
        try {
            calendarRepository.insertCalendar(name, priority);
        } catch (Exception e) {
            throw new BadRequestException("Calendar already exists with name: " + name);
        }

        return new CalendarResponse(name, priority);
    }

    public List<CalendarResponse> getAllCalendars(String xUsername) {
        return calendarRepository.findAllSorted().stream()
                .map(c -> new CalendarResponse(c.getName(), c.getPriority()))
                .collect(Collectors.toList());
    }
}
