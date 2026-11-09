package com.taskflow.dto;

import java.util.List;
import java.util.Map;

public record DashboardDto(
        long totalProjects,
        long totalTasks,
        long pending,
        long inProgress,
        long completed,
        long overdue,
        long dueSoon,
        int completionRate,
        long myOpenTasks,
        long unreadNotifications,
        Map<String, Long> byPriority,
        List<ProjectProgress> projects,
        List<TaskDtos.TaskDto> upcoming,
        List<TaskDtos.TaskDto> overdueTasks) {

    public record ProjectProgress(Long id, String name, long total, long completed, int progress) {}
}
