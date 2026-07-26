package me.mourjo.prompt.meetings.repository;

import me.mourjo.prompt.meetings.model.Calendar;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CalendarRepository extends ListCrudRepository<Calendar, String> {

    @Query("SELECT priority FROM calendars WHERE name = :name")
    Double getPriority(@Param("name") String name);

    @Query("SELECT name, priority FROM calendars ORDER BY priority ASC, name ASC")
    List<Calendar> findAllSorted();

    @Modifying
    @Query("INSERT INTO calendars (name, priority) VALUES (:name, :priority)")
    void insertCalendar(@Param("name") String name, @Param("priority") double priority);
}
