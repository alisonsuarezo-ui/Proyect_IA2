package com.taskflow.service;

import com.taskflow.dto.NotificationDtos.CreateNotificationRequest;
import com.taskflow.dto.NotificationDtos.CreateNotificationResult;
import com.taskflow.dto.NotificationDtos.NotificationDto;
import com.taskflow.entity.Notification;
import com.taskflow.entity.Task;
import com.taskflow.entity.User;
import com.taskflow.exception.ApiException;
import com.taskflow.repository.NotificationRepository;
import com.taskflow.repository.TaskRepository;
import com.taskflow.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;

    public NotificationService(NotificationRepository notificationRepository, UserRepository userRepository,
                               TaskRepository taskRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.taskRepository = taskRepository;
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> list(User user, boolean unreadOnly) {
        List<Notification> list = unreadOnly
                ? notificationRepository.findUnreadByUserId(user.getId())
                : notificationRepository.findByUserId(user.getId());
        return list.stream().map(NotificationDto::from).toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(User user) {
        return notificationRepository.countByUserIdAndReadFalse(user.getId());
    }

    @Transactional
    public NotificationDto markRead(Long id, User user) {
        Notification n = owned(id, user);
        n.setRead(true);
        return NotificationDto.from(n);
    }

    @Transactional
    public int markAllRead(User user) {
        return notificationRepository.markAllRead(user.getId());
    }

    @Transactional
    public void delete(Long id, User user) {
        notificationRepository.delete(owned(id, user));
    }

    private Notification owned(Long id, User user) {
        Notification n = notificationRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Notificación no encontrada"));
        if (!n.getUser().getId().equals(user.getId())) {
            throw ApiException.forbidden("No puedes modificar notificaciones de otro usuario");
        }
        return n;
    }

    /**
     * Crea una notificación de forma idempotente: si ya existe otra con el mismo dedupKey
     * no se duplica (control en aplicación + restricción UNIQUE en base de datos).
     * Sin @Transactional a propósito: cada operación del repositorio usa su propia transacción
     * para poder capturar la violación de unicidad en condiciones de carrera.
     */
    public CreateNotificationResult create(CreateNotificationRequest req, String source) {
        if (notificationRepository.existsByDedupKey(req.dedupKey())) {
            return new CreateNotificationResult(false, true, null, req.dedupKey());
        }
        User user = userRepository.findById(req.userId())
                .orElseThrow(() -> ApiException.badRequest("El usuario " + req.userId() + " no existe"));
        Task task = null;
        if (req.taskId() != null) {
            task = taskRepository.findById(req.taskId())
                    .orElseThrow(() -> ApiException.badRequest("La tarea " + req.taskId() + " no existe"));
        }
        Notification n = new Notification();
        n.setUser(user);
        n.setTask(task);
        n.setType(req.type());
        n.setTitle(req.title());
        n.setMessage(req.message());
        n.setDedupKey(req.dedupKey());
        n.setSource(source);
        try {
            notificationRepository.saveAndFlush(n);
            log.info("Notificación {} creada para el usuario {} ({})", req.type(), user.getId(), source);
            return new CreateNotificationResult(true, false, n.getId(), req.dedupKey());
        } catch (DataIntegrityViolationException e) {
            return new CreateNotificationResult(false, true, null, req.dedupKey());
        }
    }
}
