import { useState } from 'react'

// Sequential blue ramp (one hue, light -> dark) from the reference palette.
const RAMP = ['#cde2fb', '#9ec5f4', '#6da7ec', '#3987e5', '#256abf', '#184f95', '#0d366b']

function rampColor(v) {
  if (v == null) return null
  const i = Math.min(RAMP.length - 1, Math.max(0, Math.floor(v * RAMP.length)))
  return RAMP[i]
}

/**
 * Day x hour heatmap of occupancy rate (0..1).
 *  rows: [{ key, label, cells: Array(24) of number|null }]
 * Each cell is its own hover/focus target with a tooltip; empty hours stay blank.
 */
export default function Heatmap({ rows, hours = [...Array(24).keys()], ariaLabel }) {
  const [hover, setHover] = useState(null) // { r, h, left, top }
  const show = (r, h, el) => {
    const pr = el.closest('.relative').getBoundingClientRect()
    const cr = el.getBoundingClientRect()
    setHover({ r, h, left: cr.left - pr.left + 28, top: cr.top - pr.top - 8 })
  }

  return (
    <div className="relative">
      <div className="overflow-x-auto">
        <table className="border-separate" style={{ borderSpacing: 2 }} aria-label={ariaLabel}>
          <thead>
            <tr>
              <th />
              {hours.map((h) => (
                <th key={h} className="text-[10px] font-normal muted w-6 text-center">
                  {h % 3 === 0 ? h : ''}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((row, r) => (
              <tr key={row.key}>
                <th className="text-xs font-normal secondary text-right pr-2 whitespace-nowrap">{row.label}</th>
                {hours.map((h) => {
                  const v = row.cells[h]
                  const label = `${row.label}, ${h}h: ${v == null ? 'không có dữ liệu' : Math.round(v * 100) + '%'}`
                  return (
                    <td
                      key={h}
                      tabIndex={v == null ? -1 : 0}
                      aria-label={label}
                      className="w-6 h-6 rounded-[3px] outline-none focus-visible:ring-2 focus-visible:ring-[var(--ink-primary)]"
                      style={{
                        background: v == null ? 'transparent' : rampColor(v),
                        boxShadow: v == null ? 'inset 0 0 0 1px var(--grid)' : undefined,
                        opacity: hover && (hover.r !== r || hover.h !== h) ? 0.85 : 1,
                      }}
                      onPointerEnter={(e) => show(r, h, e.currentTarget)}
                      onFocus={(e) => show(r, h, e.currentTarget)}
                      onPointerLeave={() => setHover(null)}
                      onBlur={() => setHover(null)}
                    />
                  )
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {hover && rows[hover.r] && (
        <HeatTooltip left={hover.left} top={hover.top} label={rows[hover.r].label} hour={hover.h} value={rows[hover.r].cells[hover.h]} />
      )}
      <div className="flex items-center gap-2 mt-3 text-xs muted">
        <span>0%</span>
        <div className="flex" style={{ gap: 2 }}>
          {RAMP.map((c) => (
            <span key={c} className="inline-block w-6 h-2.5 rounded-[2px]" style={{ background: c }} />
          ))}
        </div>
        <span>100%</span>
      </div>
    </div>
  )
}

function HeatTooltip({ left, top, label, hour, value }) {
  return (
    <div className="chart-tooltip" style={{ left, top }}>
      <p className="value">{value == null ? '—' : `${Math.round(value * 100)}%`}</p>
      <p className="secondary">
        {label}, {hour}:00–{hour + 1}:00
      </p>
    </div>
  )
}
