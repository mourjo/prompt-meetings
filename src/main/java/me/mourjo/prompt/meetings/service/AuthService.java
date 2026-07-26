package me.mourjo.prompt.meetings.service;

import me.mourjo.prompt.meetings.exception.UnauthorizedException;
import me.mourjo.prompt.meetings.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserRepository userRepository;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public void authenticate(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new UnauthorizedException("Authentication header X-USERNAME is missing or empty");
        }
        if (!userRepository.existsByUsername(username)) {
            throw new UnauthorizedException("User '" + username + "' is not registered in the system");
        }
    }
}
