import { seatStatusMeta } from '../lib/seatStatus'

export default function SeatTile({ seat, selected, dimmed, mine, onSelect }) {
  const meta = seatStatusMeta(seat.status)
  const offline = !seat.deviceOnline
  const title = [
    `Ghế ${seat.seatCode}: ${meta.label}`,
    offline ? 'cảm biến mất kết nối' : null,
    seat.hasPowerOutlet ? 'có ổ điện' : null,
    seat.seatType === 'GROUP_TABLE' ? 'bàn nhóm' : null,
    !seat.simulatedDevice ? 'cảm biến thật ESP32' : null,
    mine ? 'ghế của bạn' : null,
  ]
    .filter(Boolean)
    .join(', ')

  const classes = ['seat', meta.className]
  if (offline) classes.push('offline')
  if (selected) classes.push('selected')
  if (dimmed) classes.push('dimmed')

  return (
    <button
      type="button"
      className={classes.join(' ')}
      onClick={() => onSelect?.(seat)}
      aria-label={title}
      aria-pressed={Boolean(selected)}
      title={title}
    >
      <span className="seat-icons" aria-hidden>
        {seat.hasPowerOutlet && '⚡'}
        {seat.seatType === 'GROUP_TABLE' && '👥'}
        {!seat.simulatedDevice && '📡'}
      </span>
      <span className="seat-code">{seat.seatCode}</span>
      <span aria-hidden>
        {meta.icon} {mine ? 'Của bạn' : meta.short}
      </span>
    </button>
  )
}
