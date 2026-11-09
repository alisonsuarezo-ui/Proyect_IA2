package com.taskflow.ai;

import com.taskflow.config.TaskFlowProperties;
import com.taskflow.dto.AiDtos.SearchResult;
import com.taskflow.entity.Task;
import com.taskflow.entity.User;
import com.taskflow.exception.ApiException;
import com.taskflow.repository.TaskRepository;
import com.taskflow.service.AccessService;
import com.taskflow.service.TaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Búsqueda inteligente: embedding de la consulta -> Qdrant -> datos completos desde PostgreSQL. */
@Service
public class SemanticSearchService {

    private static final Logger log = LoggerFactory.getLogger(SemanticSearchService.class);

    /** Resultado interno: tarea completa (PostgreSQL) + puntuación de similitud (Qdrant). */
    public record ScoredTask(Task task, double score) {}

    private final EmbeddingService embeddingService;
    private final QdrantClient qdrant;
    private final TaskRepository taskRepository;
    private final AccessService accessService;
    private final AiAvailability availability;
    private final TaskFlowProperties props;
    private final Clock clock;

    public SemanticSearchService(EmbeddingService embeddingService, QdrantClient qdrant, TaskRepository taskRepository,
                                 AccessService accessService, AiAvailability availability,
                                 TaskFlowProperties props, Clock clock) {
        this.embeddingService = embeddingService;
        this.qdrant = qdrant;
        this.taskRepository = taskRepository;
        this.accessService = accessService;
        this.availability = availability;
        this.props = props;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<SearchResult> search(String query, Long projectId, Integer limit, User user) {
        if (query == null || query.isBlank() || query.length() > 500) {
            throw ApiException.badRequest("La consulta es obligatoria (máximo 500 caracteres)");
        }
        availability.requireConfigured();
        Collection<Long> scope = scope(projectId, user);
        int k = limit == null ? 10 : Math.max(1, Math.min(limit, 50));
        LocalDate today = LocalDate.now(clock);
        return retrieve(query, scope, k, props.ai().minScore()).stream()
                .map(st -> new SearchResult(TaskMapper.toDto(st.task(), user, today), round(st.score())))
                .toList();
    }

    /** Tareas con descripción similar a una tarea dada (usa el vector almacenado en Qdrant). */
    @Transactional(readOnly = true)
    public List<SearchResult> similar(Long taskId, Integer limit, User user) {
        availability.requireConfigured();
        Task source = accessService.requireTaskAccess(taskId, user);
        int k = limit == null ? 5 : Math.max(1, Math.min(limit, 20));
        Collection<Long> scope = accessService.accessibleProjectIds(user);
        List<ScoredTask> found;
        if (source.isIndexed()) {
            found = hydrate(qdrant.recommend(taskId, scope, k));
        } else {
            // La tarea aún no tiene vector: se genera un embedding al vuelo con su texto.
            // No se usa search() porque valida un máximo de 500 caracteres y la descripción puede superar ese límite.
            String text = EmbeddingService.buildTaskText(source.getTitle(), source.getDescription());
            found = retrieve(text, scope, k + 1, props.ai().minScore());
        }
        LocalDate today = LocalDate.now(clock);
        return found.stream()
                .filter(st -> !st.task().getId().equals(taskId))
                .limit(k)
                .map(st -> new SearchResult(TaskMapper.toDto(st.task(), user, today), round(st.score())))
                .toList();
    }

    /** Recuperación usada por el RAG y por la búsqueda. */
    public List<ScoredTask> retrieve(String query, Collection<Long> projectIds, int k, double minScore) {
        if (projectIds.isEmpty()) return List.of();
        float[] vector = embeddingService.embed(query);
        return hydrate(qdrant.search(vector, projectIds, k, minScore));
    }

    public Collection<Long> scope(Long projectId, User user) {
        if (projectId != null) {
            accessService.requireProjectAccess(projectId, user);
            return List.of(projectId);
        }
        return accessService.accessibleProjectIds(user);
    }

    /** Obtiene los datos completos desde PostgreSQL y limpia vectores huérfanos en Qdrant. */
    private List<ScoredTask> hydrate(List<QdrantClient.ScoredPoint> points) {
        if (points.isEmpty()) return List.of();
        List<Long> ids = points.stream().map(QdrantClient.ScoredPoint::id).toList();
        Map<Long, Task> byId = taskRepository.findAllWithRelationsByIdIn(ids).stream()
                .collect(Collectors.toMap(Task::getId, Function.identity()));
        List<ScoredTask> result = new ArrayList<>();
        for (QdrantClient.ScoredPoint p : points) {
            Task t = byId.get(p.id());
            if (t == null) {
                log.info("Vector huérfano {} eliminado de Qdrant", p.id());
                try { qdrant.delete(p.id()); } catch (Exception ignored) { }
                continue;
            }
            result.add(new ScoredTask(t, p.score()));
        }
        return result;
    }

    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
