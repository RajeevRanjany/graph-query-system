package com.dodge.graph.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "billing_document_headers")
@Data
public class BillingDocumentHeader {
    @Id
    @Column(name = "billing_document")
    private String billingDocument;
    private String billingDocumentType;
    private String creationDate;
    private String billingDocumentDate;
    private String billingDocumentIsCancelled;
    private String cancelledBillingDocument;
    private String totalNetAmount;
    private String transactionCurrency;
    private String companyCode;
    private String fiscalYear;
    private String accountingDocument;
    private String soldToParty;
}
