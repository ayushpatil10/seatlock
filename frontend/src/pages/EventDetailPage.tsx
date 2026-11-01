import { useCallback, useEffect, useState, useRef } from 'react';
import { Link, useParams, useNavigate } from 'react-router-dom';
import { fetchEvent, fetchSeats, createBooking, ApiError } from '../api/client';
import SeatMap from '../components/SeatMap';
import type { Event, Seat } from '../types';
import '../styles/EventDetailPage.css';

/**
 * Generates a unique idempotency key for a booking attempt.
 * Format: booking-{eventId}-{seatId}-{timestamp}-{random}
 */
function generateIdempotencyKey(eventId: number, seatId: number): string {
  return `booking-${eventId}-${seatId}-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

export default function EventDetailPage() {
  const { id } = useParams<{ id: string }>();
  const eventId = Number(id);
  const navigate = useNavigate();

  const [event, setEvent] = useState<Event | null>(null);
  const [seats, setSeats] = useState<Seat[]>([]);
  const [selectedSeatId, setSelectedSeatId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [notFound, setNotFound] = useState(false);

  // Booking submission state
  const [bookingInProgress, setBookingInProgress] = useState(false);
  const [bookingError, setBookingError] = useState<string | null>(null);
  // Stable idempotency key for the current booking attempt — regenerated on new attempt
  const idempotencyKeyRef = useRef<string | null>(null);

  const loadData = useCallback(() => {
    if (isNaN(eventId) || eventId <= 0) {
      setNotFound(true);
      setLoading(false);
      return;
    }

    setLoading(true);
    setError(null);
    setNotFound(false);

    Promise.all([fetchEvent(eventId), fetchSeats(eventId)])
      .then(([eventData, seatsData]) => {
        setEvent(eventData);
        setSeats(seatsData);
        setLoading(false);
      })
      .catch((err) => {
        if (err instanceof ApiError && err.status === 404) {
          setNotFound(true);
        } else {
          setError(err instanceof Error ? err.message : 'Failed to load event');
        }
        setLoading(false);
      });
  }, [eventId]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  /** Refresh only seats (without full-page reload) */
  const refreshSeats = useCallback(() => {
    fetchSeats(eventId)
      .then((seatsData) => {
        setSeats(seatsData);
        setSelectedSeatId(null);
      })
      .catch(() => {
        // Silently fail — stale UI is acceptable here; user can reload manually
      });
  }, [eventId]);

  /**
   * Single-seat toggle: selecting a seat deselects the previous one.
   * The backend only supports one seat per booking request.
   */
  const handleToggleSeat = useCallback((seatId: number) => {
    setSelectedSeatId((prev) => (prev === seatId ? null : seatId));
    setBookingError(null);
    // Reset idempotency key when user changes seat selection (new attempt)
    idempotencyKeyRef.current = null;
  }, []);

  /**
   * Submit booking for the selected seat.
   * Uses the same idempotency key if retrying the same attempt;
   * generates a new one for a genuinely new attempt.
   */
  const handleBookSeat = useCallback(async () => {
    if (selectedSeatId === null || bookingInProgress) return;

    setBookingError(null);
    setBookingInProgress(true);

    // Generate a new idempotency key only if this is a fresh attempt
    if (!idempotencyKeyRef.current) {
      idempotencyKeyRef.current = generateIdempotencyKey(eventId, selectedSeatId);
    }

    try {
      const booking = await createBooking({
        seatId: selectedSeatId,
        eventId,
        idempotencyKey: idempotencyKeyRef.current,
      });

      // Success — navigate to booking status page with the real booking ID
      navigate(`/bookings/${booking.id}`);
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.status === 409) {
          // Conflict: seat booked/locked by someone else — refresh seats
          setBookingError(err.message || 'This seat is no longer available.');
          refreshSeats();
          idempotencyKeyRef.current = null; // new attempt needed
        } else if (err.status === 404) {
          setBookingError('Seat or event not found. The data may have changed.');
          refreshSeats();
          idempotencyKeyRef.current = null;
        } else if (err.status === 400) {
          setBookingError(err.message || 'Invalid booking request.');
          idempotencyKeyRef.current = null;
        } else {
          // Other server error — keep idempotency key for retry
          setBookingError(err.message || 'Something went wrong. Please try again.');
        }
      } else {
        // Network error — keep idempotency key for retry
        setBookingError('Network error. Check your connection and try again.');
      }
    } finally {
      setBookingInProgress(false);
    }
  }, [selectedSeatId, eventId, bookingInProgress, navigate, refreshSeats]);

  // Derive selected seat for the summary
  const selectedSeat = seats.find((s) => s.id === selectedSeatId) ?? null;
  // Build a Set for the SeatMap component (it expects Set<number>)
  const selectedSeatIds = new Set(selectedSeatId !== null ? [selectedSeatId] : []);

  /* ── Loading ─────────────────────────────────────────── */

  if (loading) {
    return (
      <div className="event-detail">
        <div className="container">
          <div className="event-detail__loading" id="event-loading">
            <div className="event-detail__spinner" />
            <p className="event-detail__loading-text">Loading event…</p>
          </div>
        </div>
      </div>
    );
  }

  /* ── Not found ───────────────────────────────────────── */

  if (notFound) {
    return (
      <div className="event-detail">
        <div className="container">
          <div className="event-detail__not-found" id="event-not-found">
            <h2>Event not found</h2>
            <p className="event-detail__not-found-text">
              The event you're looking for doesn't exist or has been removed.
            </p>
            <Link to="/" className="btn btn--secondary">← Back to events</Link>
          </div>
        </div>
      </div>
    );
  }

  /* ── Error ───────────────────────────────────────────── */

  if (error) {
    return (
      <div className="event-detail">
        <div className="container">
          <div className="event-detail__error" id="event-error">
            <p className="event-detail__error-text">{error}</p>
            <button className="btn btn--secondary" onClick={loadData}>
              Retry
            </button>
          </div>
        </div>
      </div>
    );
  }

  /* ── Success ─────────────────────────────────────────── */

  return (
    <div className="event-detail">
      <div className="container">
        {/* Back link */}
        <Link to="/" className="event-detail__back">← All events</Link>

        {/* Event info card */}
        {event && (
          <div className="event-detail__header" id="event-info">
            <h1 className="event-detail__title">{event.title}</h1>
            <div className="event-detail__meta">
              <div className="event-detail__meta-item">
                <span className="event-detail__meta-icon">📅</span>
                <span>{event.date}</span>
              </div>
              <div className="event-detail__meta-item">
                <span className="event-detail__meta-icon">📍</span>
                <span>{event.location}</span>
              </div>
              <div className="event-detail__meta-item">
                <span className="event-detail__meta-icon">🌐</span>
                <span>{event.language}</span>
              </div>
            </div>
          </div>
        )}

        {/* Seat map section */}
        <h2 className="seat-section__title">Select a Seat</h2>

        {seats.length === 0 ? (
          <p style={{ color: 'var(--color-text-secondary)' }}>
            No seats are configured for this event yet.
          </p>
        ) : (
          <>
            <SeatMap
              seats={seats}
              selectedSeatIds={selectedSeatIds}
              onToggleSeat={handleToggleSeat}
            />

            {/* Booking error message */}
            {bookingError && (
              <div className="booking-error" id="booking-error">
                <p className="booking-error__text">{bookingError}</p>
              </div>
            )}

            {/* Selection summary + Book button */}
            {selectedSeat && (
              <div className="seat-selection-summary" id="seat-selection-summary">
                <div>
                  <p className="seat-selection-summary__text">
                    Seat{' '}
                    <span className="seat-selection-summary__count">
                      {selectedSeat.seatNumber}
                    </span>
                    {' '}selected
                  </p>
                </div>
                <button
                  type="button"
                  className="btn btn--primary"
                  id="book-seat-btn"
                  disabled={bookingInProgress}
                  onClick={handleBookSeat}
                >
                  {bookingInProgress ? 'Booking…' : 'Book This Seat'}
                </button>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}
