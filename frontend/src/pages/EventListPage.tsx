import { useEffect, useState } from 'react';
import { fetchEvents } from '../api/client';
import EventCard from '../components/EventCard';
import type { Event } from '../types';
import '../styles/EventListPage.css';

export default function EventListPage() {
  const [events, setEvents] = useState<Event[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    fetchEvents()
      .then((data) => {
        if (!cancelled) {
          setEvents(data);
          setLoading(false);
        }
      })
      .catch((err) => {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : 'Failed to load events');
          setLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div className="event-list-page">
      <div className="container">
        <h1 className="event-list-page__title">Upcoming Events</h1>
        <p className="event-list-page__subtitle">
          Browse events and book your seats
        </p>

        {loading && (
          <div className="event-list-page__loading" id="events-loading">
            <div className="event-list-page__spinner" />
            <p className="event-list-page__loading-text">Loading events…</p>
          </div>
        )}

        {error && !loading && (
          <div className="event-list-page__error" id="events-error">
            <p className="event-list-page__error-text">{error}</p>
            <button
              className="btn btn--secondary"
              onClick={() => window.location.reload()}
            >
              Retry
            </button>
          </div>
        )}

        {!loading && !error && events.length === 0 && (
          <div className="event-list-page__empty" id="events-empty">
            <p className="event-list-page__empty-text">
              No events available right now. Check back soon!
            </p>
          </div>
        )}

        {!loading && !error && events.length > 0 && (
          <div className="event-grid" id="events-grid">
            {events.map((event) => (
              <EventCard key={event.id} event={event} />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
