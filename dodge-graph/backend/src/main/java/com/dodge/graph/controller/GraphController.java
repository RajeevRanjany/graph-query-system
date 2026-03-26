package com.dodge.graph.controller;

import com.dodge.graph.dto.GraphNode;
import com.dodge.graph.dto.GraphResponse;
import com.dodge.graph.service.GraphService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/graph")
@CrossOrigin(origins = "*")
public class GraphController {

    private final GraphService graphService;

    public GraphController(GraphService graphService) {
        this.graphService = graphService;
    }

    @GetMapping("/overview")
    public GraphResponse overview(@RequestParam(defaultValue = "30") int limit) {
        return graphService.getOverview(limit);
    }

    @GetMapping("/expand/{nodeId}")
    public GraphResponse expand(@PathVariable String nodeId) {
        return graphService.expand(nodeId);
    }

    @GetMapping("/node/{nodeId}")
    public GraphNode node(@PathVariable String nodeId) {
        return graphService.getNode(nodeId);
    }
}
