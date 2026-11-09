package com.taskflow.controller;

import com.taskflow.config.SecurityUtils;
import com.taskflow.dto.UserDtos.UpdateProfileRequest;
import com.taskflow.dto.UserDtos.UserDto;
import com.taskflow.dto.UserDtos.UserSummary;
import com.taskflow.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserDto me() {
        return userService.me(SecurityUtils.currentUser());
    }

    @PutMapping("/me")
    public UserDto updateMe(@Valid @RequestBody UpdateProfileRequest req) {
        return userService.updateProfile(SecurityUtils.currentUser(), req);
    }

    /** Directorio de usuarios activos para asignar tareas / añadir miembros. */
    @GetMapping
    public List<UserSummary> directory() {
        return userService.directory();
    }
}
