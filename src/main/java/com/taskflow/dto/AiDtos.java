package com.taskflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class AiDtos {
    private AiDtos() {}

    public record SearchResult(TaskDtos.TaskDto task, double score) {}

    public record RagRequest(
            @NotBlank(message = "La pregunta es obligatoria") @Size(max = 1000) String question,
            Long projectId) {}

    public record RagResponse(String question, String answer, List<SearchResult> sources,
                              int contextTasks, String provider, String model) {}

    public record AiStatus(boolean configured, String provider, String chatModel, String embeddingModel,
                           boolean qdrantReachable, long totalTasks, long indexedTasks, String message) {}

    public record ReindexResult(int total, int indexed, int failed) {}
}
