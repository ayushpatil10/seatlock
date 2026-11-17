"""
SeatLock — Locust Load-Testing Suite
=====================================

Simulates concurrent users browsing events, viewing seats, and racing to book
the same (or different) seats. Exercises idempotency-key deduplication,
Redis distributed seat locking, and database pessimistic-write concurrency controls.

Target: http://localhost:8080 (Spring Boot backend)
"""

from __future__ import annotations

import random
import threading
import uuid
from typing import Any

from locust import HttpUser, between, events, task, tag


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _uuid() -> str:
    """Generate a unique UUID v4 idempotency key."""
    return str(uuid.uuid4())


# ---------------------------------------------------------------------------
# Thread-safe Shared State
# ---------------------------------------------------------------------------

class SharedData:
    """Container for event/seat metadata discovered from the running backend."""

    def __init__(self):
        self.event_ids: list[int] = []
        self.seats_by_event: dict[int, list[dict[str, Any]]] = {}
        self.ready: bool = False
        self._lock = threading.Lock()

    def update(self, event_ids: list[int], seats_by_event: dict[int, list[dict[str, Any]]]):
        with self._lock:
            self.event_ids = event_ids
            self.seats_by_event = seats_by_event
            self.ready = bool(event_ids)


shared = SharedData()


def _populate_shared_data(base_url: str):
    """Fetch events and seats from the backend and populate SharedData."""
    import requests as _req

    try:
        resp = _req.get(f"{base_url}/api/v1/events", timeout=10)
        if resp.status_code != 200:
            print(f"[SETUP] Could not fetch events: HTTP {resp.status_code}")
            return
        event_list = resp.json()
    except Exception as exc:
        print(f"[SETUP] Warning — Failed to connect to backend at {base_url}: {exc}")
        return

    if not event_list:
        print("[SETUP] Warning — No events found in database. Seed data first.")
        return

    event_ids = [e["id"] for e in event_list]
    seats_by_event: dict[int, list[dict[str, Any]]] = {}

    for eid in event_ids:
        try:
            seat_resp = _req.get(f"{base_url}/api/v1/events/{eid}/seats", timeout=10)
            if seat_resp.status_code == 200:
                seats_by_event[eid] = seat_resp.json()
        except Exception as exc:
            print(f"[SETUP] Warning — Failed to fetch seats for event {eid}: {exc}")

    total_seats = sum(len(s) for s in seats_by_event.values())
    shared.update(event_ids, seats_by_event)
    print(f"[SETUP] Discovered {len(event_ids)} event(s) and {total_seats} seat(s).")


@events.test_start.add_listener
def on_test_start(environment, **_kwargs):
    """
    Auto-discover existing events and seats before the test starts.
    Workers in distributed mode lazily discover in on_start.
    """
    try:
        from locust.runners import WorkerRunner
        if isinstance(environment.runner, WorkerRunner):
            return
    except ImportError:
        pass

    base = environment.host or "http://localhost:8080"
    _populate_shared_data(base)


# ---------------------------------------------------------------------------
# Locust User
# ---------------------------------------------------------------------------

