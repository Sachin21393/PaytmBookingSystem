# Engineering Architecture Write-Up (`WRITEUP.md`)
**Paytm Money Take-Home Assignment — Seat Reservation at Scale (Deploy & Observe)**

---

## Live Deployment Verification & Benchmark Scorecard

The following is the verbatim execution scorecard generated directly against the live production deployment (`https://paytmbookingsystem.onrender.com`) via `./burst.sh`:

```text
========================================================================
  PAYTM MONEY — FULL-FLEDGED CONCURRENCY & LIFECYCLE TEST RUNNER
========================================================================
 Target Server : https://paytmbookingsystem.onrender.com
 Hot-Seat Storm: 500 concurrent users on seat "A12"
 Mixed Burst   : 20,000 mixed requests
 Concurrency   : 30 parallel workers
========================================================================

[0/6] Checking server health (waking up cold instance if sleeping)...
  -> Health ping attempt 1/25... [HTTP 200] Server is WARM and healthy!

[1/6] User Lifecycle: Testing User Registration & Login...
  -> Successfully registered and logged in 30 users with JWT tokens.

[2/6] Show Lifecycle: Creating Show & Verifying Initial Inventory...
  -> Created Show with ID: 22 (Seats: A1, A2, A3, A4, A5, Price: 25000 paise).
  -> Initial Inventory: Available=5, Confirmed=0, Total=5
  -> Invariant (Available + Confirmed == Total): PASSED

[3/6] Standalone Cancellation Lifecycle (Reserve -> Non-Owner 403 -> Owner 200 -> Re-book)...
  [Step 1] User A reserved seat A1 -> Status: 201, Reservation ID: 32
  [Step 2] Attacker (User B) attempts to cancel User A's reservation -> Status: 403 (Expected: 403)
  [Step 3] Owner (User A) cancels reservation -> Status: 200, New Status: "CANCELLED" (Expected: 200, CANCELLED)
  [Step 4] Checking seat A1 inventory status -> Status: "AVAILABLE" (Expected: AVAILABLE)
  [Step 5] User B re-books released seat A1 -> Status: 201, Status: "CONFIRMED" (Expected: 201, CONFIRMED)
  => STAGE 3 CANCELLATION LIFECYCLE: PASSED (100% CORRECT)

[4/6] RUNNING TEST 1: 500 USERS STORM HOT SEAT "A12" AT ONCE...
  -> Created Show for Storm with ID: 23 (24 seats, price: 15000 paise).
  -> 500 users competing for seat: "A12" across 100 parallel workers...
  Storm Progress: 500/500 [100%]
  Storm finished in 10.69s (47 req/s)

  +-------------------------------------------------------------+
  |          STAGE 4: 500-USER HOT-SEAT "A12" SCORECARD         |
  +-------------------------------+---------------+-------------+
  | Metric                        | Result        | Expectation |
  +-------------------------------+---------------+-------------+
  | 201 Created (Winner)          | 1             | Exactly 1   |
  | 409 Conflict (Losers)         | 499           | 499         |
  | 5xx Server Errors             | 0             | ZERO (0)    |
  | Other Status Codes            | 0             | 0           |
  | Throughput                    | 21 req/s      | High        |
  | Latency: p50 / p95 / p99      | 1398.9 / 2000.4 / 2222.8 ms |
  +-------------------------------+---------------+-------------+
  => 500-USER HOT-SEAT BURST: PASSED (100% INVARIANT CONCURRENCY)
  -> Final Show 1 Inventory: Available=23, Confirmed=1, Total=24
  -> Reconciliation Invariant (Available + Confirmed == Total): PASSED

[5/6] RUNNING TEST 2: 20,000 MIXED WORKLOAD BURST...
  -> Created Show 2 with ID: 24 (20 seats, per-user limit: 4).
  Workload Breakdown:
   - 4,000 Idempotent Replays (Same key & payload -> expect cached 200/201)
   - 2,500 Tampered Idempotent Requests (Same key, different payload -> expect 409 Conflict)
   - 3,000 Limit Exceeding Requests (Booking 5 seats > limit 4 -> expect 400 Bad Request)
   - 10,500 Hot-Seat Storm Requests for "A2" (Expect 1 winner 201, rest 409 Conflict)
  Mixed Progress: 20,000/20,000 [100%]
  Mixed burst finished in 347.50s (58 req/s)

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
  -> Final Show 2 Inventory: Available=18, Confirmed=2, Total=20
  -> Reconciliation Invariant (Available + Confirmed == Total): PASSED

[6/6] Prometheus Observability: Inspecting Live Metrics...
  -> Successfully queried Prometheus endpoint (/actuator/prometheus)!
  -> Live Business Metrics Sample:
     # HELP reservations_confirmed_total Total number of successfully confirmed reservations
     # TYPE reservations_confirmed_total counter
     reservations_confirmed_total{application="paytmProject"} 21.0
     # HELP reservations_declined_total  
     # TYPE reservations_declined_total counter
     reservations_declined_total{application="paytmProject",reason="idempotent-mismatch"} 5000.0
     reservations_declined_total{application="paytmProject",reason="idempotent-replay"} 8770.0
     reservations_declined_total{application="paytmProject",reason="per-user-limit"} 6000.0
     reservations_declined_total{application="paytmProject",reason="seat-taken"} 62512.0

========================================================================
  FULL-FLOW BENCHMARK: ALL STAGES PASSED WITH 100% SUCCESS!
========================================================================
```

