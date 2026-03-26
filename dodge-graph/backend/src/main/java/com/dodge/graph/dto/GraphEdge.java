package com.dodge.graph.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GraphEdge {
    private String id;
    private String source;
    private String target;
    private String label;  // relationship type
}
