package com.dodge.graph.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.*;

@Service
@Slf4j
public class LlmQueryService {

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    @Value("${gemini.api.url}")
    private String geminiApiUrl;

    @PersistenceContext
    private EntityManager em;

    private final ObjectMapper mapper = new ObjectMapper();
    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    private static final String SCHEMA_CONTEXT = """
            You are a data analyst assistant for an SAP Order-to-Cash (O2C) system.
            You ONLY answer questions about the following dataset. Refuse any unrelated queries.
            
            DATABASE SCHEMA (SQLite):
            
            sales_order_headers(sales_order PK, sales_order_type, sales_organization, sold_to_party,
              creation_date, total_net_amount, overall_delivery_status, overall_ord_reltd_billg_status,
              transaction_currency, requested_delivery_date, customer_payment_terms)
            
            sales_order_items(id PK, sales_order FK, sales_order_item, material, requested_quantity,
              requested_quantity_unit, net_amount, transaction_currency, material_group, production_plant, storage_location)
            
            outbound_delivery_headers(delivery_document PK, creation_date, actual_goods_movement_date,
              overall_goods_movement_status, overall_picking_status, shipping_point,
              delivery_block_reason, header_billing_block_reason)
            
            outbound_delivery_items(id PK, delivery_document FK, delivery_document_item,
              reference_sd_document, reference_sd_document_item, plant, storage_location,
              actual_delivery_quantity, delivery_quantity_unit, last_change_date)
              NOTE: reference_sd_document links delivery items back to sales_order in sales_order_headers
            
            billing_document_headers(billing_document PK, billing_document_type, creation_date,
              billing_document_date, billing_document_is_cancelled, cancelled_billing_document,
              total_net_amount, transaction_currency, company_code, fiscal_year,
              accounting_document, sold_to_party)
            
            billing_document_items(id PK, billing_document FK, billing_document_item, material,
              billing_quantity, billing_quantity_unit, net_amount, transaction_currency,
              reference_sd_document, reference_sd_document_item)
              NOTE: reference_sd_document in billing_document_items links to delivery_document in outbound_delivery_headers
            
            journal_entry_items(id PK, company_code, fiscal_year, accounting_document,
              accounting_document_item, reference_document, gl_account, customer,
              amount_in_transaction_currency, transaction_currency, posting_date,
              clearing_date, clearing_accounting_document, financial_account_type, profit_center)
              NOTE: reference_document links to billing_document in billing_document_headers
            
            payments(id PK, company_code, fiscal_year, accounting_document, accounting_document_item,
              clearing_date, clearing_accounting_document, amount_in_transaction_currency,
              transaction_currency, customer, invoice_reference, sales_document, posting_date,
              gl_account, financial_account_type)
            
            business_partners(business_partner PK, customer, business_partner_full_name,
              business_partner_name, business_partner_category, first_name, last_name,
              organization_bp_name1, industry, creation_date)
            
            products(product PK, product_type, product_old_id, product_group, base_unit,
              division, gross_weight, weight_unit, creation_date)
            
            plants(plant PK, plant_name, sales_organization, distribution_channel, division, language)
            
            KEY RELATIONSHIPS (O2C flow):
            Customer (sold_to_party) → SalesOrder → SalesOrderItem → OutboundDeliveryItem (via reference_sd_document=sales_order) → OutboundDeliveryHeader → BillingDocumentItem (via reference_sd_document=delivery_document) → BillingDocumentHeader → JournalEntry (via reference_document=billing_document) → Payment (via clearing_accounting_document=accounting_document)
            
            GUARDRAIL: If the user asks anything NOT related to this O2C dataset (e.g. general knowledge, coding, weather, creative writing), respond ONLY with:
            "This system is designed to answer questions related to the provided dataset only."
            
            INSTRUCTIONS:
            1. Analyze the user question.
            2. Generate a valid SQLite SQL query to answer it.
            3. Return ONLY a JSON object in this exact format (no markdown, no extra text):
            {"sql": "<your SQL query>", "explanation": "<brief explanation of what the query does>"}
            
            Use LIMIT clauses (max 100 rows) to avoid huge results.
            Use proper JOINs based on the relationships above.
            For "trace full flow" queries, use multiple JOINs across the O2C chain.
            For "broken flow" queries, use LEFT JOINs and check for NULLs.
            """;

    @Transactional(readOnly = true)
    public Map<String, Object> query(String userQuestion, List<Map<String, String>> history) {
        // Guardrail check first
        if (isOffTopic(userQuestion)) {
            return Map.of("answer", "This system is designed to answer questions related to the provided dataset only.",
                    "sql", "", "rows", List.of());
        }

        try {
            // Step 1: Ask LLM to generate SQL
            String sqlJson = callGemini(buildSqlPrompt(userQuestion, history));
            if (sqlJson == null) {
                return Map.of("answer", "Failed to connect to LLM. Please check your API key.", "sql", "", "rows", List.of());
            }

            // Parse SQL from response
            String sql = extractSql(sqlJson);
            String explanation = extractExplanation(sqlJson);

            if (sql == null || sql.isBlank()) {
                // LLM returned guardrail message
                return Map.of("answer", sqlJson.contains("dataset only") ?
                        "This system is designed to answer questions related to the provided dataset only." :
                        "I could not generate a valid query for that question.", "sql", "", "rows", List.of());
            }

            // Step 2: Execute SQL
            List<Map<String, Object>> rows = executeQuery(sql);

            // Step 3: Ask LLM to summarize results
            String answer = callGemini(buildAnswerPrompt(userQuestion, sql, rows));
            if (answer == null) answer = "Query executed successfully. " + rows.size() + " rows returned.";

            return Map.of("answer", answer, "sql", sql, "explanation", explanation != null ? explanation : "", "rows", rows);

        } catch (Exception e) {
            log.error("Query error", e);
            return Map.of("answer", "An error occurred: " + e.getMessage(), "sql", "", "rows", List.of());
        }
    }

