# Flash Sale and Inventory Reservation System

## Problem Statement
E-commerce flash sales face a "thundering herd" problem where thousands of concurrent requests attempt to purchase a limited inventory. Systems must be able to handle immense read/write load without overselling, deadlocking, or producing inconsistent data.

## Goals
- Provide atomic, fast reservations using Redis.
- Ensure durable business invariants in PostgreSQL.
- Handle 100 stock / 10000 requests without negative inventory.
- Process mock payments with a 60-second checkout window.
- Recover predictably from transient failures.

## Technology Stack
- **Backend**: Java 21, Spring Boot 3.3.4, Spring Web, Spring Data JPA/Redis, PostgreSQL, Redis, Flyway
- **Frontend**: Java 21, JavaFX 21, embedded `HttpClient`
- **Infrastructure**: Docker, Docker Compose

## Architecture
- **Backend Modular Monolith**: Isolates Auth, Reservation, Payment, and Order domains.
- **Cache Aside**: Product data cached with TTL.
- **Lua Scripts**: Redis executes Lua scripts to atomically check limits and admit reservations.
- **Transactional Outbox**: Payments and Expire workers write an outbox event alongside DB commits.
- **Redis Streams**: Background consumer handles asynchronous order creation and inventory releases securely.

## Inventory Conservation Invariant
`Total Stock = Available Stock + Reserved Stock + Sold Stock`
Durable bounds check ensures database correctness even if Redis loses synchronization.

## Redis vs PostgreSQL
- **Why Caching?** It offloads heavy read paths. Active sale inventory is preloaded to avoid stampedes.
- **Why Lua Scripts?** A simple `GET` + `DECR` suffers from race conditions. Lua guarantees atomicity across validation, time-checks, and decrement.
- **Pending Admissions Recovery**: Durable `outbox_events` and Stream Consumers manage reconciliation if a service goes down between Redis and Postgres transactions.

## Start the Project
1. Configure env and start containers:
```bash
cp .env.example .env
docker compose up -d
```
2. Start the Backend API (wait for DB/Redis readiness):
```bash
cd backend
./mvnw spring-boot:run
```
3. Start the JavaFX Application:
```bash
cd frontend-javafx
./mvnw clean javafx:run
```

## Running the Concurrency Simulator
Through the JavaFX GUI or Swagger (http://localhost:8080/swagger-ui.html):
- Create a Product
- Create and Activate a Flash Sale
- Under Simulator, select sale, users, attempts. Wait for the simulation and view metrics.

## Tests
Integration tests spin up Testcontainers (Postgres and Redis).
```bash
cd backend
./mvnw test
```
Observe `ConcurrencyTest` correctly handling 100 units / 10000 requests.
