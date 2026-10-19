import { Routes, Route } from 'react-router-dom';
import Header from './components/Header';
import EventListPage from './pages/EventListPage';
import EventDetailPage from './pages/EventDetailPage';
import BookingStatusPage from './pages/BookingStatusPage';
import './App.css';

export default function App() {
  return (
    <div className="app">
      <Header />
      <main className="app__main">
        <Routes>
          <Route path="/" element={<EventListPage />} />
          <Route path="/events/:id" element={<EventDetailPage />} />
          <Route path="/bookings/:id" element={<BookingStatusPage />} />
        </Routes>
      </main>
    </div>
  );
}
