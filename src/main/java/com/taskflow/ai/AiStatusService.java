package com.taskflow.ai;

import com.taskflow.dto.AiDtos.AiStatus;
import com.taskflow.repository.TaskRepository;
import org.springframework.stereotype.Service;

@Service
public class AiStatusService {

    private final AiAvailability availability;
    private final QdrantClient qdrant;
    private final TaskRepository taskRepository;

    public AiStatusService(AiAvailability availability, QdrantClient qdrant, TaskRepository taskRepository) {
        this.availability = availability;
        this.qdrant = qdrant;
        this.taskRepository = taskRepository;
    }

    public AiStatus status() {
        boolean configured = availability.isConfigured();
        boolean reachable = qdrant.isReachable();
        String message;
        if (!configured) message = availability.missingConfigurationMessage();
        else if (!reachable) message = "Qdrant no está accesible en este momento.";
        else message = "IA operativa: embeddings en Qdrant y RAG disponibles.";
        return new AiStatus(configured, availability.provider(), availability.chatModel(), availability.embeddingModel(),
                reachable, taskRepository.count(), taskRepository.countByIndexedTrue(), message);
    }
}
