package com.taskflow.controller;

import com.taskflow.dto.NotificationDtos.CreateNotificationRequest;
import com.taskflow.dto.NotificationDtos.CreateNotificationResult;
import com.taskflow.dto.NotificationDtos.ItemsResponse;
import com.taskflow.service.N8nIntegrationService;
import com.taskflow.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * API consumida por los workflows de n8n. Protegida con la cabecera X-API-Key (N8N_API_KEY).
 */
@RestController
@RequestMapping("/api/n8n")
public class N8nController {

    private final N8nIntegrationService integrationService;
    private final NotificationService notificationService;

    public N8nController(N8nIntegrationService integrationService, NotificationService notificationService) {
        this.integrationService = integrationService;
        this.notificationService = notificationService;
    }

    @GetMapping("/tasks/due-tomorrow")
    public ItemsResponse dueTomorrow() {
        return ItemsResponse.of(integrationService.tasksDueTomorrow());
    }

    @GetMapping("/tasks/overdue")
    public ItemsResponse overdue() {
        return ItemsResponse.of(integrationService.overdueTasks());
    }

    @GetMapping("/summaries/daily")
    public ItemsResponse dailySummaries() {
        return ItemsResponse.of(integrationService.dailySummaries());
    }

    /** Registra una notificación de forma idempotente (dedupKey). 201 si se crea, 200 si era duplicada. */
    @PostMapping("/notifications")
    public ResponseEntity<CreateNotificationResult> createNotification(@Valid @RequestBody CreateNotificationRequest req) {
        CreateNotificationResult result = notificationService.create(req, "N8N");
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result);
    }
}
