package com.taskflow.service;

import com.taskflow.entity.Project;
import com.taskflow.entity.Task;
import com.taskflow.entity.User;
import com.taskflow.exception.ApiException;
import com.taskflow.repository.ProjectRepository;
import com.taskflow.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Centraliza las reglas de autorización por proyecto/tarea. */
@Service
@Transactional(readOnly = true)
public class AccessService {

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;

    public AccessService(ProjectRepository projectRepository, TaskRepository taskRepository) {
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
    }

    /** IDs de proyectos visibles para el usuario (ADMIN ve todos). */
    public List<Long> accessibleProjectIds(User user) {
        return user.isAdmin() ? projectRepository.findAllIds() : projectRepository.findAccessibleIds(user);
    }

    public Project requireProjectAccess(Long projectId, User user) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> ApiException.notFound("Proyecto no encontrado"));
        if (!project.hasAccess(user)) {
            throw ApiException.forbidden("No tienes acceso a este proyecto");
        }
        return project;
    }

    public Project requireProjectManage(Long projectId, User user) {
        Project project = requireProjectAccess(projectId, user);
        if (!project.canManage(user)) {
            throw ApiException.forbidden("Solo el propietario del proyecto o un administrador puede realizar esta acción");
        }
        return project;
    }

    public Task requireTaskAccess(Long taskId, User user) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> ApiException.notFound("Tarea no encontrada"));
        if (!task.getProject().hasAccess(user)) {
            throw ApiException.forbidden("No tienes acceso a esta tarea");
        }
        return task;
    }

    public boolean canDeleteTask(Task task, User user) {
        return user.isAdmin()
                || task.getProject().getOwner().getId().equals(user.getId())
                || task.getCreatedBy().getId().equals(user.getId());
    }
}