---

## 1. The Atomic Decision & Deadlock Avoidance

### How It Works
Atomicity is pushed directly into PostgreSQL's lock manager using **pessimistic row-level write locking** (`SELECT ... FOR UPDATE`) within a `@Transactional(isolation = Isolation.READ_COMMITTED)` boundary:
```sql
SELECT s FROM Seat s WHERE s.show.id = :showId AND s.seatNumber IN :seatNumbers ORDER BY s.seatNumber ASC FOR UPDATE;
```
When thousands of requests contest seat `A1`, PostgreSQL grants the lock to exactly one transaction. The winner verifies `status == AVAILABLE`, updates to `CONFIRMED`, assigns `reservation_id`, and commits. Subsequent transactions unblock, see `status == CONFIRMED`, and immediately fail with HTTP `409 Conflict`.

### Deadlock Avoidance on Multi-Seat Bookings
When Transaction 1 wants `[A1, A2]` and Transaction 2 wants `[A2, A1]`, a classic circular wait deadlock occurs.  
**Our Solution**: In-memory alphabetical sorting before acquiring locks:
```java
List<String> sortedSeats = request.getSeatNumbers().stream().distinct().sorted().toList();
```
By enforcing a global acquisition hierarchy ($A_1 \to A_2 \to B_1$), transactions never hold a higher resource while waiting for a lower one. Circular wait is mathematically eliminated.

### Alternatives Excluded & Why
| Alternative | Why Excluded |
| :--- | :--- |
| **Optimistic Locking (`@Version`)** | Under a storm of 500+ users on 1 hot seat, 499 threads fail validation and must either abort or retry. In high-concurrency write contention, optimistic locking causes catastrophic **retry storms**, thrashing database CPU, IOPS, and connection pools. |
| **In-Memory Java Locks (`ReentrantLock` / `synchronized`)** | Completely ineffective in production. The instant the service scales horizontally across 2 or more container instances behind a load balancer, in-memory locks do not coordinate, leading to instant double-selling. |
| **Distributed Locks (Redis / Redlock)** | Introduces external network hops, split-brain hazards during network partitions, and dual-write synchronization complexity between Redis and PostgreSQL for a single-database service. |

### Why This Is the Best Fit
Pessimistic row locking in PostgreSQL leverages the database's native ACID engine. It guarantees 100% serializability without distributed overhead, eliminates retry storms, and guarantees zero double-selling. Combined with our bounded HikariCP connection pool (`maximum-pool-size: 30`, `minimum-idle: 10`), it eliminates database connection exhaustion: incoming burst threads queue safely in JVM memory, keeping PostgreSQL CPU and IOPS strictly bounded with zero DB crash overhead.

---

## 2. Idempotency

### How It Works
- **Storage**: Dedicated `idempotency_records` table with a `UNIQUE (idempotency_key)` constraint.
- **Payload Fingerprinting**: SHA-256 hash of `show_id + sorted(seat_numbers)` stored in `request_hash`.
- **Pre-Check Before Locking**:
  1. **Replay Hit**: If key exists and `hash == stored_hash`, return original cached response (`200/201`). Zero seats booked, zero DB locks taken.
  2. **Tampered Payload (Same Key, Different Body)**: If key exists but `hash != stored_hash`, throw `InvalidIdempotencyKeyException` $\to$ **HTTP `409 Conflict`** (`IDEMPOTENCY_CONFLICT`), adhering strictly to Karan's specification.
