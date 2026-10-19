import type { Seat } from '../types';

interface SeatCellProps {
  seat: Seat;
  isSelected: boolean;
  onToggle: (seatId: number) => void;
}

/**
 * Returns a CSS modifier class based on the seat's status
 * and whether it's currently selected by the user.
 */
function getSeatModifier(seat: Seat, isSelected: boolean): string {
  if (isSelected) return 'seat-cell--selected';

  switch (seat.status) {
    case 'AVAILABLE':
      return 'seat-cell--available';
    case 'BOOKED':
      return 'seat-cell--booked';
    case 'LOCKED':
      return 'seat-cell--locked';
    default:
      return 'seat-cell--booked'; // treat unknown statuses as unavailable
  }
}

function isInteractive(seat: Seat, isSelected: boolean): boolean {
  return isSelected || seat.status === 'AVAILABLE';
}

/** Extracts the short label shown inside a seat cell (e.g. "3" from "A3"). */
function getSeatLabel(seatNumber: string): string {
  // Remove leading letters to show only the number inside the cell
  const num = seatNumber.replace(/^[A-Za-z]+/, '');
  return num || seatNumber;
}

export default function SeatCell({ seat, isSelected, onToggle }: SeatCellProps) {
  const modifier = getSeatModifier(seat, isSelected);
  const interactive = isInteractive(seat, isSelected);

  return (
    <button
      type="button"
      className={`seat-cell ${modifier}`}
      id={`seat-${seat.id}`}
      title={`Seat ${seat.seatNumber} — ${isSelected ? 'Selected' : seat.status}`}
      aria-label={`Seat ${seat.seatNumber}, ${isSelected ? 'selected' : seat.status.toLowerCase()}`}
      aria-pressed={isSelected}
      disabled={!interactive}
      onClick={() => {
        if (interactive) onToggle(seat.id);
      }}
    >
      {getSeatLabel(seat.seatNumber)}
    </button>
  );
}
