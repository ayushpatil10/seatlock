import { Link } from 'react-router-dom';
import type { Event } from '../types';

interface EventCardProps {
  event: Event;
}

export default function EventCard({ event }: EventCardProps) {
  return (
    <Link to={`/events/${event.id}`} className="event-card" id={`event-card-${event.id}`}>
      <h3 className="event-card__title">{event.title}</h3>
      <div className="event-card__details">
        <div className="event-card__detail">
          <span className="event-card__detail-icon">📅</span>
          <span>{event.date}</span>
        </div>
        <div className="event-card__detail">
          <span className="event-card__detail-icon">📍</span>
          <span>{event.location}</span>
        </div>
        <div className="event-card__detail">
          <span className="event-card__detail-icon">🌐</span>
          <span>{event.language}</span>
        </div>
      </div>
      <div className="event-card__action">
        <span>Select seats</span>
        <span className="event-card__action-arrow">→</span>
      </div>
    </Link>
  );
}
