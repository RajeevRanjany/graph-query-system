package com.dodge.graph.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "sales_order_headers")
@Data
public class SalesOrderHeader {
    @Id
    @Column(name = "sales_order")
    private String salesOrder;
    private String salesOrderType;
    private String salesOrganization;
    private String soldToParty;
    private String creationDate;
    private String totalNetAmount;
    private String overallDeliveryStatus;
    private String overallOrdReltdBillgStatus;
    private String transactionCurrency;
    private String requestedDeliveryDate;
    private String customerPaymentTerms;
}
