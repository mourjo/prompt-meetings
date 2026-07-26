CREATE TABLE calendars (
    name VARCHAR(100) NOT NULL PRIMARY KEY,
    priority DOUBLE NOT NULL
);

INSERT INTO calendars (name, priority) VALUES ('default', 1.0);

ALTER TABLE meetings ADD COLUMN calendar_name VARCHAR(100) DEFAULT 'default' NOT NULL;

ALTER TABLE meetings ADD CONSTRAINT fk_meetings_calendar FOREIGN KEY (calendar_name) REFERENCES calendars(name) ON DELETE CASCADE;
