import { SEAT_STATUS, SEAT_STATUS_ORDER } from '../lib/seatStatus'

const KEY = { AVAILABLE: 'available', RESERVED: 'reserved', OCCUPIED: 'occupied', AWAY: 'away' }
const FILL = { AVAILABLE: 'var(--good)', RESERVED: 'var(--warning)', OCCUPIED: 'var(--critical)', AWAY: 'var(--serious)' }

/** Segmented bar: one zone split by seat state, 2px surface gap between segments. */
export default function ZoneBar({ zone, height = 10 }) {
  const total = zone.totalSeats || 1
  return (
    <div
      className="flex w-full overflow-hidden rounded-full"
      style={{ height, gap: 2, background: 'var(--surface-1)' }}
      role="img"
      aria-label={SEAT_STATUS_ORDER.map((s) => `${SEAT_STATUS[s].label} ${zone[KEY[s]]}`).join(', ')}
    >
      {SEAT_STATUS_ORDER.map((s) =>
        zone[KEY[s]] > 0 ? (
          <div
            key={s}
            style={{ width: `${(zone[KEY[s]] / total) * 100}%`, background: FILL[s] }}
            title={`${SEAT_STATUS[s].label}: ${zone[KEY[s]]}`}
          />
        ) : null,
      )}
    </div>
  )
}
