import { seatStatusMeta } from '../lib/seatStatus'

/**
 * One desk on the floor plan. The room already says which zone it is, so the desk shows
 * only its number and a status glyph; the full description lives in the accessible name.
 * `since` (ms epoch): updates newer than this get one short pulse.
 */
export default function SeatTile({ seat, selected, dimmed, mine, onSelect, since }) {
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
  if (seat.seatType === 'GROUP_TABLE') classes.push('seat-group')
  if (offline) classes.push('offline')
  if (selected) classes.push('selected')
  if (dimmed) classes.push('dimmed')
  if (mine) classes.push('mine')

  const fresh = since && seat.updatedAt && new Date(seat.updatedAt).getTime() > since
  const number = seat.seatCode.split('-')[1] || seat.seatCode

  return (
    <button
      type="button"
      className={classes.join(' ')}
      onClick={() => onSelect?.(seat)}
      aria-label={title}
      aria-pressed={Boolean(selected)}
      title={title}
    >
      {fresh && <span key={seat.updatedAt} className="seat-ping" aria-hidden />}
      <span className="seat-icons" aria-hidden>
        {seat.hasPowerOutlet && '⚡'}
        {!seat.simulatedDevice && '📡'}
      </span>
      <span className="seat-num" aria-hidden>
        {number}
      </span>
      <span className="seat-glyph" aria-hidden>
        {mine ? '★' : meta.icon}
      </span>
    </button>
  )
}
