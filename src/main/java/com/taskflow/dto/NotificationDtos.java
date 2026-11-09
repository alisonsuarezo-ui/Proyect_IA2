package com.taskflow.dto;

import com.taskflow.entity.Notification;
import com.taskflow.entity.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class NotificationDtos {
    private NotificationDtos() {}

    public record NotificationDto(Long id, NotificationType type, String title, String message, boolean read,
                                  Long taskId, String taskTitle, String source, Instant createdAt) {
        public static NotificationDto from(Notification n) {
            return new NotificationDto(n.getId(), n.getType(), n.getTitle(), n.getMessage(), n.isRead(),
                    n.getTask() != null ? n.getTask().getId() : null,
                    n.getTask() != null ? n.getTask().getTitle() : null,
                    n.getSource(), n.getCreatedAt());
        }
    }

    /** Petición que n8n envía al backend para registrar una notificación. */
    public record CreateNotificationRequest(
            @NotNull(message = "userId es obligatorio") Long userId,
            Long taskId,
            @NotNull(message = "type es obligatorio") NotificationType type,
            @NotBlank(message = "title es obligatorio") @Size(max = 200) String title,
            @Size(max = 4000) String message,
            @NotBlank(message = "dedupKey es obligatorio") @Size(max = 200) String dedupKey) {}

    public record CreateNotificationResult(boolean created, boolean duplicate, Long notificationId, String dedupKey) {}

    /** Envoltorio de listas para n8n (el nodo Split Out separa "items"). */
    public record ItemsResponse(int count, List<Map<String, Object>> items) {
        public static ItemsResponse of(List<Map<String, Object>> items) { return new ItemsResponse(items.size(), items); }
    }
}