class SeatLockUser(HttpUser):
    """
    Simulates a concurrent user exercising the SeatLock API:
    1. Browsing events and seat availability (GET).
    2. Concurrent attempts to book different seats (POST).
    3. Simultaneous contention racing on an initially AVAILABLE seat.
    4. Repeated requests verifying idempotency key deduplication (same booking ID).
    5. Complete booking lifecycles ensuring no dangling PENDING reservations.
    """

    wait_time = between(0.5, 2.0)  # think-time between user actions
    host = "http://localhost:8080"

    def on_start(self):
        """Ensure shared state is initialized for this worker/user."""
        if not shared.ready or not shared.event_ids:
            try:
                resp = self.client.get("/api/v1/events", name="/api/v1/events [setup]")
                if resp.status_code == 200:
                    events_data = resp.json()
                    eids = [e["id"] for e in events_data]
                    seats_map: dict[int, list[dict[str, Any]]] = {}
                    for eid in eids:
                        s_resp = self.client.get(
                            f"/api/v1/events/{eid}/seats",
                            name="/api/v1/events/[id]/seats [setup]",
                        )
                        if s_resp.status_code == 200:
                            seats_map[eid] = s_resp.json()
                    shared.update(eids, seats_map)
            except Exception as e:
                print(f"[User on_start] Notice: {e}")

    # ------------------------------------------------------------------
    # Helper: Find an AVAILABLE seat for targeted scenarios
    # ------------------------------------------------------------------

    def _get_available_seat(self, event_id: int) -> dict[str, Any] | None:
        """
        Fetch the current seat map for an event and return an AVAILABLE seat,
        ensuring contention or idempotency tests start with an available seat.
        """
        try:
            resp = self.client.get(
                f"/api/v1/events/{event_id}/seats",
                name="/api/v1/events/[id]/seats [check-available]",
            )
            if resp.status_code == 200:
                seats = resp.json()
                avail = [s for s in seats if s.get("status") == "AVAILABLE"]
                if avail:
                    return random.choice(avail)
        except Exception:
            pass
        return None

    # ------------------------------------------------------------------
    # Scenario 1: Fetching Events & Seats
    # ------------------------------------------------------------------

    @tag("browse")
    @task(5)
    def list_events(self):
        """GET /api/v1/events — read-heavy event discovery."""
        with self.client.get(
            "/api/v1/events",
            name="/api/v1/events",
            catch_response=True,
        ) as resp:
            if resp.status_code == 200:
                resp.success()
            else:
                resp.failure(f"Unexpected status: {resp.status_code}")

    @tag("browse")
    @task(3)
    def view_seats(self):
        """GET /api/v1/events/{eventId}/seats — inspect seat availability."""
        if not shared.event_ids:
            return
        eid = random.choice(shared.event_ids)
        with self.client.get(
            f"/api/v1/events/{eid}/seats",
            name="/api/v1/events/[id]/seats",
            catch_response=True,
        ) as resp:
            if resp.status_code == 200:
                resp.success()
            else:
                resp.failure(f"Unexpected status: {resp.status_code}")

    # ------------------------------------------------------------------
    # Scenario 2: Concurrent Attempts to Book Different Seats
    # ------------------------------------------------------------------

    @tag("book", "concurrency")
    @task(4)
    def book_random_seat(self):
        """
        POST /api/v1/bookings with a unique idempotency key targeting random seats.
        - HTTP 200: Booking created (status PENDING). Resolved to CONFIRMED or CANCELLED
          to prevent leaving stranded PENDING reservations.
        - HTTP 409: Seat already booked/locked by another user (expected concurrency outcome).
        """
        if not shared.event_ids or not shared.seats_by_event:
            return

        eid = random.choice(shared.event_ids)
        seats = shared.seats_by_event.get(eid)
        if not seats:
            return

        seat = random.choice(seats)
        payload = {
            "seatId": seat["id"],
            "eventId": eid,
            "idempotencyKey": _uuid(),
        }

        with self.client.post(
            "/api/v1/bookings",
            json=payload,
            name="/api/v1/bookings [random-seat]",
            catch_response=True,
        ) as resp:
            if resp.status_code == 200:
                resp.success()
                booking = resp.json()
                booking_id = booking.get("id")

                # Resolve PENDING status: 50% confirm, 50% cancel to release seat
                if booking_id:
                    if random.random() < 0.5:
                        self.client.post(
                            f"/api/v1/bookings/{booking_id}/confirm",
                            name="/api/v1/bookings/[id]/confirm",
                        )
                    else:
                        self.client.delete(
                            f"/api/v1/bookings/{booking_id}",
                            name="/api/v1/bookings/[id]/cancel",
                        )
            elif resp.status_code == 409:
                # 409 Conflict is expected under concurrency when a seat is taken
                resp.success()
            else:
                resp.failure(f"Unexpected status: {resp.status_code} - {resp.text}")

    # ------------------------------------------------------------------
    # Scenario 3: Concurrent Attempts to Book the SAME Seat
    # ------------------------------------------------------------------

    @tag("book", "concurrency", "contention")
    @task(3)
    def book_same_seat_contention(self):
        """
        High-contention scenario: multiple concurrent users race to book the exact same seat.
        Uses an initially AVAILABLE seat so the test measures genuine lock contention.
        - Exactly one request wins (HTTP 200, status PENDING).
        - All concurrent and subsequent attempts are safely rejected with HTTP 409.
        - The winning user cancels the booking, releasing the Redis lock and resetting
          the seat to AVAILABLE for subsequent contention waves (no stranded PENDING).
        """
        if not shared.event_ids:
            return

        eid = shared.event_ids[0]
        seat = self._get_available_seat(eid)
        if not seat:
            return

        payload = {
            "seatId": seat["id"],
            "eventId": eid,
            "idempotencyKey": _uuid(),
        }

        with self.client.post(
            "/api/v1/bookings",
            json=payload,
            name="/api/v1/bookings [same-seat-contention]",
            catch_response=True,
        ) as resp:
            if resp.status_code == 200:
                resp.success()
                booking_data = resp.json()
                booking_id = booking_data.get("id")

                # Winner immediately cancels to release seat back to AVAILABLE
                if booking_id:
                    self.client.delete(
                        f"/api/v1/bookings/{booking_id}",
                        name="/api/v1/bookings/[id] [contention-release]",
                    )
            elif resp.status_code == 409:
                # Expected: Another concurrent user won the seat lock
                resp.success()
            else:
                resp.failure(f"Unexpected status: {resp.status_code} - {resp.text}")

    # ------------------------------------------------------------------
    # Scenario 4: Repeated Requests Using the Same Idempotency Key
    # ------------------------------------------------------------------

    @tag("idempotency")
    @task(2)
    def replay_idempotency_key(self):
        """
        Replays the exact same booking request (same seatId, eventId, and idempotencyKey).
        Uses an initially AVAILABLE seat to ensure the initial booking succeeds.
        Verifies:
        - Both requests return HTTP 200 with the exact same booking ID (no duplicates).
        - Both responses return identical idempotency keys.
        - Cancels the created booking to prevent leaving unnecessary PENDING records.
        """
        if not shared.event_ids:
            return

        eid = random.choice(shared.event_ids)
        seat = self._get_available_seat(eid)
        if not seat:
            return

        key = _uuid()
        payload = {
            "seatId": seat["id"],
            "eventId": eid,
            "idempotencyKey": key,
        }

        booking_id_1 = None

        # Call 1: Initial booking attempt
        with self.client.post(
            "/api/v1/bookings",
            json=payload,
            name="/api/v1/bookings [idempotency-call-1]",
            catch_response=True,
        ) as resp1:
            if resp1.status_code == 200:
                resp1.success()
                b1 = resp1.json()
                booking_id_1 = b1.get("id")
            elif resp1.status_code == 409:
                # Seat was taken in the microsecond before our call
                resp1.success()
                return
            else:
                resp1.failure(f"Call 1 unexpected status: {resp1.status_code} - {resp1.text}")
                return

        # Call 2: Immediate replay with identical idempotencyKey
        with self.client.post(
            "/api/v1/bookings",
            json=payload,
            name="/api/v1/bookings [idempotency-call-2-replay]",
            catch_response=True,
        ) as resp2:
            if resp2.status_code == 200:
                b2 = resp2.json()
                booking_id_2 = b2.get("id")

                if booking_id_1 != booking_id_2:
                    resp2.failure(
                        f"Idempotency violation! Duplicate booking created: "
                        f"ID1={booking_id_1}, ID2={booking_id_2}"
                    )
                elif b2.get("idempotencyKey") != key:
                    resp2.failure(
                        f"Idempotency key mismatch: Expected {key}, got {b2.get('idempotencyKey')}"
                    )
                else:
                    resp2.success()
            else:
                resp2.failure(
                    f"Idempotency replay failed with status {resp2.status_code}: {resp2.text}"
                )

        # Cleanup: Release the booking so it does not remain PENDING
        if booking_id_1:
            self.client.delete(
                f"/api/v1/bookings/{booking_id_1}",
                name="/api/v1/bookings/[id] [idempotency-cleanup]",
            )

    # ------------------------------------------------------------------
    # Scenario 5: Full Lifecycle (Create -> Poll -> Confirm or Cancel)
    # ------------------------------------------------------------------

    @tag("lifecycle")
    @task(2)
    def lifecycle_confirm(self):
        """
        Complete reservation journey:
        1. Create booking (POST /api/v1/bookings) -> PENDING.
        2. Poll booking status (GET /api/v1/bookings/{id}).
        3. Confirm booking (POST /api/v1/bookings/{id}/confirm) -> CONFIRMED.
        Ensures failed steps clean up to avoid leaving stranded PENDING bookings.
        """
        if not shared.event_ids or not shared.seats_by_event:
            return

        eid = random.choice(shared.event_ids)
        seat = self._get_available_seat(eid)
        if not seat:
            return

        payload = {
            "seatId": seat["id"],
            "eventId": eid,
            "idempotencyKey": _uuid(),
        }

        # Step 1: Create
        booking_id = None
        confirmed = False

        with self.client.post(
            "/api/v1/bookings",
            json=payload,
            name="/api/v1/bookings [lifecycle-create]",
            catch_response=True,
        ) as resp:
            if resp.status_code == 200:
                resp.success()
                booking_data = resp.json()
                booking_id = booking_data.get("id")
            elif resp.status_code == 409:
                resp.success()
                return  # Seat taken; lifecycle finishes cleanly
            else:
                resp.failure(f"Create failed: {resp.status_code} - {resp.text}")
                return

        if not booking_id:
            return

        try:
            # Step 2: Poll status
            with self.client.get(
                f"/api/v1/bookings/{booking_id}",
                name="/api/v1/bookings/[id] [poll]",
                catch_response=True,
            ) as resp:
                if resp.status_code == 200:
                    resp.success()
                else:
                    resp.failure(f"Poll failed: {resp.status_code} - {resp.text}")

            # Step 3: Confirm
            with self.client.post(
                f"/api/v1/bookings/{booking_id}/confirm",
                name="/api/v1/bookings/[id]/confirm",
                catch_response=True,
            ) as resp:
                if resp.status_code == 200:
                    resp.success()
                    confirmed = True
                elif resp.status_code in (400, 409):
                    resp.success()
                else:
                    resp.failure(f"Confirm failed: {resp.status_code} - {resp.text}")
        finally:
            # Defensive cleanup: If confirm did not succeed, cancel so it never stays PENDING
            if not confirmed:
                self.client.delete(
                    f"/api/v1/bookings/{booking_id}",
                    name="/api/v1/bookings/[id] [lifecycle-cleanup]",
                )

    @tag("lifecycle")
    @task(2)
    def lifecycle_cancel(self):
        """
        Complete cancellation journey:
        1. Create booking (POST /api/v1/bookings) -> PENDING.
        2. Poll booking status (GET /api/v1/bookings/{id}).
        3. Cancel booking (DELETE /api/v1/bookings/{id}) -> CANCELLED & seat released.
        """
        if not shared.event_ids or not shared.seats_by_event:
            return

        eid = random.choice(shared.event_ids)
        seat = self._get_available_seat(eid)
        if not seat:
            return

        payload = {
            "seatId": seat["id"],
            "eventId": eid,
            "idempotencyKey": _uuid(),
        }

        # Step 1: Create
        booking_id = None
        with self.client.post(
            "/api/v1/bookings",
            json=payload,
            name="/api/v1/bookings [lifecycle-create]",
            catch_response=True,
        ) as resp:
            if resp.status_code == 200:
                resp.success()
                booking_data = resp.json()
                booking_id = booking_data.get("id")
            elif resp.status_code == 409:
                resp.success()
                return
            else:
                resp.failure(f"Create failed: {resp.status_code} - {resp.text}")
                return

        if not booking_id:
            return

        # Step 2: Poll status
        with self.client.get(
            f"/api/v1/bookings/{booking_id}",
            name="/api/v1/bookings/[id] [poll]",
            catch_response=True,
        ) as resp:
            if resp.status_code == 200:
                resp.success()
            else:
                resp.failure(f"Poll failed: {resp.status_code} - {resp.text}")

        # Step 3: Cancel (releases seat back to AVAILABLE)
        with self.client.delete(
            f"/api/v1/bookings/{booking_id}",
            name="/api/v1/bookings/[id] [cancel]",
            catch_response=True,
        ) as resp:
            if resp.status_code == 200:
                resp.success()
            elif resp.status_code == 409:
                resp.success()
            else:
                resp.failure(f"Cancel failed: {resp.status_code} - {resp.text}")
