package com.taskflow.service;

import com.taskflow.ai.VectorEvents;
import com.taskflow.dto.ProjectDtos.ProjectDto;
import com.taskflow.dto.ProjectDtos.ProjectRequest;
import com.taskflow.dto.UserDtos.UserSummary;
import com.taskflow.entity.Project;
import com.taskflow.entity.TaskStatus;
import com.taskflow.entity.User;
import com.taskflow.exception.ApiException;
import com.taskflow.repository.ProjectRepository;
import com.taskflow.repository.TaskRepository;
import com.taskflow.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final AccessService accessService;
    private final ApplicationEventPublisher events;

    public ProjectService(ProjectRepository projectRepository, TaskRepository taskRepository,
                          UserRepository userRepository, AccessService accessService,
                          ApplicationEventPublisher events) {
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.accessService = accessService;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<ProjectDto> list(User user) {
        List<Project> projects = user.isAdmin()
                ? projectRepository.findAllByOrderByCreatedAtDesc()
                : projectRepository.findAccessible(user);
        return projects.stream().map(p -> toDto(p, user)).toList();
    }

    @Transactional(readOnly = true)
    public ProjectDto get(Long id, User user) {
        return toDto(accessService.requireProjectAccess(id, user), user);
    }

    @Transactional
    public ProjectDto create(ProjectRequest req, User user) {
        Project project = new Project();
        project.setOwner(userRepository.getReferenceById(user.getId()));
        apply(project, req, user.getId());
        projectRepository.save(project);
        return toDto(project, user);
    }

    @Transactional
    public ProjectDto update(Long id, ProjectRequest req, User user) {
        Project project = accessService.requireProjectManage(id, user);
        Set<Long> before = new HashSet<>();
        project.getMembers().forEach(m -> before.add(m.getId()));
        apply(project, req, project.getOwner().getId());
        Set<Long> after = new HashSet<>();
        project.getMembers().forEach(m -> after.add(m.getId()));
        before.removeAll(after);
        // Los miembros retirados dejan de ser responsables de las tareas del proyecto
        for (Long removed : before) {
            taskRepository.unassignUserFromProject(removed, project.getId());
        }
        return toDto(project, user);
    }

    @Transactional
    public void delete(Long id, User user) {
        Project project = accessService.requireProjectManage(id, user);
        projectRepository.delete(project);
        events.publishEvent(new VectorEvents.ProjectDeleted(id));
    }

    private void apply(Project project, ProjectRequest req, Long ownerId) {
        project.setName(req.name().trim());
        project.setDescription(req.description() == null ? null : req.description().trim());
        Set<User> members = new HashSet<>();
        if (req.memberIds() != null) {
            List<Long> ids = req.memberIds().stream().filter(Objects::nonNull).distinct()
                    .filter(mid -> !mid.equals(ownerId)).toList();
            List<User> found = userRepository.findAllById(ids);
            if (found.size() != ids.size()) {
                throw ApiException.badRequest("Alguno de los miembros indicados no existe");
            }
            if (found.stream().anyMatch(u -> !u.isActive())) {
                throw ApiException.badRequest("No se pueden añadir usuarios desactivados");
            }
            members.addAll(found);
        }
        project.getMembers().clear();
        project.getMembers().addAll(members);
    }

    private ProjectDto toDto(Project p, User viewer) {
        long total = p.getId() == null ? 0 : taskRepository.countByProjectId(p.getId());
        long completed = p.getId() == null ? 0 : taskRepository.countByProjectIdAndStatus(p.getId(), TaskStatus.COMPLETADA);
        int progress = total == 0 ? 0 : (int) Math.round(completed * 100.0 / total);
        List<UserSummary> members = p.getMembers().stream()
                .sorted(Comparator.comparing(User::getFullName))
                .map(UserSummary::from).toList();
        return new ProjectDto(p.getId(), p.getName(), p.getDescription(), UserSummary.from(p.getOwner()), members,
                total, completed, progress, p.canManage(viewer), p.getCreatedAt(), p.getUpdatedAt());
    }
}
