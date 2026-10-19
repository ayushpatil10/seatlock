import type { Seat } from '../types';
import SeatCell from './SeatCell';

interface SeatMapProps {
  seats: Seat[];
  selectedSeatIds: Set<number>;
  onToggleSeat: (seatId: number) => void;
}

/**
 * Groups seats into rows by their leading letter(s).
 * e.g. "A1" → row "A", "B12" → row "B".
 * Returns rows sorted alphabetically.
 */
function groupByRow(seats: Seat[]): Map<string, Seat[]> {
  const rowMap = new Map<string, Seat[]>();

  for (const seat of seats) {
    const match = seat.seatNumber.match(/^([A-Za-z]+)/);
    const rowKey = match ? match[1].toUpperCase() : '?';
    const existing = rowMap.get(rowKey) ?? [];
    existing.push(seat);
    rowMap.set(rowKey, existing);
  }

  // Sort seats within each row by their numeric suffix
  for (const [, rowSeats] of rowMap) {
    rowSeats.sort((a, b) => {
      const numA = parseInt(a.seatNumber.replace(/^[A-Za-z]+/, ''), 10) || 0;
      const numB = parseInt(b.seatNumber.replace(/^[A-Za-z]+/, ''), 10) || 0;
      return numA - numB;
    });
  }

  // Sort rows alphabetically
  return new Map(
    [...rowMap.entries()].sort(([a], [b]) => a.localeCompare(b)),
  );
}

/** Checks whether any seat uses LOCKED status — if so, we show it in the legend. */
function hasLockedSeats(seats: Seat[]): boolean {
  return seats.some((s) => s.status === 'LOCKED');
}

export default function SeatMap({ seats, selectedSeatIds, onToggleSeat }: SeatMapProps) {
  const rows = groupByRow(seats);
  const showLocked = hasLockedSeats(seats);

  return (
    <div className="seat-map" id="seat-map">
      {/* Stage indicator */}
      <div className="seat-map__stage">Stage / Screen</div>

      {/* Seat grid */}
      <div className="seat-map__grid">
        {[...rows.entries()].map(([rowKey, rowSeats]) => (
          <div className="seat-map__row" key={rowKey}>
            <span className="seat-map__row-label">{rowKey}</span>
            {rowSeats.map((seat) => (
              <SeatCell
                key={seat.id}
                seat={seat}
                isSelected={selectedSeatIds.has(seat.id)}
                onToggle={onToggleSeat}
              />
            ))}
          </div>
        ))}
      </div>

      {/* Legend */}
      <div className="seat-legend">
        <div className="seat-legend__item">
          <span className="seat-legend__swatch seat-legend__swatch--available" />
          <span>Available</span>
        </div>
        <div className="seat-legend__item">
          <span className="seat-legend__swatch seat-legend__swatch--selected" />
          <span>Selected</span>
        </div>
        <div className="seat-legend__item">
          <span className="seat-legend__swatch seat-legend__swatch--booked" />
          <span>Booked</span>
        </div>
        {showLocked && (
          <div className="seat-legend__item">
            <span className="seat-legend__swatch seat-legend__swatch--locked" />
            <span>Held</span>
          </div>
        )}
      </div>
    </div>
  );
}
