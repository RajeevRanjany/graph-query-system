package com.dodge.graph.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "billing_document_items")
@Data
public class BillingDocumentItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String billingDocument;
    private String billingDocumentItem;
    private String material;
    private String billingQuantity;
    private String billingQuantityUnit;
    private String netAmount;
    private String transactionCurrency;
    private String referenceSdDocument;      // links to delivery document
    private String referenceSdDocumentItem;
}
