package com.taskflow.controller;

import com.taskflow.ai.VectorSyncService;
import com.taskflow.config.SecurityUtils;
import com.taskflow.dto.AiDtos.ReindexResult;
import com.taskflow.dto.UserDtos.AdminUpdateUserRequest;
import com.taskflow.dto.UserDtos.UserDto;
import com.taskflow.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Endpoints exclusivos de ADMIN (protegidos en SecurityConfig: /api/admin/**). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserService userService;
    private final VectorSyncService vectorSyncService;

    public AdminController(UserService userService, VectorSyncService vectorSyncService) {
        this.userService = userService;
        this.vectorSyncService = vectorSyncService;
    }

    @GetMapping("/users")
    public List<UserDto> users() {
        return userService.listAll();
    }

    @PutMapping("/users/{id}")
    public UserDto updateUser(@PathVariable Long id, @Valid @RequestBody AdminUpdateUserRequest req) {
        return userService.adminUpdate(id, req, SecurityUtils.currentUser());
    }

    /** Recrea la colección de Qdrant y regenera los embeddings de todas las tareas. */
    @PostMapping("/ai/reindex")
    public ReindexResult reindex() {
        return vectorSyncService.reindexAll();
    }
}
