/** Matches backend: com.seatlock.backend.model.Event */
export interface Event {
  id: number;
  title: string;
  language: string;
  location: string;
  date: string;
}

/** Matches backend: com.seatlock.backend.model.Seat */
export interface Seat {
  id: number;
  eventId: number;
  seatNumber: string;
  status: string; // "AVAILABLE" | "LOCKED" | "BOOKED"
  lockedUntil: string | null;
}

/** Matches backend: com.seatlock.backend.model.Booking */
export interface Booking {
  id: number;
  seatId: number;
  eventId: number;
  status: string; // "PENDING" | "CONFIRMED" | "CANCELLED" | "EXPIRED"
  createdAt: string;
  idempotencyKey: string;
}

/** Standard shape for API error responses from Spring Boot */
export interface ApiError {
  status: number;
  error: string;
  message: string;
  path: string;
}
