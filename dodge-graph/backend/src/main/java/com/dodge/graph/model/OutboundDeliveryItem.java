package com.dodge.graph.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "outbound_delivery_items")
@Data
public class OutboundDeliveryItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String deliveryDocument;
    private String deliveryDocumentItem;
    private String referenceSdDocument;      // links to salesOrder
    private String referenceSdDocumentItem;  // links to salesOrderItem
    private String plant;
    private String storageLocation;
    private String actualDeliveryQuantity;
    private String deliveryQuantityUnit;
    private String lastChangeDate;
}
