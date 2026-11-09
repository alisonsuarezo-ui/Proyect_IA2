package com.taskflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class ProjectDtos {
    private ProjectDtos() {}

    public record ProjectRequest(
            @NotBlank(message = "El nombre del proyecto es obligatorio") @Size(max = 150) String name,
            @Size(max = 2000) String description,
            List<Long> memberIds) {}

    public record ProjectDto(Long id, String name, String description,
                             UserDtos.UserSummary owner, List<UserDtos.UserSummary> members,
                             long totalTasks, long completedTasks, int progress,
                             boolean canManage, Instant createdAt, Instant updatedAt) {}
}
