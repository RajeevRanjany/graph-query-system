# SAP O2C Graph Explorer

A graph-based data modeling and query system for SAP Order-to-Cash (O2C) data, with an LLM-powered natural language query interface.

## Architecture

```
Frontend (React + ReactFlow)  ←→  Backend (Spring Boot Java)  ←→  SQLite DB
                                          ↕
                                   Gemini 1.5 Flash (LLM)
```

### Backend — Spring Boot (Java 21)
- **Framework**: Spring Boot 3.2 with Spring Data JPA
- **Database**: SQLite via `sqlite-jdbc` + Hibernate Community Dialects
- **Graph Logic**: In-memory graph traversal using JGraphT; nodes/edges built on-demand from relational queries
- **LLM Integration**: Google Gemini 1.5 Flash via OkHttp REST calls
- **Data Loading**: `DataLoaderService` reads all `.jsonl` files at startup and persists to SQLite

### Frontend — React + Vite
- **Graph Visualization**: `@xyflow/react` (ReactFlow) — interactive node expansion, drag, zoom, minimap
- **Chat UI**: Conversation history, suggestion chips, SQL display
- **API**: Axios calls to Spring Boot REST endpoints

## Graph Model

### Nodes
| Type | Key Field |
|------|-----------|
| CUSTOMER | soldToParty |
| SALES_ORDER | salesOrder |
| SO_ITEM | salesOrder + item |
| DELIVERY | deliveryDocument |
| BILLING | billingDocument |
| JOURNAL | accountingDocument |
| PAYMENT | accountingDocument |
| PRODUCT | material |
| PLANT | plant |

### Edges (O2C Flow)
```
CUSTOMER → SALES_ORDER (PLACED)
SALES_ORDER → SO_ITEM (HAS_ITEM)
SO_ITEM → PRODUCT (REFERENCES_PRODUCT)
SALES_ORDER → DELIVERY (DELIVERED_VIA)  [via outbound_delivery_items.referenceSdDocument]
DELIVERY → PLANT (SHIPPED_FROM)
DELIVERY → BILLING (BILLED_AS)  [via billing_document_items.referenceSdDocument]
BILLING → JOURNAL (POSTED_TO)  [via journal_entry_items.referenceDocument]
JOURNAL → PAYMENT (CLEARED_BY)  [via payments.clearingAccountingDocument]
```

## Database Choice: SQLite

SQLite was chosen because:
- Zero-config, file-based — no separate DB server needed for demo
- Full SQL support for complex JOIN queries across the O2C chain
- JPA/Hibernate integration via community dialect
- Sufficient for the dataset size (~tens of thousands of records)

For production, PostgreSQL or Neo4j would be more appropriate.

## LLM Prompting Strategy

Two-step approach:
1. **SQL Generation**: Send schema context + user question → LLM returns `{"sql": "...", "explanation": "..."}` JSON
2. **Answer Synthesis**: Send question + SQL + result rows → LLM returns natural language answer

The schema prompt includes:
- Full table definitions with column names
- Key relationship descriptions (which FK links to which PK)
- Explicit O2C flow chain
- Instruction to return only JSON (no markdown)

## Guardrails

Two-layer guardrail system:
1. **Keyword filter** (Java): Fast pre-check for obvious off-topic keywords (poems, weather, coding help, etc.)
2. **LLM-enforced**: System prompt explicitly instructs Gemini to return the guardrail message for non-dataset queries
3. **SQL safety**: Only `SELECT` statements are executed; `setMaxResults(100)` prevents runaway queries

## Running Locally

### Prerequisites
- Java 21+
- Gradle 8+
- Node 18+
- Gemini API key (free at https://ai.google.dev)

### Backend
```bash
cd dodge-graph/backend
export GEMINI_API_KEY=your_key_here
gradle bootRun
# Server starts at http://localhost:8080
# Dataset loads automatically from ../sap-o2c-data/
```

### Frontend
```bash
cd dodge-graph/frontend
npm install
npm run dev
# UI at http://localhost:5173
```

## Example Queries

- "Which products are associated with the highest number of billing documents?"
- "Trace the full flow of billing document 90504298"
- "Find sales orders that were delivered but not billed"
- "Show top 10 customers by total order value"
- "Which plants handle the most deliveries?"

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/graph/overview?limit=30` | Sample graph for initial view |
| GET | `/api/graph/expand/{nodeId}` | Expand a node's neighbours |
| GET | `/api/graph/node/{nodeId}` | Get single node details |
| POST | `/api/chat/query` | NL query → SQL → answer |
