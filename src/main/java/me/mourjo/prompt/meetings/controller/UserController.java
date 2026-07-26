package me.mourjo.prompt.meetings.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import me.mourjo.prompt.meetings.dto.CreateUserRequest;
import me.mourjo.prompt.meetings.dto.UserResponse;
import me.mourjo.prompt.meetings.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
@Tag(name = "Users", description = "Endpoints for managing users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new user with a unique username")
    public UserResponse createUser(@Valid @RequestBody CreateUserRequest request) {
        return userService.createUser(request.username());
    }

    @GetMapping
    @Operation(summary = "View all users in the system")
    public List<UserResponse> getAllUsers() {
        return userService.getAllUsers();
    }
}