    private String buildSqlPrompt(String question, List<Map<String, String>> history) {
        StringBuilder sb = new StringBuilder(SCHEMA_CONTEXT);
        sb.append("\n\nConversation history:\n");
        if (history != null) {
            for (Map<String, String> msg : history) {
                sb.append(msg.get("role")).append(": ").append(msg.get("content")).append("\n");
            }
        }
        sb.append("\nUser question: ").append(question);
        sb.append("\n\nGenerate the SQL query JSON now:");
        return sb.toString();
    }

    private String buildAnswerPrompt(String question, String sql, List<Map<String, Object>> rows) {
        String rowsStr = rows.isEmpty() ? "No rows returned." :
                rows.subList(0, Math.min(rows.size(), 20)).toString();
        return String.format("""
                You are a data analyst for an SAP O2C system.
                The user asked: "%s"
                SQL executed: %s
                Results (%d rows total, showing up to 20): %s
                
                Provide a clear, concise natural language answer based on the data.
                Be specific with numbers and names from the results.
                Do not make up information not in the results.
                Keep the answer under 200 words.
                """, question, sql, rows.size(), rowsStr);
    }

    private List<Map<String, Object>> executeQuery(String sql) {
        try {
            // Safety: only allow SELECT
            String trimmed = sql.trim().toUpperCase();
            if (!trimmed.startsWith("SELECT")) {
                return List.of(Map.of("error", "Only SELECT queries are allowed"));
            }

            var query = em.createNativeQuery(sql);
            query.setMaxResults(100);
            List<?> results = query.getResultList();

            List<Map<String, Object>> rows = new ArrayList<>();
            for (Object row : results) {
                if (row instanceof Object[] arr) {
                    Map<String, Object> map = new LinkedHashMap<>();
                    for (int i = 0; i < arr.length; i++) {
                        map.put("col" + i, arr[i]);
                    }
                    rows.add(map);
                } else {
                    rows.add(Map.of("value", row != null ? row : "null"));
                }
            }
            return rows;
        } catch (Exception e) {
            log.error("SQL execution error: {}", e.getMessage());
            return List.of(Map.of("error", "SQL error: " + e.getMessage()));
        }
    }

    private String callGemini(String prompt) {
        try {
            String body = mapper.writeValueAsString(Map.of(
                    "contents", List.of(Map.of(
                            "parts", List.of(Map.of("text", prompt))
                    ))
            ));

            Request request = new Request.Builder()
                    .url(geminiApiUrl + "?key=" + geminiApiKey)
                    .post(RequestBody.create(body, MediaType.parse("application/json")))
                    .build();

            try (Response response = http.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.error("Gemini API error: {} {}", response.code(), response.message());
                    return null;
                }
                String responseBody = response.body().string();
                JsonNode json = mapper.readTree(responseBody);
                return json.path("candidates").get(0)
                        .path("content").path("parts").get(0)
                        .path("text").asText();
            }
        } catch (Exception e) {
            log.error("Gemini call failed", e);
            return null;
        }
    }

    private String extractSql(String llmResponse) {
        if (llmResponse == null) return null;
        try {
            // Try to parse as JSON directly
            String cleaned = llmResponse.trim();
            // Remove markdown code blocks if present
            cleaned = cleaned.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            JsonNode node = mapper.readTree(cleaned);
            return node.path("sql").asText();
        } catch (Exception e) {
            // Try regex fallback
            Pattern p = Pattern.compile("\"sql\"\\s*:\\s*\"(.*?)\"", Pattern.DOTALL);
            Matcher m = p.matcher(llmResponse);
            if (m.find()) return m.group(1).replace("\\n", " ").replace("\\\"", "\"");
            return null;
        }
    }

    private String extractExplanation(String llmResponse) {
        if (llmResponse == null) return null;
        try {
            String cleaned = llmResponse.trim()
                    .replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            JsonNode node = mapper.readTree(cleaned);
            return node.path("explanation").asText();
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isOffTopic(String question) {
        String q = question.toLowerCase();
        // Simple keyword guardrail — LLM will also enforce this
        String[] offTopicKeywords = {
                "write a poem", "write a story", "tell me a joke", "what is the capital",
                "who is the president", "weather", "recipe", "translate", "explain quantum",
                "write code", "help me code", "what is java", "what is python"
        };
        for (String kw : offTopicKeywords) {
            if (q.contains(kw)) return true;
        }
        return false;
    }
}
