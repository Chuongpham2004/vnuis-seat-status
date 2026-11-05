import { SEAT_STATUS, SEAT_STATUS_ORDER } from '../lib/seatStatus'

export default function StatusLegend({ counts }) {
  return (
    <ul className="flex flex-wrap items-center gap-x-4 gap-y-2 text-sm" aria-label="Chú thích trạng thái ghế">
      {SEAT_STATUS_ORDER.map((s) => (
        <li key={s} className="flex items-center gap-1.5">
          <span className={'swatch ' + SEAT_STATUS[s].className} />
          <span className="secondary">
            {SEAT_STATUS[s].icon} {SEAT_STATUS[s].label}
            {counts && <span className="font-semibold text-[var(--ink-primary)]"> {counts[s] ?? 0}</span>}
          </span>
        </li>
      ))}
      <li className="flex items-center gap-1.5">
        <span className="swatch swatch-offline" />
        <span className="secondary">Cảm biến mất kết nối</span>
      </li>
      <li className="secondary">⚡ Ổ điện · 👥 Bàn nhóm · 📡 Cảm biến thật (ESP32)</li>
    </ul>
  )
}
