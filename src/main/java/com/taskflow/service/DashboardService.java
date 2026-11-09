package com.taskflow.service;

import com.taskflow.dto.DashboardDto;
import com.taskflow.dto.DashboardDto.ProjectProgress;
import com.taskflow.entity.*;
import com.taskflow.repository.NotificationRepository;
import com.taskflow.repository.ProjectRepository;
import com.taskflow.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private final AccessService accessService;
    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final NotificationRepository notificationRepository;
    private final Clock clock;

    public DashboardService(AccessService accessService, TaskRepository taskRepository,
                            ProjectRepository projectRepository, NotificationRepository notificationRepository,
                            Clock clock) {
        this.accessService = accessService;
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
        this.notificationRepository = notificationRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardDto build(User user) {
        LocalDate today = LocalDate.now(clock);
        List<Long> projectIds = accessService.accessibleProjectIds(user);
        List<Task> tasks = projectIds.isEmpty() ? List.of() : taskRepository.findByProjectIds(projectIds);

        long pending = count(tasks, TaskStatus.PENDIENTE);
        long inProgress = count(tasks, TaskStatus.EN_PROGRESO);
        long completed = count(tasks, TaskStatus.COMPLETADA);
        List<Task> overdue = tasks.stream().filter(t -> t.isOverdue(today))
                .sorted(Comparator.comparing(Task::getDueDate)).toList();
        List<Task> upcoming = tasks.stream()
                .filter(t -> t.getStatus() != TaskStatus.COMPLETADA && t.getDueDate() != null
                        && !t.getDueDate().isBefore(today) && !t.getDueDate().isAfter(today.plusDays(7)))
                .sorted(Comparator.comparing(Task::getDueDate)).toList();
        long myOpen = tasks.stream().filter(t -> t.getAssignee() != null
                && t.getAssignee().getId().equals(user.getId()) && t.getStatus() != TaskStatus.COMPLETADA).count();

        Map<String, Long> byPriority = new LinkedHashMap<>();
        for (Priority p : Priority.values()) {
            byPriority.put(p.name(), tasks.stream().filter(t -> t.getPriority() == p && t.getStatus() != TaskStatus.COMPLETADA).count());
        }

        Map<Long, List<Task>> byProject = tasks.stream().collect(Collectors.groupingBy(t -> t.getProject().getId()));
        List<ProjectProgress> projects = projectRepository.findAllById(projectIds).stream()
                .map(p -> {
                    List<Task> pt = byProject.getOrDefault(p.getId(), List.of());
                    long done = count(pt, TaskStatus.COMPLETADA);
                    int progress = pt.isEmpty() ? 0 : (int) Math.round(done * 100.0 / pt.size());
                    return new ProjectProgress(p.getId(), p.getName(), pt.size(), done, progress);
                })
                .sorted(Comparator.comparing(ProjectProgress::name)).toList();

        int rate = tasks.isEmpty() ? 0 : (int) Math.round(completed * 100.0 / tasks.size());
        return new DashboardDto(projectIds.size(), tasks.size(), pending, inProgress, completed,
                overdue.size(), upcoming.size(), rate, myOpen,
                notificationRepository.countByUserIdAndReadFalse(user.getId()),
                byPriority, projects,
                upcoming.stream().limit(8).map(t -> TaskMapper.toDto(t, user, today)).toList(),
                overdue.stream().limit(8).map(t -> TaskMapper.toDto(t, user, today)).toList());
    }

    private static long count(Collection<Task> tasks, TaskStatus status) {
        return tasks.stream().filter(t -> t.getStatus() == status).count();
    }
}
