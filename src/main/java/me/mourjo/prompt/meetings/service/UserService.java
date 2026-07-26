package me.mourjo.prompt.meetings.service;

import me.mourjo.prompt.meetings.dto.UserResponse;
import me.mourjo.prompt.meetings.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserResponse createUser(String username) {
        userRepository.save(username);
        return new UserResponse(username);
    }

    public List<UserResponse> getAllUsers() {
        return userRepository.findAllUsernames().stream()
                .map(UserResponse::new)
                .collect(Collectors.toList());
    }
}
