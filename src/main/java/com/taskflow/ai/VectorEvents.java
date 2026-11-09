package com.taskflow.ai;

/** Eventos de dominio que mantienen Qdrant sincronizado con PostgreSQL. */
public final class VectorEvents {
    private VectorEvents() {}

    /** Tarea creada o modificada: se debe (re)generar su embedding. */
    public record TaskUpserted(long taskId) {}

    /** Tarea eliminada: se debe borrar su vector. */
    public record TaskDeleted(long taskId) {}

    /** Proyecto eliminado: se borran todos los vectores de sus tareas. */
    public record ProjectDeleted(long projectId) {}
}
