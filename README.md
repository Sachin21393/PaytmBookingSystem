# Seat Reservation at Scale (Deploy & Observe)
**Paytm Money — Backend Engineering Take-Home Exercise**


A high-concurrency ticket reservation engine designed to sell assigned seats for high-demand on-sale events (concerts, movies, sporting events). Built to guarantee **strict zero double-booking**, **deadlock-free multi-seat transactions**, **idempotent retry safety**, and **full real-time observability** under bursts of tens of thousands of concurrent requests.

---

## Live Cloud Deployment
- **Live Base URL**: `https://paytmbookingsystem.onrender.com`
- **Health & Readiness**: `https://paytmbookingsystem.onrender.com/actuator/health` (HTTP 200 UP)
- **Prometheus Metrics**: `https://paytmbookingsystem.onrender.com/actuator/prometheus`
- **One-Command Cloud Burst**:
  ```bash
  # On Linux / macOS:
  ./burst.sh https://paytmbookingsystem.onrender.com

  # On Windows (PowerShell / Command Prompt):
  .\burst.bat https://paytmbookingsystem.onrender.com

  # Or directly via Node.js (All platforms):
  node burst_test.js https://paytmbookingsystem.onrender.com --total 20000 --concurrency 100
  ```

---

## Quickstart: Run Locally in Under 2 Minutes

You can run the entire service and execute the concurrency burst test locally with a single command.

### Prerequisites
Ensure you have the following installed on your machine:
| Tool | Minimum Version | Required For |
| :--- | :--- | :--- |
| **Git** | `2.x+` | Cloning the repository |
| **Java (JDK)** | `21+` | Running natively via Maven Wrapper |
| **Docker & Docker Compose** | `20.x+` | One-command full-stack containerized execution (Recommended) |
| **Node.js** | `18+` | Running the concurrency burst runner (`./burst.sh` / `burst_test.js`) |

---

### Option 1: One-Command Startup via Docker Compose (Recommended)

Spins up both the **Spring Boot backend** and **PostgreSQL 16** with zero manual configuration.

```bash
# 1. Clone the repository
git clone https://github.com/Sachin21393/PaytmBookingSystem.git
cd PaytmBookingSystem

# 2. Build and launch all containers in detached mode
docker-compose up --build -d

# 3. Verify the service is healthy
curl http://localhost:8080/actuator/health
# Expected: {"status":"UP"}

# 4. Execute the Concurrency Burst Test against localhost
# On Linux / macOS:
./burst.sh http://localhost:8080

# On Windows (PowerShell / Command Prompt):
.\burst.bat http://localhost:8080

# Or directly via Node.js:
node burst_test.js http://localhost:8080
```

To stop containers:
```bash
docker-compose down -v
```

---

### Option 2: Native Run via Maven Wrapper

If you prefer running natively with your local JDK:

```bash
# 1. Start a local PostgreSQL container (or use your existing Postgres instance):
docker run --name paytm-postgres \
  -e POSTGRES_DB=paytmdb \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgrespassword \
  -p 5432:5432 -d postgres:16-alpine

# 2. Start the Spring Boot backend:
# On Linux/macOS:
./mvnw spring-boot:run

# On Windows PowerShell:
.\mvnw.cmd spring-boot:run

# 3. In a separate terminal, execute the burst test:
# On Linux / macOS:
./burst.sh http://localhost:8080

# On Windows (PowerShell / Command Prompt):
.\burst.bat http://localhost:8080

# Or:
node burst_test.js http://localhost:8080
```

---

### Run the Automated Test Suite

To run all JUnit integration and unit tests (including concurrent hot-seat simulations):

```bash
# Linux / macOS:
./mvnw clean test

# Windows PowerShell:
.\mvnw.cmd clean test
```
*All 10/10 tests pass with 100% assertion coverage.*

---

## The Correctness Bar: Concurrency & Scale Verification

The test harness exercises the exact workload patterns defined in Paytm's specification:

