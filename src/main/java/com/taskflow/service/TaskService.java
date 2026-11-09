package com.taskflow.service;

import com.taskflow.ai.VectorEvents;
import com.taskflow.dto.TaskDtos.StatusUpdateRequest;
import com.taskflow.dto.TaskDtos.TaskDto;
import com.taskflow.dto.TaskDtos.TaskRequest;
import com.taskflow.entity.*;
import com.taskflow.exception.ApiException;
import com.taskflow.repository.TaskRepository;
import com.taskflow.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Service
public class TaskService {

    /** Evento publicado cuando una tarea se asigna (o reasigna) a un usuario. */
    public record TaskAssignedEvent(long taskId, long assigneeId, long assignedById, Instant assignedAt) {}

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final AccessService accessService;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public TaskService(TaskRepository taskRepository, UserRepository userRepository, AccessService accessService,
                       ApplicationEventPublisher events, Clock clock) {
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.accessService = accessService;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<TaskDto> list(User user, Long projectId, TaskStatus status, Priority priority,
                              Long assigneeId, String q, Boolean overdueOnly) {
        List<Long> projectIds = accessService.accessibleProjectIds(user);
        if (projectIds.isEmpty()) return List.of();
        String term = q == null ? "" : q.trim().toLowerCase()
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        String pattern = "%" + term + "%";
        LocalDate today = LocalDate.now(clock);
        return taskRepository.search(projectIds, projectId, status, priority, assigneeId, pattern).stream()
                .filter(t -> !Boolean.TRUE.equals(overdueOnly) || t.isOverdue(today))
                .map(t -> TaskMapper.toDto(t, user, today))
                .toList();
    }

    @Transactional(readOnly = true)
    public TaskDto get(Long id, User user) {
        return TaskMapper.toDto(accessService.requireTaskAccess(id, user), user, LocalDate.now(clock));
    }

    @Transactional
    public TaskDto create(TaskRequest req, User user) {
        Project project = accessService.requireProjectAccess(req.projectId(), user);
        Task task = new Task();
        task.setProject(project);
        task.setCreatedBy(userRepository.getReferenceById(user.getId()));
        task.setTitle(req.title().trim());
        task.setDescription(trimOrNull(req.description()));
        task.setPriority(req.priority() != null ? req.priority() : Priority.MEDIA);
        task.setDueDate(req.dueDate());
        applyStatus(task, req.status() != null ? req.status() : TaskStatus.PENDIENTE);
        User assignee = resolveAssignee(req.assigneeId(), project);
        task.setAssignee(assignee);
        if (assignee != null) task.setAssignedAt(Instant.now());
        taskRepository.save(task);

        events.publishEvent(new VectorEvents.TaskUpserted(task.getId()));
        if (assignee != null) {
            events.publishEvent(new TaskAssignedEvent(task.getId(), assignee.getId(), user.getId(), task.getAssignedAt()));
        }
        return TaskMapper.toDto(task, user, LocalDate.now(clock));
    }

    @Transactional
    public TaskDto update(Long id, TaskRequest req, User user) {
        Task task = accessService.requireTaskAccess(id, user);
        Project project = task.getProject();
        if (!Objects.equals(project.getId(), req.projectId())) {
            project = accessService.requireProjectAccess(req.projectId(), user);
            task.setProject(project);
        }
        task.setTitle(req.title().trim());
        task.setDescription(trimOrNull(req.description()));
        if (req.priority() != null) task.setPriority(req.priority());
        task.setDueDate(req.dueDate());
        if (req.status() != null) applyStatus(task, req.status());

        User previous = task.getAssignee();
        User assignee = resolveAssignee(req.assigneeId(), project);
        boolean assigneeChanged = assignee != null
                && (previous == null || !previous.getId().equals(assignee.getId()));
        task.setAssignee(assignee);
        if (assignee == null) task.setAssignedAt(null);
        if (assigneeChanged) task.setAssignedAt(Instant.now());
        task.setIndexed(false);
        taskRepository.save(task);

        events.publishEvent(new VectorEvents.TaskUpserted(task.getId()));
        if (assigneeChanged) {
            events.publishEvent(new TaskAssignedEvent(task.getId(), assignee.getId(), user.getId(), task.getAssignedAt()));
        }
        return TaskMapper.toDto(task, user, LocalDate.now(clock));
    }

    @Transactional
    public TaskDto updateStatus(Long id, StatusUpdateRequest req, User user) {
        Task task = accessService.requireTaskAccess(id, user);
        applyStatus(task, req.status());
        task.setIndexed(false);
        events.publishEvent(new VectorEvents.TaskUpserted(task.getId()));
        return TaskMapper.toDto(task, user, LocalDate.now(clock));
    }

    @Transactional
    public void delete(Long id, User user) {
        Task task = accessService.requireTaskAccess(id, user);
        if (!accessService.canDeleteTask(task, user)) {
            throw ApiException.forbidden("Solo el creador, el propietario del proyecto o un administrador pueden eliminar la tarea");
        }
        taskRepository.delete(task);
        events.publishEvent(new VectorEvents.TaskDeleted(id));
    }

    private void applyStatus(Task task, TaskStatus status) {
        if (status == TaskStatus.COMPLETADA && task.getStatus() != TaskStatus.COMPLETADA) {
            task.setCompletedAt(Instant.now());
        } else if (status != TaskStatus.COMPLETADA) {
            task.setCompletedAt(null);
        }
        task.setStatus(status);
    }

    private User resolveAssignee(Long assigneeId, Project project) {
        if (assigneeId == null) return null;
        User assignee = userRepository.findById(assigneeId)
                .orElseThrow(() -> ApiException.badRequest("El usuario asignado no existe"));
        if (!assignee.isActive()) {
            throw ApiException.badRequest("No se puede asignar la tarea a un usuario desactivado");
        }
        if (!project.isParticipant(assignee)) {
            throw ApiException.badRequest("El responsable debe ser propietario o miembro del proyecto");
        }
        return assignee;
    }

    private static String trimOrNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
