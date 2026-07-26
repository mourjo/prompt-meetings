package me.mourjo.prompt.meetings.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import me.mourjo.prompt.meetings.dto.CreateUserRequest;
import me.mourjo.prompt.meetings.dto.UserResponse;
import me.mourjo.prompt.meetings.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/users")
@Tag(name = "Users", description = "Endpoints for managing users")
public class UserController {
    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new user with a unique username")
    public UserResponse createUser(@Valid @RequestBody CreateUserRequest request) {
        userRepository.save(request.username());
        return new UserResponse(request.username());
    }

    @GetMapping
    @Operation(summary = "View all users in the system")
    public List<UserResponse> getAllUsers() {
        return userRepository.findAllUsernames().stream()
            .map(UserResponse::new)
            .collect(Collectors.toList());
    }
}
