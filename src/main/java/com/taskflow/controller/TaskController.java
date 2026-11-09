package com.taskflow.controller;

import com.taskflow.ai.SemanticSearchService;
import com.taskflow.config.SecurityUtils;
import com.taskflow.dto.AiDtos.SearchResult;
import com.taskflow.dto.TaskDtos.*;
import com.taskflow.entity.Priority;
import com.taskflow.entity.TaskStatus;
import com.taskflow.service.CommentService;
import com.taskflow.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;
    private final CommentService commentService;
    private final SemanticSearchService searchService;

    public TaskController(TaskService taskService, CommentService commentService, SemanticSearchService searchService) {
        this.taskService = taskService;
        this.commentService = commentService;
        this.searchService = searchService;
    }

    @GetMapping
    public List<TaskDto> list(@RequestParam(required = false) Long projectId,
                              @RequestParam(required = false) TaskStatus status,
                              @RequestParam(required = false) Priority priority,
                              @RequestParam(required = false) Long assigneeId,
                              @RequestParam(required = false) String q,
                              @RequestParam(required = false) Boolean overdue) {
        return taskService.list(SecurityUtils.currentUser(), projectId, status, priority, assigneeId, q, overdue);
    }

    @GetMapping("/{id}")
    public TaskDto get(@PathVariable Long id) {
        return taskService.get(id, SecurityUtils.currentUser());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskDto create(@Valid @RequestBody TaskRequest req) {
        return taskService.create(req, SecurityUtils.currentUser());
    }

    @PutMapping("/{id}")
    public TaskDto update(@PathVariable Long id, @Valid @RequestBody TaskRequest req) {
        return taskService.update(id, req, SecurityUtils.currentUser());
    }

    @PatchMapping("/{id}/status")
    public TaskDto updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest req) {
        return taskService.updateStatus(id, req, SecurityUtils.currentUser());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        taskService.delete(id, SecurityUtils.currentUser());
    }

    @GetMapping("/{id}/comments")
    public List<CommentDto> comments(@PathVariable Long id) {
        return commentService.list(id, SecurityUtils.currentUser());
    }

    @PostMapping("/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentDto addComment(@PathVariable Long id, @Valid @RequestBody CommentRequest req) {
        return commentService.add(id, req, SecurityUtils.currentUser());
    }

    /** Tareas semánticamente similares (vector de la tarea en Qdrant). */
    @GetMapping("/{id}/similar")
    public List<SearchResult> similar(@PathVariable Long id, @RequestParam(required = false) Integer limit) {
        return searchService.similar(id, limit, SecurityUtils.currentUser());
    }
}
