package me.mourjo.prompt.meetings.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("CALENDARS")
public class Calendar {
    @Id
    @Column("NAME")
    private String name;

    @Column("PRIORITY")
    private double priority;

    public Calendar() {}

    public Calendar(String name, double priority) {
        this.name = name;
        this.priority = priority;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public double getPriority() { return priority; }
    public void setPriority(double priority) { this.priority = priority; }
}
