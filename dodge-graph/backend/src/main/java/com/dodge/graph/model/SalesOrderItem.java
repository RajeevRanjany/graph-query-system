package com.dodge.graph.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "sales_order_items")
@Data
public class SalesOrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String salesOrder;
    private String salesOrderItem;
    private String material;
    private String requestedQuantity;
    private String requestedQuantityUnit;
    private String netAmount;
    private String transactionCurrency;
    private String materialGroup;
    private String productionPlant;
    private String storageLocation;
}
