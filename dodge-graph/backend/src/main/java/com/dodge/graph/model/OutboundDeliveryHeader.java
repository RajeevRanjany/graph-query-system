package com.dodge.graph.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "outbound_delivery_headers")
@Data
public class OutboundDeliveryHeader {
    @Id
    @Column(name = "delivery_document")
    private String deliveryDocument;
    private String creationDate;
    private String actualGoodsMovementDate;
    private String overallGoodsMovementStatus;
    private String overallPickingStatus;
    private String shippingPoint;
    private String deliveryBlockReason;
    private String headerBillingBlockReason;
}
