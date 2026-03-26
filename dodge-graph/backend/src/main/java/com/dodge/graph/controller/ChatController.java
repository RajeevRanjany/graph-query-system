package com.dodge.graph.controller;

import com.dodge.graph.service.LlmQueryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatController {

    private final LlmQueryService llmQueryService;

    public ChatController(LlmQueryService llmQueryService) {
        this.llmQueryService = llmQueryService;
    }

    @PostMapping("/query")
    public Map<String, Object> query(@RequestBody ChatRequest request) {
        return llmQueryService.query(request.question(), request.history());
    }

    public record ChatRequest(String question, List<Map<String, String>> history) {}
}
