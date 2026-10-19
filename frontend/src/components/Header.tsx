import { Link } from 'react-router-dom';
import '../styles/Header.css';

export default function Header() {
  return (
    <header className="header">
      <div className="container header__inner">
        <Link to="/" className="header__logo">
          <span className="header__logo-icon">S</span>
          <span className="header__logo-text">SeatLock</span>
        </Link>
        <nav className="header__nav">
          <Link to="/" className="header__link">Events</Link>
        </nav>
      </div>
    </header>
  );
}
