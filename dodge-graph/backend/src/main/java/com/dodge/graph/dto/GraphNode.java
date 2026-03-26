package com.dodge.graph.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GraphNode {
    private String id;
    private String type;   // SALES_ORDER, DELIVERY, BILLING, JOURNAL, PAYMENT, CUSTOMER, PRODUCT, PLANT
    private String label;
    private Map<String, Object> properties;
}
