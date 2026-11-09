package com.taskflow.dto;

import com.taskflow.entity.Priority;
import com.taskflow.entity.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;

public final class TaskDtos {
    private TaskDtos() {}

    public record TaskRequest(
            @NotBlank(message = "El título es obligatorio") @Size(max = 200) String title,
            @Size(max = 5000) String description,
            TaskStatus status,
            Priority priority,
            LocalDate dueDate,
            @NotNull(message = "El proyecto es obligatorio") Long projectId,
            Long assigneeId) {}

    public record StatusUpdateRequest(@NotNull(message = "El estado es obligatorio") TaskStatus status) {}

    public record TaskDto(Long id, String title, String description, TaskStatus status, Priority priority,
                          LocalDate dueDate, Long projectId, String projectName,
                          UserDtos.UserSummary assignee, UserDtos.UserSummary createdBy,
                          boolean overdue, boolean indexed, boolean canEdit,
                          Instant createdAt, Instant updatedAt, Instant completedAt) {}

    public record CommentRequest(
            @NotBlank(message = "El comentario no puede estar vacío") @Size(max = 2000) String content) {}

    public record CommentDto(Long id, String content, UserDtos.UserSummary author, boolean canDelete, Instant createdAt) {}
}
