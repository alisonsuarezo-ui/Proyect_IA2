package com.taskflow.service;

import com.taskflow.entity.Task;
import com.taskflow.entity.TaskStatus;
import com.taskflow.entity.User;
import com.taskflow.repository.NotificationRepository;
import com.taskflow.repository.TaskRepository;
import com.taskflow.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Datos que consumen los workflows de n8n. Cada elemento incluye la clave de deduplicación
 * que n8n reenvía al crear la notificación; además se excluyen los elementos ya notificados.
 */
@Service
@Transactional(readOnly = true)
public class N8nIntegrationService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final Clock clock;

    public N8nIntegrationService(TaskRepository taskRepository, UserRepository userRepository,
                                 NotificationRepository notificationRepository, Clock clock) {
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
        this.clock = clock;
    }

    /** Tareas abiertas que vencen mañana (recordatorio 1 día antes). */
    public List<Map<String, Object>> tasksDueTomorrow() {
        LocalDate tomorrow = LocalDate.now(clock).plusDays(1);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Task t : taskRepository.findOpenDueOn(tomorrow)) {
            User target = recipient(t);
            if (target == null) continue;
            String key = "DUE_REMINDER:" + t.getId() + ":" + t.getDueDate() + ":" + target.getId();
            if (notificationRepository.existsByDedupKey(key)) continue;
            Map<String, Object> item = baseItem(t, target);
            item.put("dedupKey", key);
            items.add(item);
        }
        return items;
    }

    /** Tareas abiertas con fecha límite vencida. Se notifica una vez por tarea y fecha límite. */
    public List<Map<String, Object>> overdueTasks() {
        LocalDate today = LocalDate.now(clock);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Task t : taskRepository.findOpenOverdue(today)) {
            User target = recipient(t);
            if (target == null) continue;
            String key = "OVERDUE:" + t.getId() + ":" + t.getDueDate() + ":" + target.getId();
            if (notificationRepository.existsByDedupKey(key)) continue;
            Map<String, Object> item = baseItem(t, target);
            item.put("daysOverdue", ChronoUnit.DAYS.between(t.getDueDate(), today));
            item.put("dedupKey", key);
            items.add(item);
        }
        return items;
    }

    /** Resumen diario por usuario con sus tareas pendientes / en progreso. */
    public List<Map<String, Object>> dailySummaries() {
        LocalDate today = LocalDate.now(clock);
        List<Map<String, Object>> items = new ArrayList<>();
        for (User u : userRepository.findByActiveTrueOrderByFullNameAsc()) {
            String key = "DAILY_SUMMARY:" + u.getId() + ":" + today;
            if (notificationRepository.existsByDedupKey(key)) continue;
            List<Task> open = taskRepository.findOpenByAssignee(u.getId());
            if (open.isEmpty()) continue;
            long pending = open.stream().filter(t -> t.getStatus() == TaskStatus.PENDIENTE).count();
            long inProgress = open.stream().filter(t -> t.getStatus() == TaskStatus.EN_PROGRESO).count();
            long overdue = open.stream().filter(t -> t.isOverdue(today)).count();
            long dueToday = open.stream().filter(t -> today.equals(t.getDueDate())).count();
            StringBuilder lines = new StringBuilder();
            open.stream().limit(10).forEach(t -> lines.append("• #").append(t.getId()).append(' ')
                    .append(t.getTitle()).append(" [").append(t.getStatus()).append(", ").append(t.getPriority())
                    .append(t.getDueDate() != null ? ", vence " + t.getDueDate() : "").append("]\n"));
            if (open.size() > 10) lines.append("… y ").append(open.size() - 10).append(" más\n");

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("userId", u.getId());
            item.put("userName", u.getFullName());
            item.put("userEmail", u.getEmail());
            item.put("date", today.toString());
            item.put("openCount", open.size());
            item.put("pendingCount", pending);
            item.put("inProgressCount", inProgress);
            item.put("overdueCount", overdue);
            item.put("dueTodayCount", dueToday);
            item.put("taskList", lines.toString().trim());
            item.put("dedupKey", key);
            items.add(item);
        }
        return items;
    }

    /** El responsable de la tarea o, si no tiene, el propietario del proyecto. */
    private User recipient(Task t) {
        User target = t.getAssignee() != null ? t.getAssignee() : t.getProject().getOwner();
        return target != null && target.isActive() ? target : null;
    }

    private Map<String, Object> baseItem(Task t, User target) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("taskId", t.getId());
        item.put("title", t.getTitle());
        item.put("projectId", t.getProject().getId());
        item.put("projectName", t.getProject().getName());
        item.put("priority", t.getPriority().name());
        item.put("status", t.getStatus().name());
        item.put("dueDate", t.getDueDate().toString());
        item.put("userId", target.getId());
        item.put("userName", target.getFullName());
        item.put("userEmail", target.getEmail());
        return item;
    }
}
