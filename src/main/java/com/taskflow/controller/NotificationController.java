package com.taskflow.controller;

import com.taskflow.config.SecurityUtils;
import com.taskflow.dto.NotificationDtos.NotificationDto;
import com.taskflow.service.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public List<NotificationDto> list(@RequestParam(defaultValue = "false") boolean unreadOnly) {
        return notificationService.list(SecurityUtils.currentUser(), unreadOnly);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {
        return Map.of("count", notificationService.unreadCount(SecurityUtils.currentUser()));
    }

    @PatchMapping("/{id}/read")
    public NotificationDto markRead(@PathVariable Long id) {
        return notificationService.markRead(id, SecurityUtils.currentUser());
    }

    @PatchMapping("/read-all")
    public Map<String, Integer> markAllRead() {
        return Map.of("updated", notificationService.markAllRead(SecurityUtils.currentUser()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        notificationService.delete(id, SecurityUtils.currentUser());
    }
}
