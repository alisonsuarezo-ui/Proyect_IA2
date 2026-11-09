package com.taskflow.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.taskflow.config.TaskFlowProperties;
import com.taskflow.exception.AiServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.*;

/**
 * Cliente REST de Qdrant. Cada punto usa como ID el ID de la tarea en PostgreSQL,
 * lo que mantiene ambas bases sincronizadas de forma directa.
 */
@Component
public class QdrantClient {

    private static final Logger log = LoggerFactory.getLogger(QdrantClient.class);

    public record ScoredPoint(long id, double score) {}

    private final RestClient http;
    private final String collection;
    private volatile Integer knownDimension;

    public QdrantClient(TaskFlowProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(30_000);
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(props.qdrant().url())
                .requestFactory(factory)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        if (props.qdrant().apiKey() != null && !props.qdrant().apiKey().isBlank()) {
            builder.defaultHeader("api-key", props.qdrant().apiKey());
        }
        this.http = builder.build();
        this.collection = props.qdrant().collection();
    }

    public String collection() { return collection; }

    public boolean isReachable() {
        try {
            http.get().uri("/").retrieve().toBodilessEntity();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Devuelve la dimensión de la colección o null si no existe. */
    public Integer collectionDimension() {
        try {
            JsonNode node = http.get().uri("/collections/{c}", collection).retrieve().body(JsonNode.class);
            JsonNode size = node.path("result").path("config").path("params").path("vectors").path("size");
            return size.isMissingNode() ? null : size.asInt();
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        } catch (RestClientException e) {
            throw new AiServiceException("Qdrant no está disponible: " + e.getMessage());
        }
    }

    /** Crea la colección (distancia coseno) si no existe y valida su dimensión. */
    public synchronized void ensureCollection(int dimension) {
        if (knownDimension != null && knownDimension == dimension) return;
        Integer existing = collectionDimension();
        if (existing == null) {
            createCollection(dimension);
        } else if (existing != dimension) {
            throw new AiServiceException("La colección '" + collection + "' tiene dimensión " + existing
                    + " pero el modelo de embeddings produce " + dimension
                    + ". Ejecuta la reindexación (POST /api/admin/ai/reindex) para recrearla.");
        }
        knownDimension = dimension;
    }

    public synchronized void recreateCollection(int dimension) {
        try {
            http.delete().uri("/collections/{c}", collection).retrieve().toBodilessEntity();
        } catch (HttpClientErrorException.NotFound ignored) {
            // no existía
        }
        createCollection(dimension);
        knownDimension = dimension;
    }

    private void createCollection(int dimension) {
        try {
            http.put().uri("/collections/{c}", collection)
                    .body(Map.of("vectors", Map.of("size", dimension, "distance", "Cosine")))
                    .retrieve().toBodilessEntity();
            http.put().uri("/collections/{c}/index?wait=true", collection)
                    .body(Map.of("field_name", "projectId", "field_schema", "integer"))
                    .retrieve().toBodilessEntity();
            log.info("Colección Qdrant '{}' creada (dimensión {})", collection, dimension);
        } catch (RestClientException e) {
            throw new AiServiceException("No se pudo crear la colección en Qdrant: " + e.getMessage());
        }
    }

    public void upsert(long id, float[] vector, Map<String, Object> payload) {
        ensureCollection(vector.length);
        Map<String, Object> point = Map.of("id", id, "vector", vector, "payload", payload);
        try {
            http.put().uri("/collections/{c}/points?wait=true", collection)
                    .body(Map.of("points", List.of(point)))
                    .retrieve().toBodilessEntity();
        } catch (RestClientException e) {
            throw new AiServiceException("Error guardando el vector en Qdrant: " + e.getMessage());
        }
    }

    public void delete(long id) {
        deleteWithBody(Map.of("points", List.of(id)));
    }

    public void deleteByProject(long projectId) {
        deleteWithBody(Map.of("filter", Map.of("must", List.of(
                Map.of("key", "projectId", "match", Map.of("value", projectId))))));
    }

    private void deleteWithBody(Map<String, Object> body) {
        try {
            http.post().uri("/collections/{c}/points/delete?wait=true", collection)
                    .body(body).retrieve().toBodilessEntity();
        } catch (HttpClientErrorException.NotFound e) {
            // la colección aún no existe: no hay nada que borrar
        } catch (RestClientException e) {
            throw new AiServiceException("Error eliminando vectores en Qdrant: " + e.getMessage());
        }
    }

    /** Búsqueda por similitud restringida a los proyectos a los que el usuario tiene acceso. */
    public List<ScoredPoint> search(float[] vector, Collection<Long> projectIds, int limit, double minScore) {
        if (projectIds.isEmpty()) return List.of();
        Map<String, Object> body = new HashMap<>();
        body.put("vector", vector);
        body.put("limit", limit);
        body.put("with_payload", false);
        body.put("score_threshold", minScore);
        body.put("filter", projectFilter(projectIds));
        return scoredQuery("/collections/{c}/points/search", body);
    }

    /** Tareas similares a una tarea existente usando su propio vector almacenado. */
    public List<ScoredPoint> recommend(long taskId, Collection<Long> projectIds, int limit) {
        if (projectIds.isEmpty()) return List.of();
        Map<String, Object> body = new HashMap<>();
        body.put("positive", List.of(taskId));
        body.put("limit", limit);
        body.put("with_payload", false);
        body.put("filter", projectFilter(projectIds));
        return scoredQuery("/collections/{c}/points/recommend", body);
    }

    private Map<String, Object> projectFilter(Collection<Long> projectIds) {
        return Map.of("must", List.of(Map.of("key", "projectId", "match", Map.of("any", new ArrayList<>(projectIds)))));
    }

    private List<ScoredPoint> scoredQuery(String uri, Map<String, Object> body) {
        try {
            JsonNode node = http.post().uri(uri, collection).body(body).retrieve().body(JsonNode.class);
            List<ScoredPoint> result = new ArrayList<>();
            for (JsonNode p : node.path("result")) {
                result.add(new ScoredPoint(p.path("id").asLong(), p.path("score").asDouble()));
            }
            return result;
        } catch (HttpClientErrorException.NotFound e) {
            return List.of(); // colección o punto inexistente
        } catch (HttpClientErrorException.BadRequest e) {
            log.warn("Consulta Qdrant rechazada: {}", e.getResponseBodyAsString());
            return List.of();
        } catch (RestClientException e) {
            throw new AiServiceException("Error consultando Qdrant: " + e.getMessage());
        }
    }

    public long countPoints() {
        try {
            JsonNode node = http.post().uri("/collections/{c}/points/count", collection)
                    .body(Map.of("exact", true)).retrieve().body(JsonNode.class);
            return node.path("result").path("count").asLong();
        } catch (Exception e) {
            return 0;
        }
    }
}
