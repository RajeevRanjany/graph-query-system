package com.dodge.graph.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "journal_entry_items")
@Data
public class JournalEntryItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String companyCode;
    private String fiscalYear;
    private String accountingDocument;
    private String accountingDocumentItem;
    private String referenceDocument;   // links to billingDocument
    private String glAccount;
    private String customer;
    private String amountInTransactionCurrency;
    private String transactionCurrency;
    private String postingDate;
    private String clearingDate;
    private String clearingAccountingDocument;
    private String financialAccountType;
    private String profitCenter;
}
