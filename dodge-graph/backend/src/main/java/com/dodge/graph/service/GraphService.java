package com.dodge.graph.service;

import com.dodge.graph.dto.GraphEdge;
import com.dodge.graph.dto.GraphNode;
import com.dodge.graph.dto.GraphResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Builds graph subsets on demand from the SQLite database.
 * Each node has an id = "<TYPE>_<key>", e.g. "SO_740506"
 */
@Service
@Slf4j
public class GraphService {

    @PersistenceContext
    private EntityManager em;

    // ── Overview graph: sample of top-level nodes ──────────────────────────

    @Transactional(readOnly = true)
    public GraphResponse getOverview(int limit) {
        List<GraphNode> nodes = new ArrayList<>();
        List<GraphEdge> edges = new ArrayList<>();

        // Sample sales orders
        List<?> orders = em.createNativeQuery(
                "SELECT sales_order, sold_to_party, total_net_amount, transaction_currency FROM sales_order_headers LIMIT ?1"
        ).setParameter(1, limit).getResultList();

        for (Object row : orders) {
            Object[] r = (Object[]) row;
            String soId = "SO_" + r[0];
            nodes.add(new GraphNode(soId, "SALES_ORDER", "SO " + r[0],
                    Map.of("salesOrder", str(r[0]), "customer", str(r[1]),
                            "amount", str(r[2]), "currency", str(r[3]))));

            // Customer node
            String custId = "CUST_" + r[1];
            nodes.add(new GraphNode(custId, "CUSTOMER", "Customer " + r[1],
                    Map.of("customerId", str(r[1]))));
            edges.add(new GraphEdge(custId + "_" + soId, custId, soId, "PLACED"));
        }

        dedup(nodes);
        return new GraphResponse(nodes, edges);
    }

    // ── Expand a node: return its direct neighbours ─────────────────────────

    @Transactional(readOnly = true)
    public GraphResponse expand(String nodeId) {
        List<GraphNode> nodes = new ArrayList<>();
        List<GraphEdge> edges = new ArrayList<>();

        String[] parts = nodeId.split("_", 2);
        if (parts.length < 2) return new GraphResponse(nodes, edges);
        String type = parts[0];
        String key = parts[1];

        switch (type) {
            case "SO" -> expandSalesOrder(key, nodes, edges);
            case "CUST" -> expandCustomer(key, nodes, edges);
            case "DEL" -> expandDelivery(key, nodes, edges);
            case "BILL" -> expandBilling(key, nodes, edges);
            case "JRNL" -> expandJournal(key, nodes, edges);
            case "PROD" -> expandProduct(key, nodes, edges);
            case "PLANT" -> expandPlant(key, nodes, edges);
        }

        dedup(nodes);
        return new GraphResponse(nodes, edges);
    }

    // ── Node detail ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public GraphNode getNode(String nodeId) {
        String[] parts = nodeId.split("_", 2);
        if (parts.length < 2) return null;
        String type = parts[0];
        String key = parts[1];