### 1. Hot-Seat Contention (500 Concurrent Users on Seat "A12")
500 people all try to grab seat `A12` at once. The engine guarantees:
1. **Zero Double-Selling**: Exactly **1 winner (`201 Created`)**; exactly **499 contenders declined (`409 Conflict`)**.
2. **Zero Crashes**: **`0` 5xx server errors** across the burst.
3. **Reconciliation Invariant**: `available + held + confirmed == total_seats` strictly holds before, during, and after the burst.

```text
+-------------------------------------------------------------+
|          STAGE 4: 500-USER HOT-SEAT "A12" SCORECARD         |
+-------------------------------+---------------+-------------+
| Metric                        | Result        | Expectation |
+-------------------------------+---------------+-------------+
| 201 Created (Winner)          | 1             | Exactly 1   |
| 409 Conflict (Losers)         | 499           | 499         |
| 5xx Server Errors             | 0             | ZERO (0)    |
| Other Status Codes            | 0             | 0           |
| Throughput                    | 47 req/s      | High        |
| Latency: p50 / p95 / p99      | 1.9s / 3.8s / 4.8s          |
+-------------------------------+---------------+-------------+
=> 500-USER HOT-SEAT BURST: PASSED (100% INVARIANT CONCURRENCY)
```

### 2. 20,000 Mixed Workload Storm
A burst of **20,000 mixed requests** simulating realistic on-sale traffic:
- **4,000 Idempotent Replays** (same key & payload $\to$ cached `200/201`).
- **2,500 Tampered Idempotent Requests** (same key, different seats $\to$ `409 Conflict`).
- **3,000 Limit-Exceeding Requests** (booking 5 seats with limit 4 $\to$ `400 Bad Request`).
- **10,500 Hot-Seat Contention Requests** on seat `A2` (1 winner `201`, rest `409 Conflict`).

```text
+-------------------------------------------------------------+
|          STAGE 5: MIXED WORKLOAD BURST SCORECARD            |
+-------------------------------+---------------+-------------+
| Category / Status             | Result        | Expectation |
+-------------------------------+---------------+-------------+
| Idempotent Replays + Winner   | 4001          | 4001        |
| 400 User Limit Exceeded       | 3000          | 3000        |
| 409 Conflict (Contention+Keys)| 12999         | 12999       |
| 5xx Internal Server Errors    | 0             | ZERO (0)    |
| Throughput                    | 58 req/s      | High        |
+-------------------------------+---------------+-------------+
=> MIXED WORKLOAD BURST: PASSED (100% INVARIANT ACCURACY)
```

---

## One-Command Concurrency Burst Runner

We provide a zero-dependency, native Node.js load-testing harness and a bash wrapper script (`./burst.sh`) to reproduce the on-sale stampede against the live service:

### 1. Run Against the Live Deployed Service:
```bash
# On Linux / macOS:
./burst.sh https://paytmbookingsystem.onrender.com

# On Windows (PowerShell / Command Prompt):
.\burst.bat https://paytmbookingsystem.onrender.com

# Or directly with Node.js (All platforms):
node burst_test.js https://paytmbookingsystem.onrender.com --total 20000 --concurrency 100
```

### 2. What the Script Automatically Tests:
- **Stage 0**: Pings `/actuator/health` to ensure the instance is awake and warm.
- **Stage 1**: Auto-provisions and authenticates 30 distinct test users via `/api/v1/auth/register` and `/api/v1/auth/login`.
- **Stage 2**: Creates a fresh show and inspects initial inventory state via `GET /shows/{id}`.
- **Stage 3**: Verifies the **cancellation lifecycle**:
  - User A reserves seat `A1`.
  - Attacker (User B) attempts to cancel User A's reservation $\to$ asserts HTTP `403 Forbidden`.
  - Owner (User A) cancels reservation $\to$ asserts HTTP `200 OK` (status `CANCELLED`).
  - Verifies seat `A1` reverts back to `AVAILABLE` in inventory.
  - User B re-books seat `A1` $\to$ asserts HTTP `201 Created` (`CONFIRMED`).