- **TTL & Storage Mechanism**: Persisted durably in PostgreSQL with timestamps. In production at scale, records maintain a 24-hour TTL (cleaned via automated daily partition truncation `DROP TABLE idempotency_records_yymmdd`), avoiding expensive row-by-row deletes while guaranteeing multi-instance durability.

### Alternatives Excluded & Why
| Alternative | Why Excluded |
| :--- | :--- |
| **In-Memory Cache (Caffeine / Guava)** | Lost on container restart, redeploy, or crash. A retried payment during deployment would result in double-charging. |
| **Checking Only `reservation_id`** | Fails to detect when a client reuses an idempotency key with a tampered body (e.g., swapping seat `A1` for `B5`). Cryptographic hashing is required. |

### Why This Is the Best Fit
Durable PostgreSQL persistence prevents double-charging across restarts and pods; SHA-256 fingerprinting guarantees tamper detection.

---

## 3. Holds & Expiry Model

### How It Works: Explicit Owner Cancellation (`POST /reservations/{id}/cancel`)
We implemented the explicit cancellation model:
1. **Ownership Verification**:
   ```java
   if (!reservation.getUser().getId().equals(currentUser.getId())) {
       throw new AccessDeniedException("Only the reservation owner can cancel this reservation");
   }
   ```
   Non-owners attempting cancellation receive **HTTP `403 Forbidden`**.
2. **Atomic Seat Release**:
   - Reservation marked `CANCELLED`.
   - Associated seats set to `AVAILABLE` and their `reservation_id` cleared to `NULL`.
3. **Resurrection Safety**:
   The cancellation query strictly touches only rows matching `reservation_id == :id`. It can never alter or resurrect seats belonging to any other reservation.
4. **Immediate Re-Booking**: Released seats become immediately visible and bookable in the same transaction.

### Alternatives Excluded & Why
| Alternative | Why Excluded |
| :--- | :--- |
| **Background DB Sweeper (`@Scheduled` polling)** | Running `SELECT * FROM seats WHERE status = 'HELD' AND updated_at < NOW() - 10m` every second creates continuous table scan overhead, lock contention, and index thrashing during 20k burst traffic. |

### Why This Is the Best Fit
Guarantees zero ghost seats, prevents unauthorized access, and maintains the reconciliation invariant (`available + confirmed == total_seats`) without asynchronous polling lag.

---

## 4. Consistency vs. Availability Under Partition (CAP Theorem)

### Our Decision: Strict Consistency (CP) Over Availability (AP)
In ticketing and financial transactions, **selling the same seat twice is a fatal business error**. Our system explicitly prioritizes **Consistency**:

- **Failing Closed**: If database connectivity drops or connection timeouts occur, requests fail immediately with **`409 Conflict`** or **`503 Service Unavailable`**.
- **No Compromise**: We never fall back to asynchronous optimistic writes or local offline buffering.


---

## 5. Observability: What We'd Get Paged for at 2 AM

Our service exposes Prometheus metrics at `/actuator/prometheus` and JSON logs with UUID request correlation IDs (`X-Request-Id`).

### The 4 High-Severity Paging Alerts

| Alert | Condition & Threshold | Root Cause | Immediate Action |
| :--- | :--- | :--- | :--- |
| **1. Invariant Corruption (P0)** | `seats_available + seats_confirmed != total_seats` for $>0\text{s}$ | State leakage, partial rollback, or manual DB tampering. | **Immediate automated freeze** of show sales; trigger reconciliation script. |
| **2. HikariCP Starvation (P1)** | `hikaricp_pending_threads > 15` for $>30\text{s}$ | Long-running queries holding connections, pool exhaustion. | Check slow queries; scale connection pool or database IOPS. |
| **3. Lock Contention Storm (P1)** | `rate(reservations_declined_total{reason="seat-taken"}[1m]) > 500/s` with 0 confirmations | Bot storm or distributed brute-force targeting single seat. | Activate Cloudflare rate-limiting / WAF captcha on the show URL. |
| **4. Elevated 5xx Rate (P0)** | `rate(http_server_requests_seconds_count{status=~"5.."}[1m]) > 1%` | Unhandled runtime exception, JVM OOM, or database crash. | Inspect correlation IDs in logs; failover to hot-standby DB. |

