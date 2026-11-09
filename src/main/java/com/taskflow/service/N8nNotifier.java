package com.taskflow.service;

import com.taskflow.config.N8nApiKeyFilter;
import com.taskflow.config.TaskFlowProperties;
import com.taskflow.dto.NotificationDtos.CreateNotificationRequest;
import com.taskflow.entity.NotificationType;
import com.taskflow.entity.Task;
import com.taskflow.entity.User;
import com.taskflow.repository.TaskRepository;
import com.taskflow.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Comunicación saliente con n8n: cuando se asigna una tarea se invoca el webhook
 * "taskflow-task-assigned". n8n procesa el evento y registra la notificación en
 * TaskFlow (POST /api/n8n/notifications). Si n8n no está disponible, el backend crea
 * la notificación directamente con la misma clave de deduplicación.
 */
@Service
public class N8nNotifier {

    private static final Logger log = LoggerFactory.getLogger(N8nNotifier.class);

    private final RestClient http;
    private final TaskFlowProperties props;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public N8nNotifier(TaskFlowProperties props, TaskRepository taskRepository, UserRepository userRepository,
                       NotificationService notificationService) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);
        factory.setReadTimeout(10_000);
        this.http = RestClient.builder().requestFactory(factory).build();
        this.props = props;
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTaskAssigned(TaskService.TaskAssignedEvent event) {
        List<Task> found = taskRepository.findAllWithRelationsByIdIn(List.of(event.taskId()));
        User assignee = userRepository.findById(event.assigneeId()).orElse(null);
        if (found.isEmpty() || assignee == null) return;
        Task task = found.get(0);
        String assignedBy = userRepository.findById(event.assignedById()).map(User::getFullName).orElse("Alguien");
        String dedupKey = "TASK_ASSIGNED:" + task.getId() + ":" + assignee.getId() + ":" + event.assignedAt().toEpochMilli();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", "TASK_ASSIGNED");
        payload.put("taskId", task.getId());
        payload.put("taskTitle", task.getTitle());
        payload.put("projectId", task.getProject().getId());
        payload.put("projectName", task.getProject().getName());
        payload.put("priority", task.getPriority().name());
        payload.put("dueDate", task.getDueDate() != null ? task.getDueDate().toString() : null);
        payload.put("assigneeId", assignee.getId());
        payload.put("assigneeName", assignee.getFullName());
        payload.put("assigneeEmail", assignee.getEmail());
        payload.put("assignedBy", assignedBy);
        payload.put("dedupKey", dedupKey);

        try {
            http.post().uri(props.n8n().taskAssignedWebhookUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(N8nApiKeyFilter.HEADER, props.n8n().apiKey())
                    .body(payload)
                    .retrieve().toBodilessEntity();
            log.info("Evento de asignación de la tarea {} enviado a n8n", task.getId());
        } catch (Exception e) {
            log.warn("n8n no disponible ({}). Se crea la notificación de asignación desde el backend", e.getMessage());
            String due = task.getDueDate() != null ? " Fecha límite: " + task.getDueDate() + "." : "";
            notificationService.create(new CreateNotificationRequest(assignee.getId(), task.getId(),
                    NotificationType.TASK_ASSIGNED, "Nueva tarea asignada: " + task.getTitle(),
                    assignedBy + " te asignó la tarea \"" + task.getTitle() + "\" del proyecto "
                            + task.getProject().getName() + " (prioridad " + task.getPriority() + ")." + due,
                    dedupKey), "BACKEND");
        }
    }
}
