# SeatLock — Load & Concurrency Testing with Locust

Concurrency and load tests for the SeatLock high-concurrency ticket booking backend.

## Prerequisites

| Dependency       | Version      | Purpose                            |
| ---------------- | ------------ | ---------------------------------- |
| Python           | 3.10+        | Runtime                            |
| pip / venv       | (bundled)    | Package management                 |
| Locust           | ≥ 2.29       | Load-test framework                |
| SeatLock backend | Running      | Target at `http://localhost:8080`   |
| MySQL            | 8.x          | Backend database on port 3306      |
| Redis            | 7.x          | Seat-lock store on port 6379       |

The backend **must be running** with at least one event and some seats before
you start a load test. If the database is empty, create test data first (see
[Seed Data](#seed-data) below).

---

## Setup

```bash
# From the project root
cd load-test

# Create a virtual environment (recommended)
python -m venv .venv

# Activate it
# Windows PowerShell:
.\.venv\Scripts\Activate.ps1
# Linux / macOS:
# source .venv/bin/activate

# Install dependencies
pip install -r requirements.txt
```

---

## Seed Data

Before running load tests, ensure the backend has events and seats.
You can create them via the existing API:

### Bash / cURL
```bash
# Create an event
curl -X POST http://localhost:8080/api/v1/events \
  -H "Content-Type: application/json" \
  -d '{"title":"Load Test Concert","language":"English","location":"Arena","date":"2026-12-31"}'

# Generate seats for event ID 1 (5 rows × 10 seats = 50 seats)
curl -X POST "http://localhost:8080/api/v1/events/1/seats/generate?rows=5&seatsPerRow=10"
```

### Windows PowerShell
```powershell
# Create an event
Invoke-RestMethod -Method POST -Uri "http://localhost:8080/api/v1/events" `
  -ContentType "application/json" `
  -Body '{"title":"Load Test Concert","language":"English","location":"Arena","date":"2026-12-31"}'

# Generate seats for event ID 1 (5 rows × 10 seats = 50 seats)
Invoke-RestMethod -Method POST -Uri "http://localhost:8080/api/v1/events/1/seats/generate?rows=5&seatsPerRow=10"
```

---

## Running Load Tests

### Web UI (recommended for interactive exploration)

```bash
locust -f locustfile.py --host http://localhost:8080
```

Open http://localhost:8089 in your browser. Set the number of users and spawn
rate, then start the swarm.

### Headless mode (CI / scripted execution)

```bash
# 50 concurrent users, spawned at 10/s, run for 60 seconds
locust -f locustfile.py \
  --host http://localhost:8080 \
  --headless \
  --users 50 \
  --spawn-rate 10 \
  --run-time 60s \
  --csv results/run1
```

Results are written to `results/run1_stats.csv`, `results/run1_failures.csv`,
etc.

### Run specific scenarios with tags

```bash
# Only browsing (read-only)
locust -f locustfile.py --host http://localhost:8080 --tags browse

# Only booking contention scenarios
locust -f locustfile.py --host http://localhost:8080 --tags contention

# Only idempotency replay verification
locust -f locustfile.py --host http://localhost:8080 --tags idempotency

# Only full lifecycle (create → poll → confirm/cancel)
locust -f locustfile.py --host http://localhost:8080 --tags lifecycle
```

---

## Test Scenarios & Concurrency Verification

### 1. Browse Events & Seats (`@tag("browse")`)

| Task         | Endpoint                          | Method | Weight |
| ------------ | --------------------------------- | ------ | ------ |
| List events  | `GET /api/v1/events`              | GET    | 5      |
| View seats   | `GET /api/v1/events/{id}/seats`   | GET    | 3      |

**Expected Outcome:**
All requests return HTTP 200. Response times remain low (< 50 ms p95) as these
are read-only queries.

### 2. Concurrent Attempts to Book Different Seats (`@tag("book", "concurrency")`)

| Task              | Endpoint                | Method | Weight |
| ----------------- | ----------------------- | ------ | ------ |
| Book random seat  | `POST /api/v1/bookings` | POST   | 4      |

Each user picks a random seat from a random event with a unique UUID v4 idempotency key.
- **200 OK**: Booking created (`PENDING`). The task then resolves it (50% confirm, 50% cancel)
  to ensure seats are recycled and no stranded `PENDING` bookings accumulate.
- **409 Conflict**: Seat was concurrently booked/locked by another user (expected concurrency outcome).

### 3. Concurrent Attempts to Book the SAME Seat (`@tag("contention")`)

| Task           | Endpoint                | Method | Weight |
| -------------- | ----------------------- | ------ | ------ |
| Race same seat | `POST /api/v1/bookings` | POST   | 3      |

Targets an initially **`AVAILABLE`** seat on the event to test genuine lock competition:
- **Expected Outcome:** Exactly **one** concurrent user acquires the Redis lock and DB row
  lock (`200 OK`, status `PENDING`). All losing contenders receive **`409 CONFLICT`**.
- **Continuous Contention Waves:** The winner immediately cancels the booking via
  `DELETE /api/v1/bookings/{id}`, resetting the seat status to `AVAILABLE` and releasing the
  Redis lock so subsequent waves can race again without leaving dangling `PENDING` rows.

### 4. Idempotency Key Replay (`@tag("idempotency")`)

| Step               | Endpoint                | Method | Expected Status |
| ------------------ | ----------------------- | ------ | --------------- |
| 1st request        | `POST /api/v1/bookings` | POST   | 200 OK          |
| 2nd request (same) | `POST /api/v1/bookings` | POST   | 200 OK          |

Sends the exact same payload (`seatId`, `eventId`, `idempotencyKey`) twice in immediate succession on an available seat:
- **Verification:** Asserts `b1["id"] == b2["id"]` and matching idempotency keys. If IDs differ,
  the test marks the request as an explicit idempotency failure.
- **Cleanup:** Cancels the booking afterwards to release the seat and avoid leaving unneeded
  `PENDING` records.

### 5. Full Booking Lifecycle (`@tag("lifecycle")`)

| Step    | Endpoint                              | Method | Expected Status |
| ------- | ------------------------------------- | ------ | --------------- |
| Create  | `POST /api/v1/bookings`               | POST   | 200 or 409      |
| Poll    | `GET  /api/v1/bookings/{id}`          | GET    | 200             |
| Confirm | `POST /api/v1/bookings/{id}/confirm`  | POST   | 200             |
| Cancel  | `DELETE /api/v1/bookings/{id}`        | DELETE | 200             |

Exercises the full customer journey from reservation to status polling to confirmation/cancellation:
- **No Stranded Reservations:** Wrapped with defensive cleanup (`try...finally`) so that any
  interrupted or failed scenario cancels the unconfirmed booking, ensuring zero orphan
  `PENDING` bookings in the database.

---

## Directory Structure

```
seatlock/
├── load-test/
│   ├── locustfile.py        # All Locust user classes, scenarios, and assertions
│   ├── requirements.txt     # Python dependencies (locust)
│   └── README.md            # Setup, execution, and scenario guide
├── .gitignore               # Root gitignore configured to ignore .venv/ and results/
```
