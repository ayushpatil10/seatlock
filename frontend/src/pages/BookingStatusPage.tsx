import { Link, useParams } from 'react-router-dom';
import '../styles/PlaceholderPage.css';

export default function BookingStatusPage() {
  const { id } = useParams<{ id: string }>();

  return (
    <div className="placeholder-page">
      <div className="container">
        <h1 className="placeholder-page__title">Booking #{id}</h1>
        <p className="placeholder-page__text">
          Booking status, confirmation, and cancellation will be implemented in the next phase.
        </p>
        <Link to="/" className="btn btn--secondary">
          ← Back to events
        </Link>
      </div>
    </div>
  );
}
