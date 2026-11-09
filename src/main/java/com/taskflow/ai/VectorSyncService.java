package com.taskflow.ai;

import com.taskflow.dto.AiDtos.ReindexResult;
import com.taskflow.entity.Task;
import com.taskflow.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sincroniza PostgreSQL -> Qdrant. Se ejecuta tras el commit de cada transacción
 * (para no indexar datos que luego se revierten) y reintenta periódicamente las
 * tareas que quedaron sin indexar (p. ej. si la IA no estaba configurada o Qdrant caído).
 */
@Service
public class VectorSyncService {

    private static final Logger log = LoggerFactory.getLogger(VectorSyncService.class);

    private final TaskRepository taskRepository;
    private final EmbeddingService embeddingService;
    private final QdrantClient qdrant;
    private final AiAvailability availability;

    public VectorSyncService(TaskRepository taskRepository, EmbeddingService embeddingService,
                             QdrantClient qdrant, AiAvailability availability) {
        this.taskRepository = taskRepository;
        this.embeddingService = embeddingService;
        this.qdrant = qdrant;
        this.availability = availability;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTaskUpserted(VectorEvents.TaskUpserted event) {
        if (!availability.isConfigured()) {
            log.debug("IA no configurada: la tarea {} queda pendiente de indexar", event.taskId());
            return;
        }
        try {
            indexTask(event.taskId());
        } catch (Exception e) {
            log.warn("No se pudo indexar la tarea {}: {}", event.taskId(), e.getMessage());
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTaskDeleted(VectorEvents.TaskDeleted event) {
        try {
            qdrant.delete(event.taskId());
            log.info("Vector de la tarea {} eliminado de Qdrant", event.taskId());
        } catch (Exception e) {
            log.warn("No se pudo eliminar el vector de la tarea {}: {}", event.taskId(), e.getMessage());
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onProjectDeleted(VectorEvents.ProjectDeleted event) {
        try {
            qdrant.deleteByProject(event.projectId());
            log.info("Vectores del proyecto {} eliminados de Qdrant", event.projectId());
        } catch (Exception e) {
            log.warn("No se pudieron eliminar los vectores del proyecto {}: {}", event.projectId(), e.getMessage());
        }
    }

    /** Genera el embedding (título + descripción) y lo guarda en Qdrant con el ID de la tarea. */
    public boolean indexTask(long taskId) {
        List<Task> found = taskRepository.findAllWithRelationsByIdIn(List.of(taskId));
        if (found.isEmpty()) {
            qdrant.delete(taskId);
            return false;
        }
        Task task = found.get(0);
        float[] vector = embeddingService.embed(EmbeddingService.buildTaskText(task.getTitle(), task.getDescription()));
        Map<String, Object> payload = new HashMap<>();
        payload.put("taskId", task.getId());
        payload.put("projectId", task.getProject().getId());
        payload.put("title", task.getTitle());
        payload.put("status", task.getStatus().name());
        payload.put("priority", task.getPriority().name());
        payload.put("dueDate", task.getDueDate() != null ? task.getDueDate().toString() : null);
        payload.put("assigneeId", task.getAssignee() != null ? task.getAssignee().getId() : null);
        qdrant.upsert(task.getId(), vector, payload);
        taskRepository.updateIndexed(task.getId(), true);
        log.info("Tarea {} indexada en Qdrant ({} dimensiones)", task.getId(), vector.length);
        return true;
    }

    /** Reintento periódico de tareas pendientes de indexar. */
    @Scheduled(initialDelay = 30_000, fixedDelay = 300_000)
    public void syncPending() {
        if (!availability.isConfigured() || !qdrant.isReachable()) return;
        List<Long> pending = taskRepository.findUnindexedIds();
        if (pending.isEmpty()) return;
        log.info("Sincronizando {} tareas pendientes con Qdrant", pending.size());
        for (Long id : pending) {
            try {
                indexTask(id);
            } catch (Exception e) {
                log.warn("Fallo indexando tarea {}: {}", id, e.getMessage());
                return; // probablemente un problema global (cuota, red): se reintentará más tarde
            }
        }
    }

    /** Recrea la colección y vuelve a indexar todas las tareas. */
    public ReindexResult reindexAll() {
        availability.requireConfigured();
        float[] probe = embeddingService.embed("dimension probe");
        qdrant.recreateCollection(probe.length);
        taskRepository.resetIndexed();
        List<Long> ids = taskRepository.findAllIds();
        int ok = 0, failed = 0;
        for (Long id : ids) {
            try {
                if (indexTask(id)) ok++;
            } catch (Exception e) {
                failed++;
                log.warn("Fallo reindexando tarea {}: {}", id, e.getMessage());
            }
        }
        return new ReindexResult(ids.size(), ok, failed);
    }
}