- **Stage 4**: Executes the **500-User Hot-Seat Contention on "A12"** (1 winner `201`, 499 losers `409 Conflict`, `0` 5xx), followed by inventory reconciliation.
- **Stage 5**: Executes the **20,000 Mixed Workload Storm** (Idempotent replays, tampered keys `409`, limit violations `400`, seat contention `409`).
- **Stage 6**: Reconciles the inventory invariant and queries `/actuator/prometheus` for business metrics.

---

## REST API Reference

Money values are represented in **integer paise** (e.g., `25000` = ₹250.00), never floating-point numbers.

### 1. Show Management

#### Create a Show (Admin)
- **`POST /shows`** or **`POST /api/v1/shows`**
- **Request Body**:
  ```json
  {
    "name": "friday-night-concert",
    "seats": ["A1", "A2", "A3", "A4", "B1", "B2"],
    "price_paise": 25000,
    "per_user_limit": 4
  }
  ```
- **Response (`201 Created`)**:
  ```json
  {
    "id": 1,
    "name": "friday-night-concert",
    "price_paise": 25000,
    "per_user_limit": 4,
    "total_seats": 6,
    "available_count": 6,
    "held_count": 0,
    "confirmed_count": 0,
    "seats": [
      { "seat_number": "A1", "status": "AVAILABLE" },
      { "seat_number": "A2", "status": "AVAILABLE" }
    ],
    "created_at": "2026-10-02T16:00:00Z"
  }
  ```

#### Get Show State & Inventory
- **`GET /shows/{id}`** or **`GET /api/v1/shows/{id}`**
- **Response (`200 OK`)**:
  ```json
  {
    "id": 1,
    "name": "friday-night-concert",
    "total_seats": 6,
    "available_count": 5,
    "held_count": 0,
    "confirmed_count": 1,
    "seats": [
      { "seat_number": "A1", "status": "CONFIRMED" },
      { "seat_number": "A2", "status": "AVAILABLE" }
    ]
  }
  ```
- **Reconciliation Invariant**: `available_count + held_count + confirmed_count == total_seats` holds at all times.

---

### 2. Seat Reservations

#### Reserve Seat(s)
- **`POST /shows/{id}/reserve`**
- **Authentication**: `Authorization: Bearer <JWT_TOKEN>` (Required. Identity is token-derived, never spoofable).
- **Idempotency**: Key passed in header (`Idempotency-Key: <UUID>`) **OR** in request body (`"idempotency_key": "..."`).
- **Request Body**:
  ```json
  {
    "seats": ["A1"],
    "idempotency_key": "client-uuid-98765"
  }
  ```
  *(Note: Both `"seats"` and `"seat_numbers"` are supported).*

- **Success Response (`201 Created`)**:
  ```json
  {
    "reservation_id": 42,
    "show_id": 1,
    "user_id": 15,
    "seats": ["A1"],
    "amount_paise": 25000,
    "total_amount_paise": 25000,
    "status": "CONFIRMED",
    "created_at": "2026-10-02T16:05:00Z"
  }
  ```

- **Domain Decline Outcomes (Never 500)**:
  - **`409 Conflict`**: Seat already booked/held by another user (`SEAT_CONFLICT`).
  - **`409 Conflict`**: Reusing an existing idempotency key with a different body (`IDEMPOTENCY_CONFLICT`).
  - **`400 Bad Request`**: Request exceeds the show's `per_user_limit` (`USER_LIMIT_EXCEEDED`).
  - **`400 Bad Request`**: Missing idempotency key or empty seat list (`BAD_REQUEST`).
  - **`401 Unauthorized`**: Missing or invalid Bearer JWT.

#### Cancel Reservation (Owner Only)
- **`POST /reservations/{id}/cancel`**
- **Authentication**: `Authorization: Bearer <JWT_TOKEN>` (Must be the original reservation owner).
- **Success Response (`200 OK`)**:
  ```json
  {
    "reservation_id": 42,
    "show_id": 1,
    "user_id": 15,
    "seats": ["A1"],
    "status": "CANCELLED"
  }
  ```
  - All seats in this reservation atomically revert back to `AVAILABLE`.
  - Non-owners attempting cancellation receive **HTTP `403 Forbidden`**.

