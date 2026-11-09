package com.taskflow.service;

import com.taskflow.dto.TaskDtos.TaskDto;
import com.taskflow.dto.UserDtos.UserSummary;
import com.taskflow.entity.Task;
import com.taskflow.entity.User;

import java.time.LocalDate;

public final class TaskMapper {
    private TaskMapper() {}

    public static TaskDto toDto(Task t, User viewer, LocalDate today) {
        boolean canEdit = viewer != null && t.getProject().hasAccess(viewer);
        return new TaskDto(t.getId(), t.getTitle(), t.getDescription(), t.getStatus(), t.getPriority(),
                t.getDueDate(), t.getProject().getId(), t.getProject().getName(),
                UserSummary.from(t.getAssignee()), UserSummary.from(t.getCreatedBy()),
                t.isOverdue(today), t.isIndexed(), canEdit,
                t.getCreatedAt(), t.getUpdatedAt(), t.getCompletedAt());
    }
}
