package com.taskflow.ai;

import com.taskflow.config.TaskFlowProperties;
import com.taskflow.exception.AiNotConfiguredException;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Determina si el proveedor de IA está configurado. Nunca simula resultados:
 * si falta la configuración, las funciones de IA responden con un error claro.
 */
@Component
public class AiAvailability {

    private static final String PLACEHOLDER = "not-configured";

    private final TaskFlowProperties props;
    private final Environment env;

    public AiAvailability(TaskFlowProperties props, Environment env) {
        this.props = props;
        this.env = env;
    }

    public String provider() {
        return props.ai().provider() == null ? "openai" : props.ai().provider().trim().toLowerCase();
    }

    public boolean isConfigured() {
        return switch (provider()) {
            case "openai" -> {
                String key = props.ai().openaiApiKey();
                yield key != null && !key.isBlank() && !PLACEHOLDER.equals(key.trim());
            }
            case "ollama" -> true;
            default -> false;
        };
    }

    public String missingConfigurationMessage() {
        return switch (provider()) {
            case "openai" -> "La IA no está configurada: define OPENAI_API_KEY en el archivo .env "
                    + "(o AI_PROVIDER=ollama con OLLAMA_BASE_URL) y reinicia con 'docker compose up -d'.";
            case "ollama" -> "";
            default -> "Proveedor de IA no soportado: '" + provider() + "'. Usa AI_PROVIDER=openai u ollama.";
        };
    }

    public void requireConfigured() {
        if (!isConfigured()) {
            throw new AiNotConfiguredException(missingConfigurationMessage());
        }
    }

    public String chatModel() {
        return env.getProperty("spring.ai." + provider() + ".chat.options.model", "desconocido");
    }

    public String embeddingModel() {
        return env.getProperty("spring.ai." + provider() + ".embedding.options.model", "desconocido");
    }
}
