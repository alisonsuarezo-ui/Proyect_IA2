package com.taskflow.ai;

import com.taskflow.exception.AiServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/** Genera embeddings reales mediante el EmbeddingModel de Spring AI (OpenAI u Ollama). */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final ObjectProvider<EmbeddingModel> embeddingModel;
    private final AiAvailability availability;

    public EmbeddingService(ObjectProvider<EmbeddingModel> embeddingModel, AiAvailability availability) {
        this.embeddingModel = embeddingModel;
        this.availability = availability;
    }

    public float[] embed(String text) {
        availability.requireConfigured();
        EmbeddingModel model = embeddingModel.getIfAvailable();
        if (model == null) {
            throw new AiServiceException("No hay un modelo de embeddings disponible para el proveedor " + availability.provider());
        }
        try {
            float[] vector = model.embed(text);
            if (vector == null || vector.length == 0) {
                throw new AiServiceException("El proveedor de IA devolvió un embedding vacío");
            }
            return vector;
        } catch (AiServiceException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Error generando embedding: {}", e.getMessage());
            throw new AiServiceException("No se pudo generar el embedding: " + rootMessage(e));
        }
    }

    public static String buildTaskText(String title, String description) {
        StringBuilder sb = new StringBuilder("Título: ").append(title);
        if (description != null && !description.isBlank()) {
            sb.append("\nDescripción: ").append(description);
        }
        return sb.toString();
    }

    static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) t = t.getCause();
        String msg = t.getMessage();
        if (msg == null) return t.getClass().getSimpleName();
        return msg.length() > 300 ? msg.substring(0, 300) + "..." : msg;
    }
}