---

### 3. User Authentication
- **Register**: `POST /api/v1/auth/register`
  ```json
  {
    "username": "alice",
    "password": "Password@123",
    "email": "alice@paytm.com",
    "fullName": "Alice Smith"
  }
  ```
- **Login**: `POST /api/v1/auth/login`
  ```json
  {
    "username": "alice",
    "password": "Password@123"
  }
  ```
  Returns: `{ "token": "eyJhbGciOi...", "tokenType": "Bearer", "expiresIn": 86400 }`

---

## Observability & Monitoring

The system exposes full production-grade observability endpoints:

### 1. Prometheus Metrics (`/actuator/prometheus`)
Custom business metrics are instrumented using Micrometer:
- **`reservations_confirmed_total`** *(Counter)*: Total number of successfully confirmed reservations.
- **`reservations_declined_total{reason="..."}`** *(Counter)*: Total rejected requests categorized by reason:
  - `reason="seat-taken"`: Hot-seat contention.
  - `reason="per-user-limit"`: Exceeded max allowed seats.
  - `reason="idempotent-replay"`: Cached replay returned.
  - `reason="idempotent-mismatch"`: Tampered payload for existing key.
- **`seats_available`** *(Gauge)*: Real-time gauge of currently available seats across all shows.
- **`http_server_requests_seconds`**: Request latencies, percentiles, and throughput per endpoint.
- **`hikaricp_connections_*`**: Active, idle, pending, and total connection pool metrics.

### 2. Health & Readiness Probes (`/actuator/health`)
- **`/actuator/health/liveness`**: Confirms the JVM and application are alive.
- **`/actuator/health/readiness`**: Actively verifies database connectivity and connection pool health. Fails closed (HTTP 503) if the database becomes unreachable.

### 3. Structured Logging & Request Tracing
Every incoming HTTP request is intercepted by `RequestCorrelationFilter`, assigning a unique `X-Request-Id` UUID mapped to Logback's Mapped Diagnostic Context (MDC). All log messages emit JSON-structured correlation fields:
```json
{
  "timestamp": "2026-10-02T16:05:00.123Z",
  "level": "INFO",
  "correlation_id": "8f3b2e71-4a12-4c9b-98df-82e174b29c11",
  "message": "http_request_completed",
  "uri": "/shows/1/reserve",
  "method": "POST",
  "status": 201,
  "duration_ms": 14
}
```

---

## Architecture & Core Design Highlights

| Challenge | Architectural Solution |
| :--- | :--- |
| **Race Condition on Hot Seat** | **Pessimistic Row-Level Write Locking (`SELECT ... FOR UPDATE`)**: Pushes atomicity into PostgreSQL's lock manager. Competing threads serialize; exactly 1 acquires the lock and confirms, all others see `status != AVAILABLE` and immediately decline with `409 Conflict`. |
| **Multi-Seat Deadlocks** | **Deterministic In-Memory Sorting**: In multi-seat requests (`[A2, A1]`), seats are sorted alphabetically (`A1 < A2`) *before* issuing SQL queries. All concurrent transactions acquire locks in the exact same order ($A_1 \to A_2 \to B_1$), mathematically eliminating circular wait deadlocks. |
| **Idempotency Storage** | **`idempotency_records` Table**: Enforces `UNIQUE (idempotency_key)`. Computes SHA-256 hash of `(show_id + sorted seats)`. Replays return original response; tampered payloads reject with `409 Conflict`. |
| **Partial Requests Strategy** | **Strict All-or-Nothing**: If a user requests `["A1", "A2"]` and only `A1` is free, the transaction rolls back, releasing all locks, and declines with `409 Conflict`. No partial holds are created. |
| **Connection Starvation** | **Bounded Connection Pooling**: Configured HikariCP (`maximum-pool-size: 30`, `connection-timeout: 30000ms`). Web threads queue safely in memory without crashing the database process table. |



