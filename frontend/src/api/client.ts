const BASE_URL = '/api/v1';

/**
 * Custom error class that carries the HTTP status code and
 * the server-provided error message (if any).
 */
export class ApiError extends Error {
  status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

/**
 * Thin wrapper around `fetch` that:
 *  - prepends the base URL
 *  - sets JSON content-type for non-GET requests
 *  - parses the response as JSON
 *  - throws an `ApiError` on non-2xx responses
 */
async function request<T>(
  endpoint: string,
  options: RequestInit = {},
): Promise<T> {
  const url = `${BASE_URL}${endpoint}`;

  const headers: Record<string, string> = {
    Accept: 'application/json',
    ...(options.headers as Record<string, string>),
  };

  if (options.body && typeof options.body === 'string') {
    headers['Content-Type'] = 'application/json';
  }

  const response = await fetch(url, { ...options, headers });

  if (!response.ok) {
    let message = `Request failed (${response.status})`;
    try {
      const body = await response.json();
      if (body.message) {
        message = body.message;
      }
    } catch {
      // response body was not JSON — keep the default message
    }
    throw new ApiError(response.status, message);
  }

  // Handle 204 No Content
  if (response.status === 204) {
    return undefined as T;
  }

  return response.json() as Promise<T>;
}

/* ── Event endpoints ───────────────────────────────────── */

import type { Event, Seat, Booking } from '../types';

export function fetchEvents(): Promise<Event[]> {
  return request<Event[]>('/events');
}

export function fetchEvent(eventId: number): Promise<Event> {
  return request<Event>(`/events/${eventId}`);
}

/* ── Seat endpoints ────────────────────────────────────── */

export function fetchSeats(eventId: number): Promise<Seat[]> {
  return request<Seat[]>(`/events/${eventId}/seats`);
}

/* ── Booking endpoints ─────────────────────────────────── */

export function createBooking(body: {
  seatId: number;
  eventId: number;
  idempotencyKey: string;
}): Promise<Booking> {
  return request<Booking>('/bookings', {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function fetchBooking(bookingId: number): Promise<Booking> {
  return request<Booking>(`/bookings/${bookingId}`);
}

export function confirmBooking(bookingId: number): Promise<Booking> {
  return request<Booking>(`/bookings/${bookingId}/confirm`, {
    method: 'POST',
  });
}

export function cancelBooking(bookingId: number): Promise<Booking> {
  return request<Booking>(`/bookings/${bookingId}`, {
    method: 'DELETE',
  });
}