        return switch (type) {
            case "SO" -> fetchSalesOrderNode(key);
            case "CUST" -> fetchCustomerNode(key);
            case "DEL" -> fetchDeliveryNode(key);
            case "BILL" -> fetchBillingNode(key);
            case "PROD" -> fetchProductNode(key);
            case "PLANT" -> fetchPlantNode(key);
            default -> null;
        };
    }

    // ── Expand implementations ───────────────────────────────────────────────

    private void expandSalesOrder(String soId, List<GraphNode> nodes, List<GraphEdge> edges) {
        // SO node itself
        GraphNode soNode = fetchSalesOrderNode(soId);
        if (soNode != null) nodes.add(soNode);

        // SO Items → Products
        List<?> items = em.createNativeQuery(
                "SELECT sales_order_item, material, net_amount FROM sales_order_items WHERE sales_order = ?1"
        ).setParameter(1, soId).getResultList();

        for (Object row : items) {
            Object[] r = (Object[]) row;
            String itemId = "SOI_" + soId + "_" + r[0];
            nodes.add(new GraphNode(itemId, "SO_ITEM", "Item " + r[0],
                    Map.of("salesOrder", soId, "item", str(r[0]), "material", str(r[1]), "amount", str(r[2]))));
            edges.add(new GraphEdge("SO_" + soId + "_" + itemId, "SO_" + soId, itemId, "HAS_ITEM"));

            if (r[1] != null && !str(r[1]).isBlank()) {
                String prodId = "PROD_" + r[1];
                nodes.add(new GraphNode(prodId, "PRODUCT", "Product " + r[1], Map.of("product", str(r[1]))));
                edges.add(new GraphEdge(itemId + "_" + prodId, itemId, prodId, "REFERENCES_PRODUCT"));
            }
        }

        // Deliveries linked to this SO
        List<?> delItems = em.createNativeQuery(
                "SELECT DISTINCT delivery_document FROM outbound_delivery_items WHERE reference_sd_document = ?1"
        ).setParameter(1, soId).getResultList();

        for (Object row : delItems) {
            String delId = "DEL_" + row;
            nodes.add(new GraphNode(delId, "DELIVERY", "Delivery " + row, Map.of("deliveryDocument", str(row))));
            edges.add(new GraphEdge("SO_" + soId + "_" + delId, "SO_" + soId, delId, "DELIVERED_VIA"));
        }
    }

    private void expandCustomer(String custId, List<GraphNode> nodes, List<GraphEdge> edges) {
        GraphNode custNode = fetchCustomerNode(custId);
        if (custNode != null) nodes.add(custNode);

        List<?> orders = em.createNativeQuery(
                "SELECT sales_order, total_net_amount, transaction_currency FROM sales_order_headers WHERE sold_to_party = ?1 LIMIT 20"
        ).setParameter(1, custId).getResultList();

        for (Object row : orders) {
            Object[] r = (Object[]) row;
            String soId = "SO_" + r[0];
            nodes.add(new GraphNode(soId, "SALES_ORDER", "SO " + r[0],
                    Map.of("salesOrder", str(r[0]), "amount", str(r[1]), "currency", str(r[2]))));
            edges.add(new GraphEdge("CUST_" + custId + "_" + soId, "CUST_" + custId, soId, "PLACED"));
        }
    }

    private void expandDelivery(String delId, List<GraphNode> nodes, List<GraphEdge> edges) {
        GraphNode delNode = fetchDeliveryNode(delId);
        if (delNode != null) nodes.add(delNode);

        // Delivery items → SO reference + Plant
        List<?> items = em.createNativeQuery(
                "SELECT delivery_document_item, reference_sd_document, plant FROM outbound_delivery_items WHERE delivery_document = ?1"
        ).setParameter(1, delId).getResultList();

        Set<String> linkedSOs = new HashSet<>();
        Set<String> linkedPlants = new HashSet<>();

        for (Object row : items) {
            Object[] r = (Object[]) row;
            if (r[1] != null && !str(r[1]).isBlank()) linkedSOs.add(str(r[1]));
            if (r[2] != null && !str(r[2]).isBlank()) linkedPlants.add(str(r[2]));
        }

        for (String so : linkedSOs) {
            nodes.add(new GraphNode("SO_" + so, "SALES_ORDER", "SO " + so, Map.of("salesOrder", so)));
            edges.add(new GraphEdge("DEL_" + delId + "_SO_" + so, "SO_" + so, "DEL_" + delId, "DELIVERED_VIA"));
        }

        for (String plant : linkedPlants) {
            String plantId = "PLANT_" + plant;
            nodes.add(new GraphNode(plantId, "PLANT", "Plant " + plant, Map.of("plant", plant)));
            edges.add(new GraphEdge("DEL_" + delId + "_" + plantId, "DEL_" + delId, plantId, "SHIPPED_FROM"));
        }

        // Billing documents referencing this delivery
        List<?> bills = em.createNativeQuery(
                "SELECT DISTINCT billing_document FROM billing_document_items WHERE reference_sd_document = ?1"
        ).setParameter(1, delId).getResultList();

        for (Object row : bills) {
            String billId = "BILL_" + row;
            nodes.add(new GraphNode(billId, "BILLING", "Bill " + row, Map.of("billingDocument", str(row))));
            edges.add(new GraphEdge("DEL_" + delId + "_" + billId, "DEL_" + delId, billId, "BILLED_AS"));
        }
    }

    private void expandBilling(String billId, List<GraphNode> nodes, List<GraphEdge> edges) {
        GraphNode billNode = fetchBillingNode(billId);
        if (billNode != null) nodes.add(billNode);

        // Journal entries
        List<?> journals = em.createNativeQuery(
                "SELECT accounting_document, amount_in_transaction_currency, posting_date FROM journal_entry_items WHERE reference_document = ?1"
        ).setParameter(1, billId).getResultList();

        for (Object row : journals) {
            Object[] r = (Object[]) row;
            String jrnlId = "JRNL_" + r[0];
            nodes.add(new GraphNode(jrnlId, "JOURNAL", "Journal " + r[0],
                    Map.of("accountingDocument", str(r[0]), "amount", str(r[1]), "postingDate", str(r[2]))));
            edges.add(new GraphEdge("BILL_" + billId + "_" + jrnlId, "BILL_" + billId, jrnlId, "POSTED_TO"));
        }

        // Delivery items that reference this billing doc
        List<?> delItems = em.createNativeQuery(
                "SELECT DISTINCT reference_sd_document FROM billing_document_items WHERE billing_document = ?1"
        ).setParameter(1, billId).getResultList();

        for (Object row : delItems) {
            if (row != null && !str(row).isBlank()) {
                String delId = "DEL_" + row;
                nodes.add(new GraphNode(delId, "DELIVERY", "Delivery " + row, Map.of("deliveryDocument", str(row))));
                edges.add(new GraphEdge(delId + "_BILL_" + billId, delId, "BILL_" + billId, "BILLED_AS"));
            }
        }
    }

    private void expandJournal(String jrnlId, List<GraphNode> nodes, List<GraphEdge> edges) {
        // Payments clearing this journal
        List<?> payments = em.createNativeQuery(
                "SELECT accounting_document, amount_in_transaction_currency, clearing_date FROM payments WHERE clearing_accounting_document = ?1"
        ).setParameter(1, jrnlId).getResultList();

        for (Object row : payments) {
            Object[] r = (Object[]) row;
            String payId = "PAY_" + r[0];
            nodes.add(new GraphNode(payId, "PAYMENT", "Payment " + r[0],
                    Map.of("accountingDocument", str(r[0]), "amount", str(r[1]), "clearingDate", str(r[2]))));
            edges.add(new GraphEdge("JRNL_" + jrnlId + "_" + payId, "JRNL_" + jrnlId, payId, "CLEARED_BY"));
        }
    }

    private void expandProduct(String prodId, List<GraphNode> nodes, List<GraphEdge> edges) {
        GraphNode prodNode = fetchProductNode(prodId);
        if (prodNode != null) nodes.add(prodNode);

        List<?> items = em.createNativeQuery(
                "SELECT DISTINCT sales_order FROM sales_order_items WHERE material = ?1 LIMIT 10"
        ).setParameter(1, prodId).getResultList();

        for (Object row : items) {
            String soId = "SO_" + row;
            nodes.add(new GraphNode(soId, "SALES_ORDER", "SO " + row, Map.of("salesOrder", str(row))));
            edges.add(new GraphEdge("PROD_" + prodId + "_" + soId, "PROD_" + prodId, soId, "ORDERED_IN"));
        }
    }

    private void expandPlant(String plantId, List<GraphNode> nodes, List<GraphEdge> edges) {
        GraphNode plantNode = fetchPlantNode(plantId);
        if (plantNode != null) nodes.add(plantNode);

        List<?> dels = em.createNativeQuery(
                "SELECT DISTINCT delivery_document FROM outbound_delivery_items WHERE plant = ?1 LIMIT 10"
        ).setParameter(1, plantId).getResultList();

        for (Object row : dels) {
            String delId = "DEL_" + row;
            nodes.add(new GraphNode(delId, "DELIVERY", "Delivery " + row, Map.of("deliveryDocument", str(row))));
            edges.add(new GraphEdge("PLANT_" + plantId + "_" + delId, "PLANT_" + plantId, delId, "SHIPS"));
        }
    }

    // ── Fetch single node helpers ────────────────────────────────────────────

    private GraphNode fetchSalesOrderNode(String soId) {
        List<?> r = em.createNativeQuery(
                "SELECT sales_order, sold_to_party, total_net_amount, transaction_currency, overall_delivery_status FROM sales_order_headers WHERE sales_order = ?1"
        ).setParameter(1, soId).getResultList();
        if (r.isEmpty()) return null;
        Object[] row = (Object[]) r.get(0);
        return new GraphNode("SO_" + soId, "SALES_ORDER", "SO " + soId,
                Map.of("salesOrder", str(row[0]), "customer", str(row[1]),
                        "amount", str(row[2]), "currency", str(row[3]), "deliveryStatus", str(row[4])));
    }

    private GraphNode fetchCustomerNode(String custId) {
        List<?> r = em.createNativeQuery(
                "SELECT business_partner, business_partner_full_name, organization_bp_name1 FROM business_partners WHERE customer = ?1"
        ).setParameter(1, custId).getResultList();
        if (r.isEmpty()) return new GraphNode("CUST_" + custId, "CUSTOMER", "Customer " + custId, Map.of("customerId", custId));
        Object[] row = (Object[]) r.get(0);
        String name = str(row[2]) != null && !str(row[2]).isBlank() ? str(row[2]) : str(row[1]);
        return new GraphNode("CUST_" + custId, "CUSTOMER", name != null ? name : "Customer " + custId,
                Map.of("customerId", custId, "name", name != null ? name : ""));
    }

    private GraphNode fetchDeliveryNode(String delId) {
        List<?> r = em.createNativeQuery(
                "SELECT delivery_document, overall_goods_movement_status, actual_goods_movement_date FROM outbound_delivery_headers WHERE delivery_document = ?1"
        ).setParameter(1, delId).getResultList();
        if (r.isEmpty()) return new GraphNode("DEL_" + delId, "DELIVERY", "Delivery " + delId, Map.of("deliveryDocument", delId));
        Object[] row = (Object[]) r.get(0);
        return new GraphNode("DEL_" + delId, "DELIVERY", "Delivery " + delId,
                Map.of("deliveryDocument", str(row[0]), "goodsMovementStatus", str(row[1]), "goodsMovementDate", str(row[2])));
    }

    private GraphNode fetchBillingNode(String billId) {
        List<?> r = em.createNativeQuery(
                "SELECT billing_document, billing_document_type, total_net_amount, transaction_currency, sold_to_party FROM billing_document_headers WHERE billing_document = ?1"
        ).setParameter(1, billId).getResultList();
        if (r.isEmpty()) return new GraphNode("BILL_" + billId, "BILLING", "Bill " + billId, Map.of("billingDocument", billId));
        Object[] row = (Object[]) r.get(0);
        return new GraphNode("BILL_" + billId, "BILLING", "Bill " + billId,
                Map.of("billingDocument", str(row[0]), "type", str(row[1]),
                        "amount", str(row[2]), "currency", str(row[3]), "customer", str(row[4])));
    }

    private GraphNode fetchProductNode(String prodId) {
        List<?> r = em.createNativeQuery(
                "SELECT product, product_old_id, product_group, base_unit FROM products WHERE product = ?1"
        ).setParameter(1, prodId).getResultList();
        if (r.isEmpty()) return new GraphNode("PROD_" + prodId, "PRODUCT", "Product " + prodId, Map.of("product", prodId));
        Object[] row = (Object[]) r.get(0);
        String label = str(row[1]) != null && !str(row[1]).isBlank() ? str(row[1]) : "Product " + prodId;
        return new GraphNode("PROD_" + prodId, "PRODUCT", label,
                Map.of("product", str(row[0]), "oldId", str(row[1]) != null ? str(row[1]) : "",
                        "group", str(row[2]) != null ? str(row[2]) : "", "unit", str(row[3]) != null ? str(row[3]) : ""));
    }

    private GraphNode fetchPlantNode(String plantId) {
        List<?> r = em.createNativeQuery(
                "SELECT plant, plant_name FROM plants WHERE plant = ?1"
        ).setParameter(1, plantId).getResultList();
        if (r.isEmpty()) return new GraphNode("PLANT_" + plantId, "PLANT", "Plant " + plantId, Map.of("plant", plantId));
        Object[] row = (Object[]) r.get(0);
        return new GraphNode("PLANT_" + plantId, "PLANT", str(row[1]) != null ? str(row[1]) : "Plant " + plantId,
                Map.of("plant", str(row[0]), "name", str(row[1]) != null ? str(row[1]) : ""));
    }

    // ── Utility ──────────────────────────────────────────────────────────────

    private void dedup(List<GraphNode> nodes) {
        Set<String> seen = new HashSet<>();
        nodes.removeIf(n -> !seen.add(n.getId()));
    }

    private String str(Object o) {
        return o == null ? "" : o.toString();
    }
}
