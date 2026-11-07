# High-Throughput Flash Sale & Inventory Reservation System

A production-grade, distributed flash sale system designed to solve the "thundering herd" problem where thousands of concurrent shoppers attempt to purchase limited inventory simultaneously.

---

## Architecture Overview

```
[ JavaFX Client / Concurrency Studio ]
                 │
                 ▼  (REST API / JWT)
     [ Spring Boot 3 Backend ]
        │                   │
        ▼                   ▼
  [ Redis 7 ]         [ PostgreSQL 16 ]
(Atomic Lua Engine)  (ACID Durable Ledger)
```

1. **In-Memory Admission Barrier (Redis + Lua)**:
   - Evaluates incoming reservation requests in memory in $<1\text{ ms}$.
   - Executes atomic Lua scripts (`reserve_stock.lua`) to guarantee zero overselling without acquiring database locks.
   - Out-of-stock requests are rejected at the gate without touching PostgreSQL.
2. **Durable Ledger (PostgreSQL)**:
   - Holds the source of truth for completed orders, users, and audit records.
   - Enforces the mathematical inventory conservation invariant:
     $$\text{Total Stock} = \text{Available Stock} + \text{Reserved Stock} + \text{Sold Stock}$$
3. **60-Second Hold Window & Expiration Worker**:
   - Admitted reservations hold stock for 60 seconds to allow checkout.
   - Background worker restores inventory back into Redis and PostgreSQL if payment is not completed before timeout.
4. **Desktop GUI & Concurrency Studio (JavaFX)**:
   - Provides a luxury shopper storefront with live inventory tracking.
   - Built-in multi-threaded burst simulator to demonstrate 1,000+ concurrent requests, latency metrics, and overselling protection.

---

## Prerequisites

- **Java 21** or later
- **Docker** and **Docker Compose**
- *(Maven is optional; the project includes `./mvnw` wrappers)*

---

## Quick Start (Run for the First Time)

### 1. Start Infrastructure (PostgreSQL & Redis)
Navigate to `flash-sale-system/`:
```bash
cd flash-sale-system
cp .env.example .env
docker compose up -d
```
*Wait ~5 seconds for the database and Redis healthchecks to pass.*

### 2. Start the Backend API
In a new terminal window:
```bash
cd flash-sale-system/backend
./mvnw spring-boot:run
```
*Flyway will automatically initialize the database schema and insert starter seed products.*  
*Backend runs on: `http://localhost:8080`*  
*Swagger API Docs: `http://localhost:8080/swagger-ui.html`*

### 3. Start the JavaFX Desktop Client
In another terminal window:
```bash
cd flash-sale-system/frontend-javafx
./mvnw clean javafx:run
```

---

## Resetting Data for a Fresh Demonstration

To wipe all test data, custom sales, and Redis keys to start completely clean:
```bash
cd flash-sale-system
docker compose down -v
docker compose up -d
```
*(The `-v` flag removes the persistent volumes so PostgreSQL and Redis recreate fresh schemas on next startup).*

---

## Tech Stack

- **Backend**: Java 21, Spring Boot 3.3.4, Spring Security, Spring Data JPA, Redis, PostgreSQL, Flyway
- **Frontend**: Java 21, JavaFX 21, FXML, CSS
- **Concurrency & Scripting**: Redis Lua scripts
- **Infrastructure**: Docker Compose
