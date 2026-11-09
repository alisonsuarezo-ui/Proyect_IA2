package com.taskflow.controller;

import com.taskflow.ai.AiStatusService;
import com.taskflow.ai.RagService;
import com.taskflow.ai.SemanticSearchService;
import com.taskflow.config.SecurityUtils;
import com.taskflow.dto.AiDtos.AiStatus;
import com.taskflow.dto.AiDtos.RagRequest;
import com.taskflow.dto.AiDtos.RagResponse;
import com.taskflow.dto.AiDtos.SearchResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final SemanticSearchService searchService;
    private final RagService ragService;
    private final AiStatusService statusService;

    public AiController(SemanticSearchService searchService, RagService ragService, AiStatusService statusService) {
        this.searchService = searchService;
        this.ragService = ragService;
        this.statusService = statusService;
    }

    @GetMapping("/status")
    public AiStatus status() {
        return statusService.status();
    }

    /** Búsqueda inteligente (semántica) de tareas. */
    @GetMapping("/search")
    public List<SearchResult> search(@RequestParam String q,
                                     @RequestParam(required = false) Long projectId,
                                     @RequestParam(required = false) Integer limit) {
        return searchService.search(q, projectId, limit, SecurityUtils.currentUser());
    }

    /** Consulta RAG en lenguaje natural sobre las tareas del usuario. */
    @PostMapping("/ask")
    public RagResponse ask(@Valid @RequestBody RagRequest req) {
        return ragService.ask(req.question().trim(), req.projectId(), SecurityUtils.currentUser());
    }
}
