CREATE TABLE users (
    username VARCHAR(100) NOT NULL PRIMARY KEY
);

CREATE TABLE meetings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,
    timezone VARCHAR(100) NOT NULL,
    organizer_username VARCHAR(100) NOT NULL,
    CONSTRAINT fk_meetings_organizer FOREIGN KEY (organizer_username) REFERENCES users(username) ON DELETE CASCADE
);

CREATE TABLE invitations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    meeting_id BIGINT NOT NULL,
    invitee_username VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL, -- PENDING, ACCEPTED, REJECTED
    CONSTRAINT fk_invitations_meeting FOREIGN KEY (meeting_id) REFERENCES meetings(id) ON DELETE CASCADE,
    CONSTRAINT fk_invitations_invitee FOREIGN KEY (invitee_username) REFERENCES users(username) ON DELETE CASCADE,
    CONSTRAINT uq_invitations_meeting_invitee UNIQUE (meeting_id, invitee_username)
);
