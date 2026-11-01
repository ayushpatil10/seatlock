import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import {
  fetchBooking,
  confirmBooking,
  cancelBooking,
  ApiError,
} from '../api/client';
import type { Booking } from '../types';
import '../styles/BookingStatusPage.css';

const POLLING_INTERVAL_MS = 15000;

/** Map booking status to a badge CSS modifier */
function statusBadgeClass(status: string): string {
  switch (status) {
    case 'PENDING':
      return 'badge badge--warning';
    case 'CONFIRMED':
      return 'badge badge--success';
    case 'CANCELLED':
    case 'EXPIRED':
      return 'badge badge--danger';
    default:
      return 'badge badge--neutral';
  }
}

/** Format an ISO datetime string for display */
function formatDate(isoString: string): string {
  try {
    return new Date(isoString).toLocaleString();
  } catch {
    return isoString;
  }
}

export default function BookingStatusPage() {
  const { id } = useParams<{ id: string }>();
  const bookingId = Number(id);

  const [booking, setBooking] = useState<Booking | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [notFound, setNotFound] = useState(false);

  // Polling state
  const [isChecking, setIsChecking] = useState(false);
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);

  // Action state
  const [actionInProgress, setActionInProgress] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [actionSuccess, setActionSuccess] = useState<string | null>(null);

  const loadBooking = useCallback(() => {
    if (isNaN(bookingId) || bookingId <= 0) {
      setNotFound(true);
      setLoading(false);
      return;
    }

    setLoading(true);
    setError(null);
    setNotFound(false);

    fetchBooking(bookingId)
      .then((data) => {
        setBooking(data);
        setLastUpdated(new Date());
        setLoading(false);
      })
      .catch((err) => {
        if (err instanceof ApiError && err.status === 404) {
          setNotFound(true);
        } else {
          setError(err instanceof Error ? err.message : 'Failed to load booking');
        }
        setLoading(false);
      });
  }, [bookingId]);

  useEffect(() => {
    loadBooking();
  }, [loadBooking]);

  // Automatic polling while booking status is PENDING
  const isPending = booking?.status === 'PENDING';

  useEffect(() => {
    if (!isPending || isNaN(bookingId) || bookingId <= 0) {
      return;
    }

    let isMounted = true;
    let inFlight = false;

    const pollStatus = async () => {
      // Prevent overlapping requests or polling during user-triggered confirm/cancel
      if (inFlight || actionInProgress) {
        return;
      }

      inFlight = true;
      setIsChecking(true);

      try {
        const latestBooking = await fetchBooking(bookingId);
        if (isMounted) {
          setBooking(latestBooking);
          setLastUpdated(new Date());
        }
      } catch (err) {
        // Handle temporary network/API errors without crashing or clearing booking status
        console.warn('Booking status polling encountered an error:', err);
      } finally {
        if (isMounted) {
          setIsChecking(false);
          inFlight = false;
        }
      }
    };

    const intervalId = setInterval(pollStatus, POLLING_INTERVAL_MS);

    return () => {
      isMounted = false;
      clearInterval(intervalId);
    };
  }, [bookingId, isPending, actionInProgress]);

  const handleConfirm = useCallback(async () => {
    if (actionInProgress) return;
    setActionError(null);
    setActionSuccess(null);
    setActionInProgress(true);

    try {
      const updated = await confirmBooking(bookingId);
      setBooking(updated);
      setLastUpdated(new Date());
      setActionSuccess('Booking confirmed successfully!');
    } catch (err) {
      if (err instanceof ApiError) {
        setActionError(err.message);
      } else {
        setActionError('Failed to confirm booking. Please try again.');
      }
    } finally {
      setActionInProgress(false);
    }
  }, [bookingId, actionInProgress]);

  const handleCancel = useCallback(async () => {
    if (actionInProgress) return;
    setActionError(null);
    setActionSuccess(null);
    setActionInProgress(true);

    try {
      const updated = await cancelBooking(bookingId);
      setBooking(updated);
      setLastUpdated(new Date());
      setActionSuccess('Booking cancelled. The seat has been released.');
    } catch (err) {
      if (err instanceof ApiError) {
        setActionError(err.message);
      } else {
        setActionError('Failed to cancel booking. Please try again.');
      }
    } finally {
      setActionInProgress(false);
    }
  }, [bookingId, actionInProgress]);

  /* ── Loading ─────────────────────────────────────────── */

  if (loading) {
    return (
      <div className="booking-status">
        <div className="container">
          <div className="booking-status__loading" id="booking-loading">
            <div className="booking-status__spinner" />
            <p className="booking-status__loading-text">Loading booking…</p>
          </div>
        </div>
      </div>
    );
  }

  /* ── Not found ───────────────────────────────────────── */

  if (notFound) {
    return (
      <div className="booking-status">
        <div className="container">
          <div className="booking-status__not-found" id="booking-not-found">
            <h2>Booking not found</h2>
            <p className="booking-status__not-found-text">
              This booking doesn't exist or has been removed.
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
      <div className="booking-status">
        <div className="container">
          <div className="booking-status__error" id="booking-error">
            <p className="booking-status__error-text">{error}</p>
            <button className="btn btn--secondary" onClick={loadBooking}>
              Retry
            </button>
          </div>
        </div>
      </div>
    );
  }

  /* ── Success ─────────────────────────────────────────── */

  if (!booking) return null;

  return (
    <div className="booking-status">
      <div className="container">
        <Link to={`/events/${booking.eventId}`} className="booking-status__back">
          ← Back to event
        </Link>

        <div className="booking-card" id="booking-card">
          <div className="booking-card__header">
            <div>
              <h1 className="booking-card__title">Booking #{booking.id}</h1>
              {isPending && (
                <div className="booking-status__indicator" id="booking-polling-indicator">
                  <span
                    className={`polling-dot ${isChecking ? 'polling-dot--active' : ''}`}
                    aria-hidden="true"
                  />
                  <span className="booking-status__indicator-text">
                    {isChecking
                      ? 'Checking booking status...'
                      : lastUpdated
                      ? `Last updated: ${lastUpdated.toLocaleTimeString()}`
                      : 'Checking booking status...'}
                  </span>
                </div>
              )}
            </div>
            <span className={statusBadgeClass(booking.status)}>
              {booking.status}
            </span>
          </div>

          <div className="booking-card__details">
            <span className="booking-card__label">Seat ID</span>
            <span className="booking-card__value">{booking.seatId}</span>

            <span className="booking-card__label">Event ID</span>
            <span className="booking-card__value">{booking.eventId}</span>

            <span className="booking-card__label">Created</span>
            <span className="booking-card__value">
              {formatDate(booking.createdAt)}
            </span>

            <span className="booking-card__label">Idempotency Key</span>
            <span className="booking-card__value" style={{ wordBreak: 'break-all' }}>
              {booking.idempotencyKey}
            </span>
          </div>

          {/* Actions — only shown for PENDING bookings */}
          {isPending && (
            <div className="booking-card__actions">
              <button
                type="button"
                className="btn btn--primary"
                id="confirm-booking-btn"
                disabled={actionInProgress}
                onClick={handleConfirm}
              >
                {actionInProgress ? 'Processing…' : 'Confirm Booking'}
              </button>
              <button
                type="button"
                className="btn btn--danger"
                id="cancel-booking-btn"
                disabled={actionInProgress}
                onClick={handleCancel}
              >
                {actionInProgress ? 'Processing…' : 'Cancel Booking'}
              </button>
            </div>
          )}

          {/* Action feedback */}
          {actionError && (
            <div className="booking-card__error" id="booking-action-error">
              {actionError}
            </div>
          )}
          {actionSuccess && (
            <div className="booking-card__success" id="booking-action-success">
              {actionSuccess}
            </div>
          )}

          {/* Background expiration notice */}
          {booking.status === 'EXPIRED' && !actionSuccess && (
            <div className="booking-card__info-banner booking-card__info-banner--expired" id="booking-expired-banner">
              This booking has expired because it was not confirmed within the hold window. The seat has been released.
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
