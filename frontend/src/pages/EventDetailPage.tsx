import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { fetchEvent, fetchSeats, ApiError } from '../api/client';
import SeatMap from '../components/SeatMap';
import type { Event, Seat } from '../types';
import '../styles/EventDetailPage.css';

export default function EventDetailPage() {
  const { id } = useParams<{ id: string }>();
  const eventId = Number(id);

  const [event, setEvent] = useState<Event | null>(null);
  const [seats, setSeats] = useState<Seat[]>([]);
  const [selectedSeatIds, setSelectedSeatIds] = useState<Set<number>>(new Set());
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [notFound, setNotFound] = useState(false);

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

  const handleToggleSeat = useCallback((seatId: number) => {
    setSelectedSeatIds((prev) => {
      const next = new Set(prev);
      if (next.has(seatId)) {
        next.delete(seatId);
      } else {
        next.add(seatId);
      }
      return next;
    });
  }, []);

  // Derive selected seat labels for the summary
  const selectedSeats = seats.filter((s) => selectedSeatIds.has(s.id));

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
        <h2 className="seat-section__title">Select Your Seats</h2>

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

            {/* Selection summary */}
            {selectedSeats.length > 0 && (
              <div className="seat-selection-summary" id="seat-selection-summary">
                <div>
                  <p className="seat-selection-summary__text">
                    <span className="seat-selection-summary__count">
                      {selectedSeats.length}
                    </span>
                    {' '}seat{selectedSeats.length !== 1 ? 's' : ''} selected
                  </p>
                  <p className="seat-selection-summary__seats">
                    {selectedSeats.map((s) => s.seatNumber).join(', ')}
                  </p>
                </div>
                {/* Booking button will be added in a later phase */}
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}
