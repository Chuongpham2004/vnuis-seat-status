import { SEAT_STATUS, SEAT_STATUS_ORDER } from '../lib/seatStatus'

export default function StatusLegend({ counts, showMine = true }) {
  return (
    <ul className="flex flex-wrap items-center gap-x-5 gap-y-2 text-sm" aria-label="Chú thích trạng thái ghế">
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
      <li className="secondary">⚡ ổ điện</li>
      <li className="flex items-center gap-1.5 secondary">
        <span className="inline-block w-5 h-3 rounded-full border border-[var(--ink-secondary)]" /> bàn nhóm
      </li>
      <li className="secondary">📡 cảm biến ESP32 thật</li>
      {showMine && <li className="secondary">★ ghế của bạn</li>}
    </ul>
  )
}
