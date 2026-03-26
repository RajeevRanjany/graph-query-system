package com.dodge.graph.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "payments")
@Data
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String companyCode;
    private String fiscalYear;
    private String accountingDocument;
    private String accountingDocumentItem;
    private String clearingDate;
    private String clearingAccountingDocument;
    private String amountInTransactionCurrency;
    private String transactionCurrency;
    private String customer;
    private String invoiceReference;
    private String salesDocument;
    private String postingDate;
    private String glAccount;
    private String financialAccountType;
}
