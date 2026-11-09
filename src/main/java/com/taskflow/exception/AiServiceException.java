package com.taskflow.exception;

import org.springframework.http.HttpStatus;

/** Error al comunicarse con el proveedor de IA o con Qdrant. */
public class AiServiceException extends ApiException {
    public AiServiceException(String message) {
        super(HttpStatus.BAD_GATEWAY, "AI_SERVICE_ERROR", message);
    }
}
