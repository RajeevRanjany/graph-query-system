package com.dodge.graph.service;

import com.dodge.graph.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.*;
import java.nio.file.*;
import java.util.*;

@Service
@Slf4j
public class DataLoaderService {

    @Value("${dataset.path}")
    private String datasetPath;

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private PlatformTransactionManager txManager;

    private final ObjectMapper mapper = new ObjectMapper();

    @PostConstruct
    public void loadAll() {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.execute(status -> {
            try {
                log.info("Loading dataset from: {}", datasetPath);
                loadSalesOrderHeaders();
                loadSalesOrderItems();
                loadOutboundDeliveryHeaders();
                loadOutboundDeliveryItems();
                loadBillingDocumentHeaders();
                loadBillingDocumentItems();
                loadJournalEntries();
                loadPayments();
                loadBusinessPartners();
                loadProducts();
                loadPlants();
                log.info("Dataset loading complete.");
            } catch (Exception e) {
                log.error("Dataset loading failed", e);
                status.setRollbackOnly();
            }
            return null;
        });
    }

    private List<Path> getFiles(String folder) throws IOException {
        // If dataset.path is set, load from filesystem
        if (datasetPath != null && !datasetPath.isBlank()) {
            Path dir = Paths.get(datasetPath, folder);
            if (!Files.exists(dir)) return Collections.emptyList();
            try (var stream = Files.list(dir)) {
                return stream.filter(p -> p.toString().endsWith(".jsonl")).toList();
            }
        }
        // Otherwise load from classpath (bundled in JAR)
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:sap-o2c-data/" + folder + "/*.jsonl");
        List<Path> paths = new ArrayList<>();
        for (Resource r : resources) {
            // Copy to temp file so we can read it as a Path
            File tmp = File.createTempFile("sap_", ".jsonl");
            tmp.deleteOnExit();
            try (InputStream in = r.getInputStream(); OutputStream out = new FileOutputStream(tmp)) {
                in.transferTo(out);
            }
            paths.add(tmp.toPath());
        }
        return paths;
    }

    private void loadSalesOrderHeaders() throws Exception {
        for (Path file : getFiles("sales_order_headers")) {
            try (BufferedReader br = Files.newBufferedReader(file)) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.isBlank()) continue;
                    JsonNode n = mapper.readTree(line);
                    SalesOrderHeader e = new SalesOrderHeader();
                    e.setSalesOrder(text(n, "salesOrder"));
                    e.setSalesOrderType(text(n, "salesOrderType"));
                    e.setSalesOrganization(text(n, "salesOrganization"));
                    e.setSoldToParty(text(n, "soldToParty"));
                    e.setCreationDate(text(n, "creationDate"));
                    e.setTotalNetAmount(text(n, "totalNetAmount"));
                    e.setOverallDeliveryStatus(text(n, "overallDeliveryStatus"));
                    e.setOverallOrdReltdBillgStatus(text(n, "overallOrdReltdBillgStatus"));
                    e.setTransactionCurrency(text(n, "transactionCurrency"));
                    e.setRequestedDeliveryDate(text(n, "requestedDeliveryDate"));
                    e.setCustomerPaymentTerms(text(n, "customerPaymentTerms"));
                    em.merge(e);
                }
            }
        }
        log.info("Loaded sales_order_headers");
    }

    private void loadSalesOrderItems() throws Exception {
        for (Path file : getFiles("sales_order_items")) {
            try (BufferedReader br = Files.newBufferedReader(file)) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.isBlank()) continue;
                    JsonNode n = mapper.readTree(line);
                    SalesOrderItem e = new SalesOrderItem();
                    e.setSalesOrder(text(n, "salesOrder"));
                    e.setSalesOrderItem(text(n, "salesOrderItem"));
                    e.setMaterial(text(n, "material"));
                    e.setRequestedQuantity(text(n, "requestedQuantity"));
                    e.setRequestedQuantityUnit(text(n, "requestedQuantityUnit"));
                    e.setNetAmount(text(n, "netAmount"));
                    e.setTransactionCurrency(text(n, "transactionCurrency"));
                    e.setMaterialGroup(text(n, "materialGroup"));
                    e.setProductionPlant(text(n, "productionPlant"));
                    e.setStorageLocation(text(n, "storageLocation"));
                    em.persist(e);
                }
            }
        }
        log.info("Loaded sales_order_items");
    }

    private void loadOutboundDeliveryHeaders() throws Exception {
        for (Path file : getFiles("outbound_delivery_headers")) {
            try (BufferedReader br = Files.newBufferedReader(file)) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.isBlank()) continue;
                    JsonNode n = mapper.readTree(line);
                    OutboundDeliveryHeader e = new OutboundDeliveryHeader();
                    e.setDeliveryDocument(text(n, "deliveryDocument"));
                    e.setCreationDate(text(n, "creationDate"));
                    e.setActualGoodsMovementDate(text(n, "actualGoodsMovementDate"));
                    e.setOverallGoodsMovementStatus(text(n, "overallGoodsMovementStatus"));
                    e.setOverallPickingStatus(text(n, "overallPickingStatus"));
                    e.setShippingPoint(text(n, "shippingPoint"));
                    e.setDeliveryBlockReason(text(n, "deliveryBlockReason"));
                    e.setHeaderBillingBlockReason(text(n, "headerBillingBlockReason"));
                    em.merge(e);
                }
            }
        }
        log.info("Loaded outbound_delivery_headers");
    }

    private void loadOutboundDeliveryItems() throws Exception {
        for (Path file : getFiles("outbound_delivery_items")) {
            try (BufferedReader br = Files.newBufferedReader(file)) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.isBlank()) continue;
                    JsonNode n = mapper.readTree(line);
                    OutboundDeliveryItem e = new OutboundDeliveryItem();
                    e.setDeliveryDocument(text(n, "deliveryDocument"));
                    e.setDeliveryDocumentItem(text(n, "deliveryDocumentItem"));
                    e.setReferenceSdDocument(text(n, "referenceSdDocument"));
                    e.setReferenceSdDocumentItem(text(n, "referenceSdDocumentItem"));
                    e.setPlant(text(n, "plant"));
                    e.setStorageLocation(text(n, "storageLocation"));
                    e.setActualDeliveryQuantity(text(n, "actualDeliveryQuantity"));
                    e.setDeliveryQuantityUnit(text(n, "deliveryQuantityUnit"));
                    e.setLastChangeDate(text(n, "lastChangeDate"));
                    em.persist(e);
                }
            }
        }
        log.info("Loaded outbound_delivery_items");
    }

    private void loadBillingDocumentHeaders() throws Exception {
        for (Path file : getFiles("billing_document_headers")) {
            try (BufferedReader br = Files.newBufferedReader(file)) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.isBlank()) continue;
                    JsonNode n = mapper.readTree(line);
                    BillingDocumentHeader e = new BillingDocumentHeader();
                    e.setBillingDocument(text(n, "billingDocument"));
                    e.setBillingDocumentType(text(n, "billingDocumentType"));
                    e.setCreationDate(text(n, "creationDate"));
                    e.setBillingDocumentDate(text(n, "billingDocumentDate"));
                    e.setBillingDocumentIsCancelled(String.valueOf(n.path("billingDocumentIsCancelled").asBoolean()));
                    e.setCancelledBillingDocument(text(n, "cancelledBillingDocument"));
                    e.setTotalNetAmount(text(n, "totalNetAmount"));
                    e.setTransactionCurrency(text(n, "transactionCurrency"));
                    e.setCompanyCode(text(n, "companyCode"));
                    e.setFiscalYear(text(n, "fiscalYear"));
                    e.setAccountingDocument(text(n, "accountingDocument"));
                    e.setSoldToParty(text(n, "soldToParty"));
                    em.merge(e);
                }
            }
        }
        log.info("Loaded billing_document_headers");
    }

    private void loadBillingDocumentItems() throws Exception {
        for (Path file : getFiles("billing_document_items")) {
            try (BufferedReader br = Files.newBufferedReader(file)) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.isBlank()) continue;
                    JsonNode n = mapper.readTree(line);
                    BillingDocumentItem e = new BillingDocumentItem();
                    e.setBillingDocument(text(n, "billingDocument"));
                    e.setBillingDocumentItem(text(n, "billingDocumentItem"));
                    e.setMaterial(text(n, "material"));
                    e.setBillingQuantity(text(n, "billingQuantity"));
                    e.setBillingQuantityUnit(text(n, "billingQuantityUnit"));
                    e.setNetAmount(text(n, "netAmount"));
                    e.setTransactionCurrency(text(n, "transactionCurrency"));
                    e.setReferenceSdDocument(text(n, "referenceSdDocument"));
                    e.setReferenceSdDocumentItem(text(n, "referenceSdDocumentItem"));
                    em.persist(e);
                }
            }
        }
        log.info("Loaded billing_document_items");
    }

    private void loadJournalEntries() throws Exception {
        for (Path file : getFiles("journal_entry_items_accounts_receivable")) {
            try (BufferedReader br = Files.newBufferedReader(file)) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.isBlank()) continue;
                    JsonNode n = mapper.readTree(line);
                    JournalEntryItem e = new JournalEntryItem();
                    e.setCompanyCode(text(n, "companyCode"));
                    e.setFiscalYear(text(n, "fiscalYear"));
                    e.setAccountingDocument(text(n, "accountingDocument"));
                    e.setAccountingDocumentItem(text(n, "accountingDocumentItem"));
                    e.setReferenceDocument(text(n, "referenceDocument"));
                    e.setGlAccount(text(n, "glAccount"));
                    e.setCustomer(text(n, "customer"));
                    e.setAmountInTransactionCurrency(text(n, "amountInTransactionCurrency"));
                    e.setTransactionCurrency(text(n, "transactionCurrency"));
                    e.setPostingDate(text(n, "postingDate"));
                    e.setClearingDate(text(n, "clearingDate"));
                    e.setClearingAccountingDocument(text(n, "clearingAccountingDocument"));
                    e.setFinancialAccountType(text(n, "financialAccountType"));
                    e.setProfitCenter(text(n, "profitCenter"));
                    em.persist(e);
                }
            }
        }
        log.info("Loaded journal_entry_items");
    }

    private void loadPayments() throws Exception {
        for (Path file : getFiles("payments_accounts_receivable")) {
            try (BufferedReader br = Files.newBufferedReader(file)) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.isBlank()) continue;
                    JsonNode n = mapper.readTree(line);
                    Payment e = new Payment();
                    e.setCompanyCode(text(n, "companyCode"));
                    e.setFiscalYear(text(n, "fiscalYear"));
                    e.setAccountingDocument(text(n, "accountingDocument"));
                    e.setAccountingDocumentItem(text(n, "accountingDocumentItem"));
                    e.setClearingDate(text(n, "clearingDate"));
                    e.setClearingAccountingDocument(text(n, "clearingAccountingDocument"));
                    e.setAmountInTransactionCurrency(text(n, "amountInTransactionCurrency"));
                    e.setTransactionCurrency(text(n, "transactionCurrency"));
                    e.setCustomer(text(n, "customer"));
                    e.setInvoiceReference(text(n, "invoiceReference"));
                    e.setSalesDocument(text(n, "salesDocument"));
                    e.setPostingDate(text(n, "postingDate"));
                    e.setGlAccount(text(n, "glAccount"));
                    e.setFinancialAccountType(text(n, "financialAccountType"));
                    em.persist(e);
                }
            }
        }
        log.info("Loaded payments");
    }

    private void loadBusinessPartners() throws Exception {
        for (Path file : getFiles("business_partners")) {
            try (BufferedReader br = Files.newBufferedReader(file)) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.isBlank()) continue;
                    JsonNode n = mapper.readTree(line);
                    BusinessPartner e = new BusinessPartner();
                    e.setBusinessPartner(text(n, "businessPartner"));
                    e.setCustomer(text(n, "customer"));
                    e.setBusinessPartnerFullName(text(n, "businessPartnerFullName"));
                    e.setBusinessPartnerName(text(n, "businessPartnerName"));
                    e.setBusinessPartnerCategory(text(n, "businessPartnerCategory"));
                    e.setFirstName(text(n, "firstName"));
                    e.setLastName(text(n, "lastName"));
                    e.setOrganizationBpName1(text(n, "organizationBpName1"));
                    e.setIndustry(text(n, "industry"));
                    e.setCreationDate(text(n, "creationDate"));
                    em.merge(e);
                }
            }
        }
        log.info("Loaded business_partners");
    }

    private void loadProducts() throws Exception {
        for (Path file : getFiles("products")) {
            try (BufferedReader br = Files.newBufferedReader(file)) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.isBlank()) continue;
                    JsonNode n = mapper.readTree(line);
                    Product e = new Product();
                    e.setProduct(text(n, "product"));
                    e.setProductType(text(n, "productType"));
                    e.setProductOldId(text(n, "productOldId"));
                    e.setProductGroup(text(n, "productGroup"));
                    e.setBaseUnit(text(n, "baseUnit"));
                    e.setDivision(text(n, "division"));
                    e.setGrossWeight(text(n, "grossWeight"));
                    e.setWeightUnit(text(n, "weightUnit"));
                    e.setCreationDate(text(n, "creationDate"));
                    em.merge(e);
                }
            }
        }
        log.info("Loaded products");
    }

    private void loadPlants() throws Exception {
        for (Path file : getFiles("plants")) {
            try (BufferedReader br = Files.newBufferedReader(file)) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.isBlank()) continue;
                    JsonNode n = mapper.readTree(line);
                    Plant e = new Plant();
                    e.setPlant(text(n, "plant"));
                    e.setPlantName(text(n, "plantName"));
                    e.setSalesOrganization(text(n, "salesOrganization"));
                    e.setDistributionChannel(text(n, "distributionChannel"));
                    e.setDivision(text(n, "division"));
                    e.setLanguage(text(n, "language"));
                    em.merge(e);
                }
            }
        }
        log.info("Loaded plants");
    }

    private String text(JsonNode n, String field) {
        JsonNode v = n.path(field);
        if (v.isNull() || v.isMissingNode()) return null;
        return v.asText();
    }
}
