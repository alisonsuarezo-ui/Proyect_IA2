package com.taskflow.ai;

import com.taskflow.config.TaskFlowProperties;
import com.taskflow.dto.AiDtos.RagResponse;
import com.taskflow.dto.AiDtos.SearchResult;
import com.taskflow.entity.*;
import com.taskflow.exception.AiServiceException;
import com.taskflow.repository.ProjectRepository;
import com.taskflow.repository.TaskRepository;
import com.taskflow.service.TaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG de TaskFlow:
 * 1) embedding de la pregunta, 2) Qdrant recupera las tareas más relevantes,
 * 3) PostgreSQL aporta los datos completos y métricas reales del ámbito del usuario,
 * 4) se construye el contexto y 5) el LLM responde exclusivamente con esa información.
 */
@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);
    private static final int MAX_LIST = 25;

    static final String SYSTEM_PROMPT = """
            Eres el asistente de consultas de TaskFlow, un gestor de tareas y proyectos.
            Responde SIEMPRE en español y basándote ÚNICAMENTE en el CONTEXTO proporcionado,
            que procede de la base de datos real de TaskFlow.
            Reglas:
            - No inventes tareas, fechas, personas ni datos que no aparezcan en el contexto.
            - Si el contexto no contiene información suficiente para responder, dilo claramente
              ("No hay información suficiente en TaskFlow para responder a esto") y sugiere qué dato falta.
            - Cuando menciones tareas, cita su identificador con el formato #ID y su título.
            - Una tarea está atrasada si su fecha límite es anterior a la fecha actual y no está COMPLETADA.
            - Para urgencia considera prioridad (URGENTE > ALTA > MEDIA > BAJA) y cercanía de la fecha límite.
            - Sé conciso y usa listas cuando enumeres varias tareas.
            """;

    private final SemanticSearchService searchService;
    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final ObjectProvider<ChatModel> chatModel;
    private final AiAvailability availability;
    private final TaskFlowProperties props;
    private final Clock clock;

    public RagService(SemanticSearchService searchService, TaskRepository taskRepository,
                      ProjectRepository projectRepository, ObjectProvider<ChatModel> chatModel,
                      AiAvailability availability, TaskFlowProperties props, Clock clock) {
        this.searchService = searchService;
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
        this.chatModel = chatModel;
        this.availability = availability;
        this.props = props;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public RagResponse ask(String question, Long projectId, User user) {
        availability.requireConfigured();
        LocalDate today = LocalDate.now(clock);
        Collection<Long> scope = searchService.scope(projectId, user);

        if (scope.isEmpty()) {
            return new RagResponse(question,
                    "No hay información suficiente: todavía no participas en ningún proyecto con tareas.",
                    List.of(), 0, availability.provider(), availability.chatModel());
        }

        // 1-4: recuperación semántica (Qdrant) + datos completos (PostgreSQL)
        List<SemanticSearchService.ScoredTask> retrieved =
                searchService.retrieve(question, scope, props.ai().ragTopK(), props.ai().minScore());
        List<Task> allInScope = taskRepository.findByProjectIds(scope);

        // 5: construcción del contexto
        String context = buildContext(question, retrieved, allInScope, scope, projectId, today);

        // 6: generación con el LLM
        ChatModel model = chatModel.getIfAvailable();
        if (model == null) {
            throw new AiServiceException("No hay un modelo de chat disponible para el proveedor " + availability.provider());
        }
        String answer;
        try {
            answer = ChatClient.create(model).prompt()
                    .system(SYSTEM_PROMPT)
                    .user(u -> u.text("""
                            CONTEXTO:
                            {context}

                            PREGUNTA DEL USUARIO:
                            {question}
                            """).param("context", context).param("question", question))
                    .call()
                    .content();
        } catch (Exception e) {
            log.warn("Error del LLM: {}", e.getMessage());
            throw new AiServiceException("El modelo de lenguaje no pudo generar la respuesta: "
                    + EmbeddingService.rootMessage(e));
        }

        List<SearchResult> sources = retrieved.stream()
                .map(st -> new SearchResult(TaskMapper.toDto(st.task(), user, today),
                        Math.round(st.score() * 1000.0) / 1000.0))
                .toList();
        return new RagResponse(question, answer, sources, retrieved.size(),
                availability.provider(), availability.chatModel());
    }

    String buildContext(String question, List<SemanticSearchService.ScoredTask> retrieved, List<Task> all,
                        Collection<Long> scope, Long projectId, LocalDate today) {
        StringBuilder sb = new StringBuilder();
        sb.append("Fecha actual: ").append(today.format(DateTimeFormatter.ISO_LOCAL_DATE)).append('\n');

        if (projectId != null) {
            projectRepository.findById(projectId).ifPresent(p -> {
                sb.append("Proyecto consultado: ").append(p.getName()).append(" (#").append(p.getId()).append(")\n");
                if (p.getDescription() != null) sb.append("Descripción del proyecto: ").append(p.getDescription()).append('\n');
            });
        } else {
            sb.append("Ámbito: ").append(scope.size()).append(" proyecto(s) accesibles para el usuario\n");
        }

        // Métricas reales calculadas en PostgreSQL
        Map<TaskStatus, Long> byStatus = all.stream().collect(Collectors.groupingBy(Task::getStatus, Collectors.counting()));
        long total = all.size();
        long done = byStatus.getOrDefault(TaskStatus.COMPLETADA, 0L);
        sb.append("\n=== RESUMEN (datos de PostgreSQL) ===\n");
        sb.append("Total de tareas: ").append(total)
          .append(" | PENDIENTE: ").append(byStatus.getOrDefault(TaskStatus.PENDIENTE, 0L))
          .append(" | EN_PROGRESO: ").append(byStatus.getOrDefault(TaskStatus.EN_PROGRESO, 0L))
          .append(" | COMPLETADA: ").append(done)
          .append(" | Progreso: ").append(total == 0 ? 0 : Math.round(done * 100.0 / total)).append("%\n");

        if (projectId == null) {
            Map<String, List<Task>> perProject = all.stream().collect(Collectors.groupingBy(t -> t.getProject().getName()));
            perProject.forEach((name, list) -> {
                long d = list.stream().filter(t -> t.getStatus() == TaskStatus.COMPLETADA).count();
                sb.append("- Proyecto ").append(name).append(": ").append(list.size()).append(" tareas, ")
                  .append(d).append(" completadas\n");
            });
        }

        List<Task> overdue = all.stream().filter(t -> t.isOverdue(today))
                .sorted(Comparator.comparing(Task::getDueDate)).toList();
        appendList(sb, "TAREAS ATRASADAS (" + overdue.size() + ")", overdue, today);

        List<Task> open = all.stream().filter(t -> t.getStatus() != TaskStatus.COMPLETADA)
                .sorted(Comparator.comparing((Task t) -> -t.getPriority().getWeight())
                        .thenComparing(t -> t.getDueDate() == null ? LocalDate.MAX : t.getDueDate()))
                .toList();
        appendList(sb, "TAREAS ABIERTAS ordenadas por urgencia (" + open.size() + ")", open, today);

        List<Task> recentDone = all.stream().filter(t -> t.getStatus() == TaskStatus.COMPLETADA)
                .sorted(Comparator.comparing(Task::getUpdatedAt).reversed()).limit(10).toList();
        appendList(sb, "ÚLTIMAS TAREAS COMPLETADAS", recentDone, today);

        sb.append("\n=== TAREAS MÁS RELEVANTES PARA LA PREGUNTA (búsqueda semántica en Qdrant) ===\n");
        if (retrieved.isEmpty()) {
            sb.append("(ninguna tarea supera el umbral de similitud)\n");
        }
        for (SemanticSearchService.ScoredTask st : retrieved) {
            Task t = st.task();
            sb.append(line(t, today)).append(" | similitud ").append(String.format(Locale.ROOT, "%.3f", st.score())).append('\n');
            if (t.getDescription() != null) {
                String d = t.getDescription();
                sb.append("   Descripción: ").append(d.length() > 600 ? d.substring(0, 600) + "…" : d).append('\n');
            }
        }
        return sb.toString();
    }

    private void appendList(StringBuilder sb, String header, List<Task> tasks, LocalDate today) {
        sb.append("\n=== ").append(header).append(" ===\n");
        if (tasks.isEmpty()) {
            sb.append("(ninguna)\n");
            return;
        }
        tasks.stream().limit(MAX_LIST).forEach(t -> sb.append(line(t, today)).append('\n'));
        if (tasks.size() > MAX_LIST) sb.append("… y ").append(tasks.size() - MAX_LIST).append(" más\n");
    }

    private String line(Task t, LocalDate today) {
        return "#" + t.getId() + " \"" + t.getTitle() + "\" | proyecto: " + t.getProject().getName()
                + " | estado: " + t.getStatus() + " | prioridad: " + t.getPriority()
                + " | fecha límite: " + (t.getDueDate() != null ? t.getDueDate() : "sin fecha")
                + (t.isOverdue(today) ? " (ATRASADA)" : "")
                + " | responsable: " + (t.getAssignee() != null ? t.getAssignee().getFullName() : "sin asignar");
    }
}
