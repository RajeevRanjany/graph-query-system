package com.dodge.graph.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GraphResponse {
    private List<GraphNode> nodes;
    private List<GraphEdge> edges;
}