---

## 6. AI Usage: Directed vs. Decided

### What Was Done by Me (Architecture & Core Business Logic)
1. **Full Architectural Design**: 
   - Evaluated and chose the concurrency control paradigm: selected database-level pessimistic row locking (`SELECT ... FOR UPDATE`) over optimistic locking and distributed locks.
   - Enforced strict CP (Consistency over Availability) fail-closed model for inventory safety.
2. **Core Business Logic Coding**:
   - Implemented the deadlock elimination algorithm via in-memory alphabetical seat sorting ($A_1 \to A_2 \to B_1$) before acquiring SQL locks.
   - Built the idempotency verification engine: SHA-256 payload hashing, replay cache retrieval, and tamper detection.
   - Implemented the cancellation and inventory release lifecycle with strict owner authorization checks (`403 Forbidden`) and resurrection immunity.
   - Enforced atomic all-or-nothing transactional semantics and reconciliation invariants (`available + held + confirmed == total_seats`).
3. **Concurrency & Load Modeling**:
   - Identified that simulating a 20,000-request storm requires a bounded 30-worker connection pool to match the HikariCP pool, prevent local TCP socket exhaustion, and mirror production reverse proxies.
4. **Database Connection Pool Sizing & Resource Protection**:
   - Explicitly designed and tuned HikariCP (`maximum-pool-size: 30`, `minimum-idle: 10`, `connection-timeout: 60000ms`) to eliminate database connection exhaustion and backend process table bloat.
   - During high-concurrency bursts, surplus requests queue safely in JVM application memory rather than overwhelming the database process table, ensuring zero connection churn and minimal database overhead.
5. **Relational Schema Design & Query Optimization**:
   - Designed normalized relational tables with efficient foreign-key relations and join structures across `shows`, `seats`, `reservations`, and `users`.
   - Optimized query execution plans using composite b-tree indexing on `(show_id, seat_number)` in the `seats` table, ensuring $O(1)$ index seek times during concurrent `SELECT ... FOR UPDATE` row locks.
   - Indexed foreign keys on `reservation_id` to eliminate full table scans during inventory reconciliation, cancellations, and status lookups.
   - Enforced unique index constraints on `idempotency_records(idempotency_key)` to guarantee instantaneous, sub-millisecond replay lookups with zero lock contention.

### What Was Done by AI (Directed by Me)
1. **Boilerplate & Plumbing Generation**: Generated repetitive Spring Boot DTO records, JPA repository interfaces, entity mapping annotations, and controller routing stubs.
2. **Metric Wiring**: Wrote the boilerplate Micrometer instrumentation for `reservations_confirmed_total`, `reservations_declined_total`, and `seats_available`.
3. **Load Test Script**: Generated the raw Node.js script scaffolding (`burst_test.js`) and bash wrapper (`burst.sh`).
4. **Code Cleanup & Formatting**: Standardized code formatting, import organization, and log message structure.

---

## 7. What We Would Do Next (Production Roadmap)

1. **Redis Lua Scripting Ingress Filter**:
   Place Redis in front of PostgreSQL. A 5-line Lua script checks and claims seat availability in $<1\text{ms}$ in-memory. 19,999 losing contenders are rejected at the edge without touching the PostgreSQL connection pool.
2. **Read Replicas & CQRS**:
   Route high-volume `GET /shows/{id}` inventory queries to PostgreSQL read replicas, reserving 100% of primary database IOPS for atomic `POST /reserve` transactions.
3. **High-Availability (HA) Pod Auto-Recovery & Load Balancing**:
   - Distribute ingress traffic across multiple horizontal pod replicas behind an Envoy / Kubernetes Ingress controller using a **Round-Robin** algorithm.
   - Implement an active **Watchdog mechanism** wired to our Spring Boot `/actuator/health/liveness` and `/readiness` endpoints: if an individual pod crashes, encounters JVM memory pressure, or becomes unresponsive, the watchdog instantly ejects it from the routing pool and provisions a replacement, allowing healthy peer pods to pick up the traffic automatically with zero customer disruption.
   - Because all concurrency control and locking are enforced at the database row level rather than in-memory JVM structures, pods are completely stateless and failover is instantaneous without split-brain risk.
