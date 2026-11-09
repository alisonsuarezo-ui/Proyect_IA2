package com.taskflow.exception;

import org.springframework.http.HttpStatus;

/** Se lanza cuando falta la configuración del proveedor de IA (p. ej. OPENAI_API_KEY). */
public class AiNotConfiguredException extends ApiException {
    public AiNotConfiguredException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "AI_NOT_CONFIGURED", message);
    }
}
